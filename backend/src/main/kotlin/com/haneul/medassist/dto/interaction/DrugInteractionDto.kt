package com.haneul.medassist.dto.interaction

import com.haneul.medassist.domain.interaction.DrugInteractionAnalysisResult
import com.haneul.medassist.domain.interaction.InteractionCheckStatus
import com.haneul.medassist.domain.interaction.InteractionResult
import com.haneul.medassist.domain.interaction.IngredientPairResult
import com.haneul.medassist.domain.medication.VerifiedDrugProduct
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotEmpty
import jakarta.validation.constraints.Size
import java.time.Instant

data class DrugInteractionBatchRequest(
    @field:NotBlank(message = "새 약의 품목기준코드를 입력해 주세요.")
    val newMedicationProductCode: String,
    @field:NotEmpty(message = "비교할 기존 약의 품목기준코드를 입력해 주세요.")
    @field:Size(max = 20, message = "한 번에 비교할 수 있는 기존 약은 최대 20개입니다.")
    val existingMedicationProductCodes: List<@NotBlank(message = "품목기준코드는 비어 있을 수 없습니다.") String>,
)

data class DrugInteractionBatchResponse(
    val processingStatus: InteractionCheckStatus,
    val newMedicationProductCode: String,
    val results: List<DrugInteractionPairResponse>,
    val coverage: DrugInteractionBatchCoverage,
    val analyzedAt: Instant,
    val disclaimer: String = InteractionResult.CONSULTATION_NOTICE,
) {
    companion object {
        fun from(
            newMedicationProductCode: String,
            analyses: List<Pair<String, DrugInteractionAnalysisResult>>,
        ): DrugInteractionBatchResponse {
            val results = analyses.map { (requestedCode, analysis) ->
                DrugInteractionPairResponse.from(requestedCode, analysis)
            }
            val completed = results.count { it.processingStatus == InteractionCheckStatus.COMPLETED }
            val failed = results.count { it.processingStatus == InteractionCheckStatus.FAILED }
            val status = when {
                failed == results.size -> InteractionCheckStatus.FAILED
                completed == results.size -> InteractionCheckStatus.COMPLETED
                else -> InteractionCheckStatus.PARTIAL
            }
            return DrugInteractionBatchResponse(
                processingStatus = status,
                newMedicationProductCode = newMedicationProductCode,
                results = results,
                coverage = DrugInteractionBatchCoverage(
                    totalComparisons = results.size,
                    completedComparisons = completed,
                    partialComparisons = results.count { it.processingStatus == InteractionCheckStatus.PARTIAL },
                    failedComparisons = failed,
                    totalIngredientPairs = results.sumOf { it.coverage?.totalPairs ?: 0 },
                    completedIngredientPairs = results.sumOf { it.coverage?.completedPairs ?: 0 },
                    failedIngredientPairs = results.sumOf { it.coverage?.failedPairs ?: 0 },
                ),
                analyzedAt = analyses.maxOfOrNull { it.second.analyzedAt } ?: Instant.now(),
            )
        }
    }
}

data class DrugInteractionBatchCoverage(
    val totalComparisons: Int,
    val completedComparisons: Int,
    val partialComparisons: Int,
    val failedComparisons: Int,
    val totalIngredientPairs: Int,
    val completedIngredientPairs: Int,
    val failedIngredientPairs: Int,
)

data class DrugInteractionPairResponse(
    val requestedExistingProductCode: String,
    val processingStatus: InteractionCheckStatus,
    val newMedication: DrugProductResponse?,
    val existingMedication: DrugProductResponse?,
    val severity: String?,
    val summary: String,
    val evidence: List<DrugInteractionEvidenceResponse>,
    val coverage: DrugInteractionCoverageResponse?,
    val failedSteps: List<String>,
    val analyzedAt: Instant,
) {
    companion object {
        fun from(requestedCode: String, analysis: DrugInteractionAnalysisResult) = DrugInteractionPairResponse(
            requestedExistingProductCode = requestedCode,
            processingStatus = analysis.processingStatus,
            newMedication = analysis.leftProduct?.let(DrugProductResponse::from),
            existingMedication = analysis.rightProduct?.let(DrugProductResponse::from),
            severity = analysis.interaction?.status?.name,
            summary = analysis.interaction?.summary
                ?: "공식 제품 또는 성분을 확인하지 못했습니다. 안전하다는 의미가 아닙니다.",
            evidence = analysis.interaction?.ingredientPairs.orEmpty().flatMap(DrugInteractionEvidenceResponse::from),
            coverage = analysis.interaction?.coverage?.let {
                DrugInteractionCoverageResponse(
                    totalIngredients = it.totalIngredients,
                    resolvedIngredients = it.resolvedIngredients,
                    totalPairs = it.totalPairs,
                    completedPairs = it.completedPairs,
                    failedPairs = it.failedPairs,
                    percentage = it.percentage,
                    complete = it.complete,
                )
            },
            failedSteps = analysis.failedSteps.map { it.name }.sorted(),
            analyzedAt = analysis.analyzedAt,
        )
    }
}

data class DrugProductResponse(
    val productCode: String,
    val productName: String,
    val manufacturer: String?,
) {
    companion object {
        fun from(product: VerifiedDrugProduct) = DrugProductResponse(
            productCode = product.productCode,
            productName = product.productName,
            manufacturer = product.manufacturer,
        )
    }
}

data class DrugInteractionCoverageResponse(
    val totalIngredients: Int,
    val resolvedIngredients: Int,
    val totalPairs: Int,
    val completedPairs: Int,
    val failedPairs: Int,
    val percentage: Int,
    val complete: Boolean,
)

data class DrugInteractionEvidenceResponse(
    val ingredientA: String,
    val ingredientB: String,
    val evidenceType: String,
    val sourceName: String,
    val sourceUrl: String,
    val sourceRecordId: String?,
    val retrievedAt: Instant,
    val originalSummary: String?,
    val sourceType: String,
) {
    companion object {
        fun from(pair: IngredientPairResult): List<DrugInteractionEvidenceResponse> = pair.evidence.map { evidence ->
            DrugInteractionEvidenceResponse(
                ingredientA = pair.left.displayName,
                ingredientB = pair.right.displayName,
                evidenceType = pair.status.name,
                sourceName = evidence.sourceName,
                sourceUrl = evidence.providerReference,
                sourceRecordId = evidence.sourceRecordId,
                retrievedAt = evidence.retrievedAt,
                originalSummary = evidence.originalMessage,
                sourceType = evidence.sourceType,
            )
        }
    }
}
