package com.haneul.medassist.data

import kotlinx.serialization.Serializable

@Serializable
data class SupplementInteractionCheckRequest(
    val medicationProductCode: String,
    val supplementStatementNo: String,
)

@Serializable
data class SupplementInteractionCheckResponse(
    val processingStatus: String,
    val severity: String,
    val message: String,
    val explanation: SupplementInteractionExplanationDto,
    val medication: MedicationInteractionSummaryDto? = null,
    val medicationOverview: DrugOverviewDto? = null,
    val supplement: SupplementInteractionProductDto? = null,
    val drugIngredients: List<DrugIngredientDto> = emptyList(),
    val supplementIngredients: List<SupplementIngredientDto> = emptyList(),
    val evaluatedPairs: List<SupplementInteractionPairDto> = emptyList(),
    val matchedRules: List<MatchedRuleDto> = emptyList(),
    val evidence: List<InteractionEvidenceDto> = emptyList(),
    val coverage: SupplementInteractionCoverageDto,
    val failedSteps: Set<String> = emptySet(),
    val catalogMetadata: SupplementInteractionCatalogMetadataDto,
    val disclaimer: String,
    val analyzedAt: String,
) {
    val severityValue: SupplementInteractionSeverity
        get() = SupplementInteractionSeverity.fromWire(severity)
}

enum class SupplementInteractionSeverity {
    AVOID_COMBINATION,
    CAUTION,
    NO_VERIFIED_RULE_FOUND,
    UNKNOWN;

    companion object {
        fun fromWire(value: String): SupplementInteractionSeverity =
            entries.firstOrNull { it.name == value } ?: UNKNOWN
    }
}

@Serializable
data class MedicationInteractionSummaryDto(
    val productCode: String,
    val productName: String,
    val manufacturer: String? = null,
    val source: InteractionSourceDto,
)

@Serializable
data class SupplementInteractionProductDto(
    val statementNo: String,
    val productName: String,
    val manufacturer: String? = null,
    val registerDate: String? = null,
    val intakeMethod: String? = null,
    val intakeHint: String? = null,
    val mainFunction: String? = null,
    val baseStandard: String? = null,
    val productSource: InteractionSourceDto,
    val retrievedAt: String,
)

@Serializable
data class InteractionSourceDto(
    val name: String,
    val recordId: String,
    val retrievedAt: String,
    val providerReference: String,
)

@Serializable
data class DrugIngredientDto(
    val providerCode: String? = null,
    val displayName: String,
    val normalizedName: String,
    val amount: Double? = null,
    val unit: String? = null,
    val source: InteractionSourceDto,
)

@Serializable
data class SupplementIngredientDto(
    val canonicalId: String,
    val canonicalName: String,
    val displayName: String,
    val providerCode: String? = null,
    val category: String? = null,
    val sourceReferenceId: String,
    val verificationStatus: String,
)

@Serializable
data class SupplementInteractionPairDto(
    val drugIngredientCode: String,
    val drugIngredientName: String,
    val supplementIngredientCanonicalId: String,
    val supplementIngredientName: String,
    val evaluated: Boolean,
    val matchedRuleIds: List<String> = emptyList(),
    val errorCode: String? = null,
)

@Serializable
data class MatchedRuleDto(
    val id: String,
    val drugIngredientCode: String,
    val drugIngredientName: String,
    val supplementIngredientCanonicalId: String,
    val severity: String,
    val interactionType: String,
    val mechanismSummary: String? = null,
    val userMessage: String,
    val recommendation: String,
    val sourceReferenceIds: Set<String> = emptySet(),
    val verificationStatus: String,
    val ruleVersion: String? = null,
    val validFrom: String? = null,
    val validTo: String? = null,
)

@Serializable
data class InteractionEvidenceDto(
    val ruleId: String,
    val evidenceType: String,
    val sourceAuthority: String,
    val sourceReferenceId: String,
    val title: String,
    val sourceTitle: String,
    val originalText: String,
    val drugIngredientCode: String,
    val drugIngredientName: String,
    val supplementIngredientCanonicalId: String,
    val supplementIngredientName: String,
    val severity: String,
    val verificationStatus: String,
    val ruleVersion: String? = null,
    val sourceVersion: String? = null,
    val validFrom: String? = null,
    val validTo: String? = null,
    val retrievedAt: String,
)

@Serializable
data class SupplementInteractionCoverageDto(
    val medicationResolved: Boolean,
    val medicationIngredientsExpected: Int,
    val medicationIngredientsResolved: Int,
    val medicationIngredientsComplete: Boolean,
    val supplementResolved: Boolean,
    val supplementIngredientMappingAvailable: Boolean,
    val supplementIngredientsExpected: Int,
    val supplementIngredientsVerified: Int,
    val totalPairs: Int,
    val evaluatedPairs: Int,
    val matchedPairs: Int,
    val failedPairs: Int,
    val ruleRepositoryAvailable: Boolean,
    val complete: Boolean,
    val percentage: Int,
)

@Serializable
data class SupplementInteractionCatalogMetadataDto(
    val available: Boolean,
    val verified: Boolean,
    val catalogVersion: String? = null,
    val schemaVersion: String? = null,
    val catalogChecksum: String? = null,
    val loadedAt: String,
    val sourceCount: Int,
    val canonicalIngredientCount: Int,
    val productMappingCount: Int,
    val interactionRuleCount: Int,
    val validationErrorCodes: List<String> = emptyList(),
)

@Serializable
data class SupplementInteractionExplanationDto(
    val status: String,
    val summary: String,
    val rationale: String,
    val consultationAdvice: String,
    val keyPoints: List<String> = emptyList(),
    val provider: String? = null,
    val model: String? = null,
) {
    val statusValue: SupplementInteractionExplanationStatus
        get() = SupplementInteractionExplanationStatus.fromWire(status)
}

enum class SupplementInteractionExplanationStatus {
    GENERATED,
    FALLBACK,
    UNAVAILABLE;

    companion object {
        fun fromWire(value: String): SupplementInteractionExplanationStatus =
            entries.firstOrNull { it.name == value } ?: UNAVAILABLE
    }
}

@Serializable
data class OfficialMedicalTextDto(
    val raw: String,
    val display: String,
)

@Serializable
data class DrugOverviewCoverageDto(
    val productResolved: Boolean,
    val overviewResolved: Boolean,
    val complete: Boolean,
)

@Serializable
data class DrugOverviewDto(
    val productCode: String,
    val productName: String,
    val manufacturer: String? = null,
    val efficacy: OfficialMedicalTextDto? = null,
    val usageMethod: OfficialMedicalTextDto? = null,
    val warning: OfficialMedicalTextDto? = null,
    val precautions: OfficialMedicalTextDto? = null,
    val interactions: OfficialMedicalTextDto? = null,
    val sideEffects: OfficialMedicalTextDto? = null,
    val storageMethod: OfficialMedicalTextDto? = null,
    val imageUrl: String? = null,
    val openDate: String? = null,
    val updateDate: String? = null,
    val source: InteractionSourceDto,
    val coverage: DrugOverviewCoverageDto,
)

@Serializable
data class ProblemDetailsDto(
    val type: String? = null,
    val title: String? = null,
    val status: Int? = null,
    val detail: String? = null,
    val instance: String? = null,
    val code: String? = null,
    val timestamp: String? = null,
)

@Serializable
data class SupplementProductSearchRequest(
    val query: String,
    val manufacturer: String? = null,
)

@Serializable
data class SupplementProductSearchResponse(
    val query: String,
    val normalizedQuery: String,
    val status: String,
    val sourceType: String,
    val complete: Boolean,
    val candidates: List<SupplementSearchCandidateDto> = emptyList(),
)

@Serializable
data class SupplementSearchCandidateDto(
    val sttemntNo: String,
    val productName: String,
    val manufacturer: String? = null,
    val matchScore: Int,
    val matchType: String,
    val source: SupplementSearchSourceDto,
)

@Serializable
data class SupplementSearchSourceDto(
    val name: String,
    val recordId: String,
    val retrievedAt: String,
    val providerReference: String,
)
