package com.haneul.medassist.repository

import com.haneul.medassist.domain.evidence.EvidenceVerificationStatus
import com.haneul.medassist.domain.evidence.VerifiedSourceReference
import com.haneul.medassist.domain.supplement.MappingType
import com.haneul.medassist.domain.supplement.SupplementIngredientCanonical
import com.haneul.medassist.domain.supplement.SupplementInteractionRule
import com.haneul.medassist.domain.supplement.SupplementProductIngredientMapping
import tools.jackson.databind.JsonNode
import tools.jackson.databind.ObjectMapper
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.time.Clock
import java.time.Instant

data class SupplementRuleCatalogValidationResult(
    val document: SupplementRuleCatalogDocument?,
    val report: CatalogValidationReport,
)

class SupplementRuleCatalogValidator(
    private val objectMapper: ObjectMapper,
    private val clock: Clock = Clock.systemUTC(),
) {
    fun validate(
        bytes: ByteArray,
        requireVerifiedManifest: Boolean = false,
    ): SupplementRuleCatalogValidationResult {
        val errors = mutableListOf<CatalogValidationIssue>()
        val warnings = mutableListOf<CatalogValidationIssue>()
        val duplicateIds = linkedSetOf<String>()
        val missingReferences = linkedSetOf<String>()
        val invalidVerificationStates = linkedSetOf<String>()
        val invalidDateRanges = linkedSetOf<String>()
        val duplicateActiveRules = linkedSetOf<String>()
        val root = parseRoot(bytes, errors)
        if (root != null) validateShape(root, errors, duplicateIds, invalidDateRanges)
        val document = if (root != null && errors.none { it.code.startsWith("SCHEMA_") }) {
            runCatching { objectMapper.treeToValue(root, SupplementRuleCatalogDocument::class.java) }
                .getOrElse {
                    errors += issue("SCHEMA_DESERIALIZATION_FAILED", "$", "catalog field types or values are invalid")
                    null
                }
        } else {
            null
        }

        val checksum = document?.let(::contentChecksum)
        if (document != null) {
            validateIds(document, errors, duplicateIds)
            validateReferences(document, errors, missingReferences, invalidVerificationStates)
            validateAliases(document, errors)
            validateActiveStates(document, errors, warnings, invalidVerificationStates)
            validateDuplicateRules(document.rules, errors, duplicateActiveRules)
            validateManifest(document, checksum.orEmpty(), requireVerifiedManifest, errors, invalidVerificationStates)
        }
        val report = CatalogValidationReport(
            valid = errors.isEmpty(),
            catalogVersion = document?.manifest?.catalogVersion,
            schemaVersion = document?.manifest?.schemaVersion,
            sourceCount = document?.sources?.size ?: 0,
            canonicalIngredientCount = document?.ingredients?.size ?: 0,
            productMappingCount = document?.mappings?.size ?: 0,
            interactionRuleCount = document?.rules?.size ?: 0,
            errors = errors.distinctBy { Triple(it.code, it.path, it.message) },
            warnings = warnings.distinctBy { Triple(it.code, it.path, it.message) },
            duplicateIds = duplicateIds.toList().sorted(),
            missingReferences = missingReferences.toList().sorted(),
            invalidVerificationStates = invalidVerificationStates.toList().sorted(),
            invalidDateRanges = invalidDateRanges.toList().sorted(),
            duplicateActiveRules = duplicateActiveRules.toList().sorted(),
            checksum = checksum,
            generatedAt = clock.instant(),
        )
        return SupplementRuleCatalogValidationResult(document, report)
    }

    fun contentChecksum(document: SupplementRuleCatalogDocument): String {
        val payload = SupplementRuleCatalogDocument(
            sources = document.sources,
            ingredients = document.ingredients,
            mappings = document.mappings,
            rules = document.rules,
        )
        return sha256(objectMapper.writeValueAsBytes(payload))
    }

    fun rawChecksum(bytes: ByteArray): String = sha256(bytes)

    private fun parseRoot(bytes: ByteArray, errors: MutableList<CatalogValidationIssue>): JsonNode? {
        if (!StandardCharsets.UTF_8.newDecoder().runCatchingDecode(bytes)) {
            errors += issue("SCHEMA_INVALID_UTF8", "$", "catalog must be valid UTF-8")
            return null
        }
        return runCatching { objectMapper.readTree(bytes) }
            .getOrElse {
                errors += issue("SCHEMA_MALFORMED_JSON", "$", "catalog must be valid JSON")
                null
            }
            ?.also {
                if (!it.isObject) errors += issue("SCHEMA_ROOT_NOT_OBJECT", "$", "catalog root must be an object")
            }
    }

    private fun validateShape(
        root: JsonNode,
        errors: MutableList<CatalogValidationIssue>,
        duplicateIds: MutableSet<String>,
        invalidDateRanges: MutableSet<String>,
    ) {
        if (!root.isObject) return
        validateUnknownFields(root, "$", setOf("manifest", "sources", "ingredients", "mappings", "rules"), errors)
        val requiredBySection = mapOf(
            "sources" to listOf("id", "authority", "title", "originalText", "retrievedAt", "verificationStatus"),
            "ingredients" to listOf(
                "id", "canonicalName", "displayName", "active", "sourceReferenceId", "verificationStatus", "createdAt", "updatedAt",
            ),
            "mappings" to listOf(
                "id", "statementNo", "productName", "supplementIngredientCanonicalId", "ingredientDisplayName",
                "mappingType", "sourceField", "sourceReferenceId", "verificationStatus", "createdAt", "updatedAt",
            ),
            "rules" to listOf(
                "id", "drugIngredientCode", "drugIngredientName", "supplementIngredientCanonicalId", "severity",
                "interactionType", "userMessage", "recommendation", "sourceReferenceIds", "verificationStatus", "createdAt", "updatedAt",
            ),
        )
        val optionalBySection = mapOf(
            "sources" to setOf("sourceUrl", "documentIdentifier", "publishedAt", "reviewedBy", "reviewedAt", "notes", "sourceVersion"),
            "ingredients" to setOf("aliases", "providerCode", "category"),
            "mappings" to setOf("validFrom", "validTo"),
            "rules" to setOf("mechanismSummary", "validFrom", "validTo", "ruleVersion"),
        )
        requiredBySection.forEach { (section, requiredFields) ->
            val records = root.get(section)
            if (records == null || !records.isArray) {
                errors += issue("SCHEMA_REQUIRED_ARRAY", "/$section", "$section must be an array")
                return@forEach
            }
            val sectionIds = mutableSetOf<String>()
            records.forEachIndexed { index, record ->
                val path = "/$section/$index"
                if (!record.isObject) {
                    errors += issue("SCHEMA_RECORD_NOT_OBJECT", path, "record must be an object")
                    return@forEachIndexed
                }
                validateUnknownFields(record, path, requiredFields.toSet() + optionalBySection.getValue(section), errors)
                requiredFields.forEach { field ->
                    val value = record.get(field)
                    if (value == null || value.isNull || (value.isString && value.asString().isBlank())) {
                        errors += issue("SCHEMA_REQUIRED_FIELD", "$path/$field", "$field is required")
                    }
                }
                record.get("id")?.takeIf(JsonNode::isString)?.asString()?.let { id ->
                    if (!sectionIds.add(id)) duplicateIds += id
                }
                validateDateRange(record, path, errors, invalidDateRanges)
                if (section == "sources") validateSourceShape(record, path, errors)
                if (section == "ingredients") validateAliasShape(record, path, errors)
                if (section == "rules") validateSourceIdsShape(record, path, errors)
            }
        }
        root.get("manifest")?.takeUnless(JsonNode::isNull)?.let { manifest ->
            if (!manifest.isObject) {
                errors += issue("SCHEMA_MANIFEST_NOT_OBJECT", "/manifest", "manifest must be an object")
            } else {
                val manifestFields = listOf(
                    "catalogVersion", "schemaVersion", "generatedAt", "generatedBy", "recordCounts", "status", "contentChecksum",
                )
                validateUnknownFields(
                    manifest,
                    "/manifest",
                    manifestFields.toSet() + setOf("reviewer", "reviewedAt", "sourceFileChecksums"),
                    errors,
                )
                manifestFields.forEach { field ->
                    val value = manifest.get(field)
                    if (value == null || value.isNull || (value.isString && value.asString().isBlank())) {
                        errors += issue("SCHEMA_REQUIRED_FIELD", "/manifest/$field", "$field is required")
                    }
                }
            }
        }
    }

    private fun validateUnknownFields(
        node: JsonNode,
        path: String,
        allowed: Set<String>,
        errors: MutableList<CatalogValidationIssue>,
    ) {
        node.propertyNames().filterNot(allowed::contains).forEach { field ->
            errors += issue("SCHEMA_UNKNOWN_FIELD", "$path/$field", "unknown catalog field")
        }
    }

    private fun validateSourceShape(record: JsonNode, path: String, errors: MutableList<CatalogValidationIssue>) {
        val hasUrl = record.get("sourceUrl")?.takeIf(JsonNode::isString)?.asString()?.isNotBlank() == true
        val hasIdentifier = record.get("documentIdentifier")?.takeIf(JsonNode::isString)?.asString()?.isNotBlank() == true
        if (!hasUrl && !hasIdentifier) {
            errors += issue("SCHEMA_SOURCE_IDENTIFIER_REQUIRED", path, "sourceUrl or documentIdentifier is required")
        }
        if (record.get("verificationStatus")?.asString() == EvidenceVerificationStatus.VERIFIED.name) {
            if (record.get("reviewedBy")?.asString()?.isNotBlank() != true) {
                errors += issue("SCHEMA_VERIFIED_REVIEWER_REQUIRED", "$path/reviewedBy", "VERIFIED source requires reviewedBy")
            }
            if (record.get("reviewedAt")?.asString()?.isNotBlank() != true) {
                errors += issue("SCHEMA_VERIFIED_REVIEWED_AT_REQUIRED", "$path/reviewedAt", "VERIFIED source requires reviewedAt")
            }
        }
    }

    private fun validateAliasShape(record: JsonNode, path: String, errors: MutableList<CatalogValidationIssue>) {
        val aliases = record.get("aliases") ?: return
        if (!aliases.isArray) {
            errors += issue("SCHEMA_ALIASES_NOT_ARRAY", "$path/aliases", "aliases must be an array")
            return
        }
        val seen = mutableSetOf<String>()
        aliases.forEachIndexed { index, alias ->
            val normalized = alias.takeIf(JsonNode::isString)?.asString()?.trim()?.lowercase().orEmpty()
            if (normalized.isBlank()) {
                errors += issue("SCHEMA_INVALID_ALIAS", "$path/aliases/$index", "alias must be a non-blank string")
            } else if (!seen.add(normalized)) {
                errors += issue("DUPLICATE_ALIAS", "$path/aliases/$index", "alias is duplicated within the canonical ingredient")
            }
        }
    }

    private fun validateSourceIdsShape(record: JsonNode, path: String, errors: MutableList<CatalogValidationIssue>) {
        val sourceIds = record.get("sourceReferenceIds") ?: return
        if (!sourceIds.isArray || sourceIds.isEmpty) {
            errors += issue("SCHEMA_RULE_SOURCE_REQUIRED", "$path/sourceReferenceIds", "rule requires at least one source reference")
        }
    }

    private fun validateDateRange(
        record: JsonNode,
        path: String,
        errors: MutableList<CatalogValidationIssue>,
        invalidDateRanges: MutableSet<String>,
    ) {
        val from = record.get("validFrom")?.takeIf(JsonNode::isString)?.asString()?.let { runCatching { Instant.parse(it) }.getOrNull() }
        val to = record.get("validTo")?.takeIf(JsonNode::isString)?.asString()?.let { runCatching { Instant.parse(it) }.getOrNull() }
        val createdAt = record.get("createdAt")?.takeIf(JsonNode::isString)?.asString()
            ?.let { runCatching { Instant.parse(it) }.getOrNull() }
        val updatedAt = record.get("updatedAt")?.takeIf(JsonNode::isString)?.asString()
            ?.let { runCatching { Instant.parse(it) }.getOrNull() }
        val id = record.get("id")?.asString().orEmpty()
        if (from != null && to != null && to.isBefore(from)) {
            invalidDateRanges += id
            errors += issue("INVALID_DATE_RANGE", path, "validTo must not precede validFrom")
        }
        if (createdAt != null && updatedAt != null && updatedAt.isBefore(createdAt)) {
            invalidDateRanges += id
            errors += issue("INVALID_DATE_RANGE", path, "updatedAt must not precede createdAt")
        }
    }

    private fun validateIds(
        document: SupplementRuleCatalogDocument,
        errors: MutableList<CatalogValidationIssue>,
        duplicateIds: MutableSet<String>,
    ) {
        val allIds = buildList {
            addAll(document.sources.map { it.id to "sources" })
            addAll(document.ingredients.map { it.id to "ingredients" })
            addAll(document.mappings.map { it.id to "mappings" })
            addAll(document.rules.map { it.id to "rules" })
        }
        allIds.groupBy { it.first }.filterValues { it.size > 1 }.forEach { (id, owners) ->
            duplicateIds += id
            errors += issue("DUPLICATE_GLOBAL_ID", "/", "id $id is used by ${owners.joinToString { it.second }}")
        }
    }

    private fun validateReferences(
        document: SupplementRuleCatalogDocument,
        errors: MutableList<CatalogValidationIssue>,
        missingReferences: MutableSet<String>,
        invalidVerificationStates: MutableSet<String>,
    ) {
        val sources = document.sources.associateBy(VerifiedSourceReference::id)
        val ingredients = document.ingredients.associateBy(SupplementIngredientCanonical::id)
        document.ingredients.forEach { ingredient ->
            val source = sources[ingredient.sourceReferenceId]
            if (source == null) missing(errors, missingReferences, "ingredient:${ingredient.id}:source:${ingredient.sourceReferenceId}")
            if (ingredient.verificationStatus == EvidenceVerificationStatus.VERIFIED && source?.isProductionEligible() != true) {
                invalid(errors, invalidVerificationStates, "ingredient:${ingredient.id}", "VERIFIED canonical ingredient requires a VERIFIED source")
            }
        }
        document.mappings.forEach { mapping ->
            val source = sources[mapping.sourceReferenceId]
            val ingredient = ingredients[mapping.supplementIngredientCanonicalId]
            if (source == null) missing(errors, missingReferences, "mapping:${mapping.id}:source:${mapping.sourceReferenceId}")
            if (ingredient == null) missing(errors, missingReferences, "mapping:${mapping.id}:ingredient:${mapping.supplementIngredientCanonicalId}")
            if (mapping.verificationStatus == EvidenceVerificationStatus.VERIFIED &&
                (source?.isProductionEligible() != true || ingredient?.isProductionEligible() != true)
            ) {
                invalid(errors, invalidVerificationStates, "mapping:${mapping.id}", "VERIFIED mapping requires VERIFIED source and canonical ingredient")
            }
            if (mapping.verificationStatus == EvidenceVerificationStatus.VERIFIED &&
                mapping.mappingType == MappingType.UNVERIFIED_CANDIDATE
            ) {
                invalid(errors, invalidVerificationStates, "mapping:${mapping.id}", "VERIFIED mapping cannot be UNVERIFIED_CANDIDATE")
            }
        }
        document.rules.forEach { rule ->
            val ingredient = ingredients[rule.supplementIngredientCanonicalId]
            if (ingredient == null) missing(errors, missingReferences, "rule:${rule.id}:ingredient:${rule.supplementIngredientCanonicalId}")
            val ruleSources = rule.sourceReferenceIds.map { sourceId ->
                sources[sourceId] ?: alsoMissing(errors, missingReferences, "rule:${rule.id}:source:$sourceId")
            }
            if (rule.verificationStatus == EvidenceVerificationStatus.VERIFIED &&
                (ingredient?.isProductionEligible() != true || ruleSources.any { it?.isProductionEligible() != true })
            ) {
                invalid(errors, invalidVerificationStates, "rule:${rule.id}", "VERIFIED rule requires VERIFIED canonical ingredient and sources")
            }
        }
    }

    private fun validateAliases(document: SupplementRuleCatalogDocument, errors: MutableList<CatalogValidationIssue>) {
        val owners = mutableMapOf<String, String>()
        document.ingredients.forEach { ingredient ->
            ingredient.aliases.forEach { alias ->
                val normalized = alias.trim().lowercase()
                val previous = owners.putIfAbsent(normalized, ingredient.id)
                if (previous != null && previous != ingredient.id) {
                    errors += issue("DUPLICATE_ALIAS", "/ingredients/${ingredient.id}/aliases", "alias is already owned by canonical ingredient $previous")
                }
            }
        }
    }

    private fun validateActiveStates(
        document: SupplementRuleCatalogDocument,
        errors: MutableList<CatalogValidationIssue>,
        warnings: MutableList<CatalogValidationIssue>,
        invalidVerificationStates: MutableSet<String>,
    ) {
        document.ingredients.forEach { ingredient ->
            if (ingredient.active && ingredient.verificationStatus == EvidenceVerificationStatus.RETIRED) {
                invalid(errors, invalidVerificationStates, "ingredient:${ingredient.id}", "RETIRED canonical ingredient cannot remain active")
            } else if (!ingredient.active && ingredient.verificationStatus == EvidenceVerificationStatus.VERIFIED) {
                warnings += issue("INACTIVE_VERIFIED_INGREDIENT", "/ingredients/${ingredient.id}", "inactive VERIFIED ingredient is excluded from production")
            }
        }
    }

    private fun validateDuplicateRules(
        rules: List<SupplementInteractionRule>,
        errors: MutableList<CatalogValidationIssue>,
        duplicateActiveRules: MutableSet<String>,
    ) {
        rules.filter { it.verificationStatus == EvidenceVerificationStatus.VERIFIED }
            .groupBy { it.drugIngredientCode to it.supplementIngredientCanonicalId }
            .values.forEach { samePair ->
                samePair.forEachIndexed { index, left ->
                    samePair.drop(index + 1).forEach { right ->
                        if (periodsOverlap(left, right)) {
                            val key = "${left.drugIngredientCode}:${left.supplementIngredientCanonicalId}:${left.id}:${right.id}"
                            duplicateActiveRules += key
                            errors += issue("DUPLICATE_ACTIVE_RULE", "/rules", "active VERIFIED rules overlap for the same ingredient pair")
                        }
                    }
                }
            }
    }

    private fun validateManifest(
        document: SupplementRuleCatalogDocument,
        checksum: String,
        requireVerifiedManifest: Boolean,
        errors: MutableList<CatalogValidationIssue>,
        invalidVerificationStates: MutableSet<String>,
    ) {
        val manifest = document.manifest
        if (manifest == null) {
            if (requireVerifiedManifest) errors += issue("MANIFEST_REQUIRED", "/manifest", "production catalog requires a VERIFIED manifest")
            return
        }
        if (manifest.schemaVersion != SCHEMA_VERSION) {
            errors += issue("SCHEMA_VERSION_MISMATCH", "/manifest/schemaVersion", "unsupported catalog schema version")
        }
        if (manifest.recordCounts != document.recordCounts()) {
            errors += issue("MANIFEST_COUNT_MISMATCH", "/manifest/recordCounts", "manifest record counts do not match catalog content")
        }
        if (!manifest.contentChecksum.equals(checksum, ignoreCase = true)) {
            errors += issue("CHECKSUM_MISMATCH", "/manifest/contentChecksum", "manifest checksum does not match catalog content")
        }
        if (manifest.sourceFileChecksums.values.any { !SHA256_PATTERN.matches(it) }) {
            errors += issue("INVALID_SOURCE_CHECKSUM", "/manifest/sourceFileChecksums", "source file checksums must be SHA-256 hex values")
        }
        if (manifest.status == SupplementRuleCatalogStatus.VERIFIED &&
            (manifest.reviewer.isNullOrBlank() || manifest.reviewedAt == null)
        ) {
            invalidVerificationStates += "manifest"
            errors += issue("VERIFIED_MANIFEST_REVIEW_REQUIRED", "/manifest", "VERIFIED manifest requires reviewer and reviewedAt")
        }
        if (requireVerifiedManifest && manifest.status != SupplementRuleCatalogStatus.VERIFIED) {
            invalidVerificationStates += "manifest:${manifest.status}"
            errors += issue("MANIFEST_NOT_VERIFIED", "/manifest/status", "production catalog manifest must be VERIFIED")
        }
    }

    private fun periodsOverlap(left: SupplementInteractionRule, right: SupplementInteractionRule): Boolean {
        val leftStartsBeforeRightEnds = right.validTo == null || left.validFrom == null || !left.validFrom.isAfter(right.validTo)
        val rightStartsBeforeLeftEnds = left.validTo == null || right.validFrom == null || !right.validFrom.isAfter(left.validTo)
        return leftStartsBeforeRightEnds && rightStartsBeforeLeftEnds
    }

    private fun missing(
        errors: MutableList<CatalogValidationIssue>,
        missingReferences: MutableSet<String>,
        value: String,
    ) {
        missingReferences += value
        errors += issue("MISSING_REFERENCE", "/", "catalog contains a missing reference: $value")
    }

    private fun alsoMissing(
        errors: MutableList<CatalogValidationIssue>,
        missingReferences: MutableSet<String>,
        value: String,
    ): VerifiedSourceReference? {
        missing(errors, missingReferences, value)
        return null
    }

    private fun invalid(
        errors: MutableList<CatalogValidationIssue>,
        invalidVerificationStates: MutableSet<String>,
        value: String,
        message: String,
    ) {
        invalidVerificationStates += value
        errors += issue("INVALID_VERIFICATION_STATE", "/", message)
    }

    private fun issue(code: String, path: String, message: String) = CatalogValidationIssue(code, path, message)

    private fun SupplementRuleCatalogDocument.recordCounts() = SupplementRuleCatalogRecordCounts(
        sources = sources.size,
        canonicalIngredients = ingredients.size,
        productMappings = mappings.size,
        interactionRules = rules.size,
    )

    private fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256")
        .digest(bytes)
        .joinToString("") { "%02x".format(it) }

    private fun java.nio.charset.CharsetDecoder.runCatchingDecode(bytes: ByteArray): Boolean =
        runCatching { decode(java.nio.ByteBuffer.wrap(bytes)) }.isSuccess

    companion object {
        const val SCHEMA_VERSION = "1.0"
        private val SHA256_PATTERN = Regex("^[a-fA-F0-9]{64}$")
    }
}
