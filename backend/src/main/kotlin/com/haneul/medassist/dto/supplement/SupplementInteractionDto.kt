package com.haneul.medassist.dto.supplement

import com.haneul.medassist.domain.evidence.SupplementRuleCatalogAuditMetadata
import com.haneul.medassist.domain.medication.DrugOverview
import com.haneul.medassist.domain.medication.SourceMetadata
import com.haneul.medassist.domain.supplement.SupplementInteractionAnalysisResult
import com.haneul.medassist.domain.supplement.SupplementInteractionCoverage
import com.haneul.medassist.domain.supplement.SupplementInteractionEvidence
import com.haneul.medassist.domain.supplement.SupplementInteractionFailureCode
import com.haneul.medassist.domain.supplement.SupplementInteractionPairEvaluation
import com.haneul.medassist.domain.supplement.SupplementInteractionExplanation
import com.haneul.medassist.domain.supplement.SupplementInteractionPresentationResult
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Pattern
import jakarta.validation.constraints.Size
import java.math.BigDecimal
import java.time.Instant

data class SupplementInteractionCheckRequest(
    @field:NotBlank(message = "의약품 품목기준코드를 입력해 주세요.")
    @field:Size(max = 50, message = "의약품 품목기준코드는 50자 이하여야 합니다.")
    @field:Pattern(regexp = "^[A-Za-z0-9_-]+$", message = "의약품 품목기준코드 형식이 올바르지 않습니다.")
    val medicationProductCode: String,
    @field:NotBlank(message = "건강기능식품 품목제조관리번호를 입력해 주세요.")
    @field:Size(max = 50, message = "건강기능식품 품목제조관리번호는 50자 이하여야 합니다.")
    @field:Pattern(regexp = "^[A-Za-z0-9_-]+$", message = "건강기능식품 품목제조관리번호 형식이 올바르지 않습니다.")
    val supplementStatementNo: String,
)

data class SupplementInteractionCheckResponse(
    val processingStatus: String,
    val severity: String,
    val message: String,
    val explanation: SupplementInteractionExplanation,
    val medication: InteractionMedicationResponse?,
    val medicationOverview: DrugOverview?,
    val supplement: InteractionSupplementResponse?,
    val drugIngredients: List<InteractionDrugIngredientResponse>,
    val supplementIngredients: List<InteractionSupplementIngredientResponse>,
    val evaluatedPairs: List<SupplementInteractionPairEvaluation>,
    val matchedRules: List<InteractionRuleResponse>,
    val evidence: List<SupplementInteractionEvidence>,
    val coverage: SupplementInteractionCoverage,
    val failedSteps: Set<SupplementInteractionFailureCode>,
    val catalogMetadata: SupplementRuleCatalogAuditMetadata,
    val disclaimer: String,
    val analyzedAt: Instant,
) {
    companion object {
        fun from(presentation: SupplementInteractionPresentationResult): SupplementInteractionCheckResponse =
            from(presentation.analysis, presentation.explanation)

        private fun from(
            result: SupplementInteractionAnalysisResult,
            explanation: SupplementInteractionExplanation,
        ): SupplementInteractionCheckResponse =
            SupplementInteractionCheckResponse(
                processingStatus = result.processingStatus.name,
                severity = result.severity.name,
                message = result.message,
                explanation = explanation,
                medication = result.medication?.let {
                    InteractionMedicationResponse(it.productCode, it.productName, it.manufacturer, it.source)
                },
                medicationOverview = result.medicationOverview,
                supplement = result.supplement?.let {
                    InteractionSupplementResponse(
                        statementNo = it.statementNo,
                        productName = it.productName,
                        manufacturer = it.manufacturer,
                        registerDate = it.registerDate,
                        intakeMethod = it.usage,
                        intakeHint = it.intakeHint,
                        mainFunction = it.mainFunction,
                        baseStandard = it.baseStandard,
                        productSource = it.source,
                        retrievedAt = it.retrievedAt,
                    )
                },
                drugIngredients = result.drugIngredients.map {
                    InteractionDrugIngredientResponse(
                        providerCode = it.providerCode,
                        displayName = it.displayName,
                        normalizedName = it.normalizedName,
                        amount = it.amount,
                        unit = it.unit,
                        source = it.source,
                    )
                },
                supplementIngredients = result.supplementIngredients.map {
                    InteractionSupplementIngredientResponse(
                        canonicalId = it.id,
                        canonicalName = it.canonicalName,
                        displayName = it.displayName,
                        providerCode = it.providerCode,
                        category = it.category,
                        sourceReferenceId = it.sourceReferenceId,
                        verificationStatus = it.verificationStatus.name,
                    )
                },
                evaluatedPairs = result.evaluatedPairs,
                matchedRules = result.matchedRules.map {
                    InteractionRuleResponse(
                        id = it.id,
                        drugIngredientCode = it.drugIngredientCode,
                        drugIngredientName = it.drugIngredientName,
                        supplementIngredientCanonicalId = it.supplementIngredientCanonicalId,
                        severity = it.severity.name,
                        interactionType = it.interactionType.name,
                        mechanismSummary = it.mechanismSummary,
                        userMessage = it.userMessage,
                        recommendation = it.recommendation,
                        sourceReferenceIds = it.sourceReferenceIds,
                        verificationStatus = it.verificationStatus.name,
                        ruleVersion = it.ruleVersion,
                        validFrom = it.validFrom,
                        validTo = it.validTo,
                    )
                },
                evidence = result.evidence,
                coverage = result.coverage,
                failedSteps = result.failedSteps,
                catalogMetadata = result.catalogMetadata,
                disclaimer = result.disclaimer,
                analyzedAt = result.analyzedAt,
            )
    }
}

data class InteractionMedicationResponse(
    val productCode: String,
    val productName: String,
    val manufacturer: String?,
    val source: SourceMetadata,
)

data class InteractionSupplementResponse(
    val statementNo: String,
    val productName: String,
    val manufacturer: String?,
    val registerDate: String?,
    val intakeMethod: String?,
    val intakeHint: String?,
    val mainFunction: String?,
    val baseStandard: String?,
    val productSource: SourceMetadata,
    val retrievedAt: Instant,
)

data class InteractionDrugIngredientResponse(
    val providerCode: String?,
    val displayName: String,
    val normalizedName: String,
    val amount: BigDecimal?,
    val unit: String?,
    val source: SourceMetadata,
)

data class InteractionSupplementIngredientResponse(
    val canonicalId: String,
    val canonicalName: String,
    val displayName: String,
    val providerCode: String?,
    val category: String?,
    val sourceReferenceId: String,
    val verificationStatus: String,
)

data class InteractionRuleResponse(
    val id: String,
    val drugIngredientCode: String,
    val drugIngredientName: String,
    val supplementIngredientCanonicalId: String,
    val severity: String,
    val interactionType: String,
    val mechanismSummary: String?,
    val userMessage: String,
    val recommendation: String,
    val sourceReferenceIds: Set<String>,
    val verificationStatus: String,
    val ruleVersion: String?,
    val validFrom: Instant?,
    val validTo: Instant?,
)
