package com.haneul.medassist.repository

import com.haneul.medassist.config.SupplementInteractionRuleProperties
import com.haneul.medassist.domain.evidence.EvidenceVerificationStatus
import com.haneul.medassist.domain.evidence.SupplementRuleCatalogAuditMetadata
import com.haneul.medassist.domain.evidence.VerifiedSourceReference
import com.haneul.medassist.domain.supplement.MappingType
import com.haneul.medassist.domain.supplement.SupplementIngredientCanonical
import com.haneul.medassist.domain.supplement.SupplementInteractionRule
import com.haneul.medassist.domain.supplement.SupplementProductIngredientMapping
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.core.io.ResourceLoader
import org.slf4j.LoggerFactory
import tools.jackson.databind.ObjectMapper
import java.time.Instant

data class SupplementRuleCatalogDocument(
    val sources: List<VerifiedSourceReference> = emptyList(),
    val ingredients: List<SupplementIngredientCanonical> = emptyList(),
    val mappings: List<SupplementProductIngredientMapping> = emptyList(),
    val rules: List<SupplementInteractionRule> = emptyList(),
    val manifest: SupplementRuleCatalogManifest? = null,
)

class JsonSupplementRuleCatalog(
    document: SupplementRuleCatalogDocument,
    private val auditMetadata: SupplementRuleCatalogAuditMetadata = document.defaultAuditMetadata(),
) : VerifiedSourceReferenceRepository,
    SupplementIngredientCanonicalRepository,
    SupplementProductIngredientMappingRepository,
    SupplementInteractionRuleRepository,
    SupplementRuleCatalogMetadataProvider {
    private val sources = document.sources.associateUniqueBy("source", VerifiedSourceReference::id)
    private val ingredients = document.ingredients.associateUniqueBy("canonical ingredient", SupplementIngredientCanonical::id)
    private val mappings = document.mappings.associateUniqueBy("product ingredient mapping", SupplementProductIngredientMapping::id)
    private val rules = document.rules.associateUniqueBy("interaction rule", SupplementInteractionRule::id)

    init {
        validateReferences()
        validateDuplicateActiveRules()
    }

    override fun findById(id: String): VerifiedSourceReference? = sources[id]

    override fun findVerifiedByIds(ids: Set<String>): List<VerifiedSourceReference> =
        ids.mapNotNull(sources::get).filter(VerifiedSourceReference::isProductionEligible)

    override fun findVerifiedById(id: String): SupplementIngredientCanonical? =
        ingredients[id]?.takeIf(SupplementIngredientCanonical::isProductionEligible)

    override fun findVerifiedByStatementNo(
        statementNo: String,
        at: Instant,
    ): List<SupplementProductIngredientMapping> = mappings.values
        .filter { it.statementNo == statementNo && it.isProductionEligible(at) }
        .sortedBy(SupplementProductIngredientMapping::id)

    override fun findVerified(
        drugIngredientCode: String,
        supplementIngredientCanonicalId: String,
        at: Instant,
    ): List<SupplementInteractionRule> = rules.values
        .filter {
            it.drugIngredientCode == drugIngredientCode &&
                it.supplementIngredientCanonicalId == supplementIngredientCanonicalId &&
                it.isProductionEligible(at)
        }
        .sortedBy(SupplementInteractionRule::id)

    override fun isAvailable(): Boolean = auditMetadata.available

    override fun metadata(): SupplementRuleCatalogAuditMetadata = auditMetadata

    private fun validateReferences() {
        ingredients.values.forEach { ingredient ->
            val source = requireSource(ingredient.sourceReferenceId, "canonical ingredient ${ingredient.id}")
            if (ingredient.verificationStatus == EvidenceVerificationStatus.VERIFIED) {
                require(source.isProductionEligible()) {
                    "VERIFIED canonical ingredient ${ingredient.id} requires a VERIFIED source"
                }
            }
        }
        mappings.values.forEach { mapping ->
            val source = requireSource(mapping.sourceReferenceId, "mapping ${mapping.id}")
            val ingredient = requireNotNull(ingredients[mapping.supplementIngredientCanonicalId]) {
                "mapping ${mapping.id} references a missing canonical ingredient"
            }
            if (mapping.verificationStatus == EvidenceVerificationStatus.VERIFIED) {
                require(mapping.mappingType != MappingType.UNVERIFIED_CANDIDATE) {
                    "VERIFIED mapping ${mapping.id} cannot be UNVERIFIED_CANDIDATE"
                }
                require(source.isProductionEligible()) { "VERIFIED mapping ${mapping.id} requires a VERIFIED source" }
                require(ingredient.isProductionEligible()) {
                    "VERIFIED mapping ${mapping.id} requires a VERIFIED canonical ingredient"
                }
            }
        }
        rules.values.forEach { rule ->
            val ingredient = requireNotNull(ingredients[rule.supplementIngredientCanonicalId]) {
                "rule ${rule.id} references a missing canonical ingredient"
            }
            val referencedSources = rule.sourceReferenceIds.map { requireSource(it, "rule ${rule.id}") }
            if (rule.verificationStatus == EvidenceVerificationStatus.VERIFIED) {
                require(ingredient.isProductionEligible()) {
                    "VERIFIED rule ${rule.id} requires a VERIFIED canonical ingredient"
                }
                require(referencedSources.all(VerifiedSourceReference::isProductionEligible)) {
                    "VERIFIED rule ${rule.id} requires only VERIFIED sources"
                }
            }
        }
    }

    private fun requireSource(id: String, owner: String): VerifiedSourceReference =
        requireNotNull(sources[id]) { "$owner references a missing source" }

    private fun validateDuplicateActiveRules() {
        rules.values
            .filter { it.verificationStatus == EvidenceVerificationStatus.VERIFIED }
            .groupBy { it.drugIngredientCode to it.supplementIngredientCanonicalId }
            .values
            .forEach { samePair ->
                samePair.forEachIndexed { index, left ->
                    samePair.drop(index + 1).forEach { right ->
                        require(!periodsOverlap(left.validFrom, left.validTo, right.validFrom, right.validTo)) {
                            "duplicate active VERIFIED rules for ${left.drugIngredientCode} and " +
                                left.supplementIngredientCanonicalId
                        }
                    }
                }
            }
    }

    private fun periodsOverlap(
        leftFrom: Instant?,
        leftTo: Instant?,
        rightFrom: Instant?,
        rightTo: Instant?,
    ): Boolean {
        val leftStartsBeforeRightEnds = rightTo == null || leftFrom == null || !leftFrom.isAfter(rightTo)
        val rightStartsBeforeLeftEnds = leftTo == null || rightFrom == null || !rightFrom.isAfter(leftTo)
        return leftStartsBeforeRightEnds && rightStartsBeforeLeftEnds
    }

    private fun <T> List<T>.associateUniqueBy(label: String, key: (T) -> String): Map<String, T> {
        val result = linkedMapOf<String, T>()
        forEach { value ->
            val id = key(value)
            require(result.putIfAbsent(id, value) == null) { "duplicate $label id: $id" }
        }
        return result
    }

    companion object {
        fun unavailable(metadata: SupplementRuleCatalogAuditMetadata): JsonSupplementRuleCatalog =
            JsonSupplementRuleCatalog(SupplementRuleCatalogDocument(), metadata)
    }
}

@Configuration
class SupplementInteractionRuleCatalogConfiguration {
    @Bean
    fun supplementRuleCatalog(
        properties: SupplementInteractionRuleProperties,
        resourceLoader: ResourceLoader,
        objectMapper: ObjectMapper,
    ): JsonSupplementRuleCatalog {
        val resource = resourceLoader.getResource(properties.resource)
        if (!resource.exists()) {
            logger.error("Supplement rule catalog unavailable code=CATALOG_NOT_FOUND resource={}", properties.resource)
            return JsonSupplementRuleCatalog.unavailable(unavailableMetadata("CATALOG_NOT_FOUND"))
        }
        val result = runCatching {
            val bytes = resource.inputStream.use { it.readAllBytes() }
            SupplementRuleCatalogValidator(objectMapper).validate(bytes, properties.requireVerifiedManifest)
        }.getOrElse {
            logger.error("Supplement rule catalog unavailable code=CATALOG_READ_FAILED resource={}", properties.resource)
            return JsonSupplementRuleCatalog.unavailable(unavailableMetadata("CATALOG_READ_FAILED"))
        }
        val errorCodes = result.report.errors.map(CatalogValidationIssue::code).distinct()
        val document = result.document
        if (!result.report.valid || document == null) {
            logger.error(
                "Supplement rule catalog unavailable code=CATALOG_VALIDATION_FAILED resource={} validationCodes={}",
                properties.resource,
                errorCodes.joinToString(","),
            )
            return JsonSupplementRuleCatalog.unavailable(
                unavailableMetadata(
                    errorCode = "CATALOG_VALIDATION_FAILED",
                    report = result.report,
                ),
            )
        }
        val manifest = document.manifest
        return JsonSupplementRuleCatalog(
            document = document,
            auditMetadata = SupplementRuleCatalogAuditMetadata(
                available = true,
                verified = manifest?.status == SupplementRuleCatalogStatus.VERIFIED,
                catalogVersion = manifest?.catalogVersion,
                schemaVersion = manifest?.schemaVersion,
                catalogChecksum = result.report.checksum,
                loadedAt = Instant.now(),
                sourceCount = document.sources.size,
                canonicalIngredientCount = document.ingredients.size,
                productMappingCount = document.mappings.size,
                interactionRuleCount = document.rules.size,
                validationErrorCodes = emptyList(),
            ),
        )
    }

    private fun unavailableMetadata(
        errorCode: String,
        report: CatalogValidationReport? = null,
    ) = SupplementRuleCatalogAuditMetadata(
        available = false,
        verified = false,
        catalogVersion = report?.catalogVersion,
        schemaVersion = report?.schemaVersion,
        catalogChecksum = report?.checksum,
        loadedAt = Instant.now(),
        sourceCount = report?.sourceCount ?: 0,
        canonicalIngredientCount = report?.canonicalIngredientCount ?: 0,
        productMappingCount = report?.productMappingCount ?: 0,
        interactionRuleCount = report?.interactionRuleCount ?: 0,
        validationErrorCodes = listOf(errorCode) + report?.errors.orEmpty().map(CatalogValidationIssue::code),
    )

    companion object {
        private val logger = LoggerFactory.getLogger(SupplementInteractionRuleCatalogConfiguration::class.java)
    }
}

private fun SupplementRuleCatalogDocument.defaultAuditMetadata() = SupplementRuleCatalogAuditMetadata(
    available = true,
    verified = manifest?.status == SupplementRuleCatalogStatus.VERIFIED || manifest == null,
    catalogVersion = manifest?.catalogVersion,
    schemaVersion = manifest?.schemaVersion,
    catalogChecksum = null,
    loadedAt = Instant.now(),
    sourceCount = sources.size,
    canonicalIngredientCount = ingredients.size,
    productMappingCount = mappings.size,
    interactionRuleCount = rules.size,
    validationErrorCodes = emptyList(),
)
