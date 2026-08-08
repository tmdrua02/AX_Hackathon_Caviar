package com.haneul.medassist.repository

import com.haneul.medassist.domain.evidence.SupplementRuleCatalogAuditMetadata
import java.time.Instant

enum class SupplementRuleCatalogStatus {
    DRAFT,
    VALIDATION_FAILED,
    READY_FOR_REVIEW,
    VERIFIED,
    REJECTED,
    RETIRED,
}

data class SupplementRuleCatalogRecordCounts(
    val sources: Int,
    val canonicalIngredients: Int,
    val productMappings: Int,
    val interactionRules: Int,
) {
    init {
        require(sources >= 0) { "source count must not be negative" }
        require(canonicalIngredients >= 0) { "canonical ingredient count must not be negative" }
        require(productMappings >= 0) { "product mapping count must not be negative" }
        require(interactionRules >= 0) { "interaction rule count must not be negative" }
    }
}

data class SupplementRuleCatalogManifest(
    val catalogVersion: String,
    val schemaVersion: String,
    val generatedAt: Instant,
    val generatedBy: String,
    val reviewer: String? = null,
    val reviewedAt: Instant? = null,
    val sourceFileChecksums: Map<String, String> = emptyMap(),
    val recordCounts: SupplementRuleCatalogRecordCounts,
    val status: SupplementRuleCatalogStatus,
    val contentChecksum: String,
)

data class CatalogValidationIssue(
    val code: String,
    val path: String,
    val message: String,
)

data class CatalogValidationReport(
    val valid: Boolean,
    val catalogVersion: String?,
    val schemaVersion: String?,
    val sourceCount: Int,
    val canonicalIngredientCount: Int,
    val productMappingCount: Int,
    val interactionRuleCount: Int,
    val errors: List<CatalogValidationIssue>,
    val warnings: List<CatalogValidationIssue>,
    val duplicateIds: List<String>,
    val missingReferences: List<String>,
    val invalidVerificationStates: List<String>,
    val invalidDateRanges: List<String>,
    val duplicateActiveRules: List<String>,
    val checksum: String?,
    val generatedAt: Instant,
)

fun interface SupplementRuleCatalogMetadataProvider {
    fun metadata(): SupplementRuleCatalogAuditMetadata
}
