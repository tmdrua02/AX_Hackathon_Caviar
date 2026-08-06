package com.haneul.medassist.domain.supplement

import com.haneul.medassist.domain.evidence.EvidenceAuthority
import com.haneul.medassist.domain.evidence.EvidenceVerificationStatus
import com.haneul.medassist.domain.evidence.VerifiedSourceReference
import com.haneul.medassist.domain.medication.DrugOverview
import com.haneul.medassist.domain.medication.Ingredient
import com.haneul.medassist.domain.medication.VerifiedDrugProduct
import java.time.Instant

enum class SupplementInteractionSeverity {
    AVOID_COMBINATION,
    CAUTION,
    NO_VERIFIED_RULE_FOUND,
    UNKNOWN,
}

enum class SupplementInteractionProcessingStatus {
    COMPLETED,
    PARTIAL,
    FAILED,
}

data class SupplementIngredientCanonical(
    val id: String,
    val canonicalName: String,
    val displayName: String,
    val aliases: Set<String> = emptySet(),
    val providerCode: String? = null,
    val category: String? = null,
    val active: Boolean = true,
    val sourceReferenceId: String,
    val verificationStatus: EvidenceVerificationStatus,
    val createdAt: Instant,
    val updatedAt: Instant,
) {
    init {
        require(id.isNotBlank()) { "canonical ingredient id must not be blank" }
        require(canonicalName.isNotBlank()) { "canonicalName must not be blank" }
        require(displayName.isNotBlank()) { "displayName must not be blank" }
        require(aliases.none(String::isBlank)) { "aliases must not contain blank values" }
        require(sourceReferenceId.isNotBlank()) { "canonical ingredient source is required" }
        require(!updatedAt.isBefore(createdAt)) { "updatedAt must not precede createdAt" }
    }

    fun isProductionEligible(): Boolean = active && verificationStatus == EvidenceVerificationStatus.VERIFIED
}

enum class MappingType {
    OFFICIAL_STRUCTURED,
    OFFICIAL_TEXT_VERIFIED,
    PRODUCT_LABEL_VERIFIED,
    MANUAL_VERIFIED,
    UNVERIFIED_CANDIDATE,
}

data class SupplementProductIngredientMapping(
    val id: String,
    val statementNo: String,
    val productName: String,
    val supplementIngredientCanonicalId: String,
    val ingredientDisplayName: String,
    val mappingType: MappingType,
    val sourceField: String,
    val sourceReferenceId: String,
    val verificationStatus: EvidenceVerificationStatus,
    val validFrom: Instant? = null,
    val validTo: Instant? = null,
    val createdAt: Instant,
    val updatedAt: Instant,
) {
    init {
        require(id.isNotBlank()) { "mapping id must not be blank" }
        require(statementNo.isNotBlank()) { "statementNo must not be blank" }
        require(productName.isNotBlank()) { "productName must not be blank" }
        require(supplementIngredientCanonicalId.isNotBlank()) { "canonical ingredient id is required" }
        require(ingredientDisplayName.isNotBlank()) { "ingredientDisplayName must not be blank" }
        require(sourceField.isNotBlank()) { "sourceField must not be blank" }
        require(sourceReferenceId.isNotBlank()) { "mapping source is required" }
        require(validFrom == null || validTo == null || !validTo.isBefore(validFrom)) {
            "mapping validTo must not precede validFrom"
        }
        require(!updatedAt.isBefore(createdAt)) { "updatedAt must not precede createdAt" }
    }

    fun isProductionEligible(at: Instant): Boolean =
        verificationStatus == EvidenceVerificationStatus.VERIFIED &&
            mappingType != MappingType.UNVERIFIED_CANDIDATE &&
            (validFrom == null || !at.isBefore(validFrom)) &&
            (validTo == null || !at.isAfter(validTo))
}

enum class InteractionType {
    BLEEDING_RISK,
    ABSORPTION_CHANGE,
    METABOLISM_CHANGE,
    EFFECT_INCREASE,
    EFFECT_DECREASE,
    DUPLICATE_EFFECT,
    BLOOD_PRESSURE_EFFECT,
    BLOOD_GLUCOSE_EFFECT,
    CENTRAL_NERVOUS_SYSTEM_EFFECT,
    ELECTROLYTE_EFFECT,
    LIVER_EFFECT,
    KIDNEY_EFFECT,
    OTHER,
}

data class SupplementInteractionRule(
    val id: String,
    val drugIngredientCode: String,
    val drugIngredientName: String,
    val supplementIngredientCanonicalId: String,
    val severity: SupplementInteractionSeverity,
    val interactionType: InteractionType,
    val mechanismSummary: String? = null,
    val userMessage: String,
    val recommendation: String,
    val sourceReferenceIds: Set<String>,
    val verificationStatus: EvidenceVerificationStatus,
    val validFrom: Instant? = null,
    val validTo: Instant? = null,
    val createdAt: Instant,
    val updatedAt: Instant,
) {
    init {
        require(id.isNotBlank()) { "rule id must not be blank" }
        require(drugIngredientCode.isNotBlank()) { "drugIngredientCode is required" }
        require(drugIngredientName.isNotBlank()) { "drugIngredientName is required" }
        require(supplementIngredientCanonicalId.isNotBlank()) { "canonical ingredient id is required" }
        require(severity == SupplementInteractionSeverity.AVOID_COMBINATION || severity == SupplementInteractionSeverity.CAUTION) {
            "stored rules may only use AVOID_COMBINATION or CAUTION"
        }
        require(userMessage.isNotBlank()) { "userMessage is required" }
        require(recommendation.isNotBlank()) { "recommendation is required" }
        require(sourceReferenceIds.isNotEmpty() && sourceReferenceIds.none(String::isBlank)) {
            "at least one sourceReferenceId is required"
        }
        require(validFrom == null || validTo == null || !validTo.isBefore(validFrom)) {
            "rule validTo must not precede validFrom"
        }
        require(!updatedAt.isBefore(createdAt)) { "updatedAt must not precede createdAt" }
    }

    fun isProductionEligible(at: Instant): Boolean =
        verificationStatus == EvidenceVerificationStatus.VERIFIED &&
            (validFrom == null || !at.isBefore(validFrom)) &&
            (validTo == null || !at.isAfter(validTo))
}

data class SupplementInteractionPairEvaluation(
    val drugIngredientCode: String,
    val drugIngredientName: String,
    val supplementIngredientCanonicalId: String,
    val supplementIngredientName: String,
    val evaluated: Boolean,
    val matchedRuleIds: List<String>,
    val errorCode: String? = null,
)

data class SupplementInteractionCoverage(
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

data class SupplementInteractionEvidence(
    val evidenceType: String,
    val sourceAuthority: EvidenceAuthority,
    val sourceReferenceId: String,
    val title: String,
    val originalText: String,
    val drugIngredientCode: String,
    val drugIngredientName: String,
    val supplementIngredientCanonicalId: String,
    val supplementIngredientName: String,
    val severity: SupplementInteractionSeverity,
    val verificationStatus: EvidenceVerificationStatus,
    val validFrom: Instant?,
    val validTo: Instant?,
    val retrievedAt: Instant,
)

data class SupplementInteractionEvidenceBundle(
    val officialMedicationProduct: VerifiedDrugProduct?,
    val officialMedicationIngredients: List<Ingredient>,
    val medicationOverview: DrugOverview?,
    val officialSupplementProduct: SupplementProductSnapshot?,
    val verifiedSupplementIngredients: List<SupplementIngredientCanonical>,
    val matchedInteractionRules: List<SupplementInteractionRule>,
    val sourceReferences: List<VerifiedSourceReference>,
    val immutableDecision: SupplementInteractionSeverity,
    val coverage: SupplementInteractionCoverage,
    val failedSteps: Set<String>,
    val analyzedAt: Instant,
    val disclaimer: String,
)

data class SupplementInteractionAnalysisResult(
    val processingStatus: SupplementInteractionProcessingStatus,
    val severity: SupplementInteractionSeverity,
    val medication: VerifiedDrugProduct?,
    val medicationOverview: DrugOverview?,
    val supplement: SupplementProductSnapshot?,
    val drugIngredients: List<Ingredient>,
    val supplementIngredients: List<SupplementIngredientCanonical>,
    val evaluatedPairs: List<SupplementInteractionPairEvaluation>,
    val matchedRules: List<SupplementInteractionRule>,
    val evidence: List<SupplementInteractionEvidence>,
    val coverage: SupplementInteractionCoverage,
    val failedSteps: Set<String>,
    val message: String,
    val disclaimer: String,
    val analyzedAt: Instant,
    val evidenceBundle: SupplementInteractionEvidenceBundle,
)

data class SupplementInteractionExplanationRequest(
    val immutableDecision: SupplementInteractionSeverity,
    val medicationName: String?,
    val supplementName: String?,
    val officialDrugIngredients: List<ExplanationDrugIngredient>,
    val verifiedSupplementIngredients: List<ExplanationSupplementIngredient>,
    val matchedRules: List<ExplanationMatchedRule>,
    val evidenceTexts: List<ExplanationEvidenceText>,
    val coverage: SupplementInteractionCoverage,
    val failedSteps: Set<String>,
    val disclaimer: String,
)

data class ExplanationDrugIngredient(
    val providerCode: String?,
    val displayName: String,
)

data class ExplanationSupplementIngredient(
    val canonicalId: String,
    val displayName: String,
)

data class ExplanationMatchedRule(
    val ruleId: String,
    val severity: SupplementInteractionSeverity,
)

data class ExplanationEvidenceText(
    val sourceReferenceId: String,
    val originalText: String,
)

fun SupplementInteractionAnalysisResult.toExplanationRequest(): SupplementInteractionExplanationRequest =
    SupplementInteractionExplanationRequest(
        immutableDecision = severity,
        medicationName = medication?.productName,
        supplementName = supplement?.productName,
        officialDrugIngredients = drugIngredients.map {
            ExplanationDrugIngredient(it.providerCode, it.displayName)
        },
        verifiedSupplementIngredients = supplementIngredients.map {
            ExplanationSupplementIngredient(it.id, it.displayName)
        },
        matchedRules = matchedRules.map { ExplanationMatchedRule(it.id, it.severity) },
        evidenceTexts = evidence.map { ExplanationEvidenceText(it.sourceReferenceId, it.originalText) },
        coverage = coverage,
        failedSteps = failedSteps,
        disclaimer = disclaimer,
    )
