package com.haneul.medassist.data

import kotlinx.serialization.Serializable

@Serializable
data class DrugInteractionBatchRequest(
    val newMedicationProductCode: String,
    val existingMedicationProductCodes: List<String>,
)

@Serializable
data class DrugInteractionBatchResponse(
    val processingStatus: String,
    val newMedicationProductCode: String,
    val results: List<DrugInteractionPairResponse>,
    val coverage: DrugInteractionBatchCoverage,
    val analyzedAt: String,
    val disclaimer: String,
)

@Serializable
data class DrugInteractionBatchCoverage(
    val totalComparisons: Int,
    val completedComparisons: Int,
    val partialComparisons: Int,
    val failedComparisons: Int,
    val totalIngredientPairs: Int,
    val completedIngredientPairs: Int,
    val failedIngredientPairs: Int,
)

@Serializable
data class DrugInteractionPairResponse(
    val requestedExistingProductCode: String,
    val processingStatus: String,
    val newMedication: DrugInteractionProductResponse? = null,
    val existingMedication: DrugInteractionProductResponse? = null,
    val severity: Severity? = null,
    val summary: String,
    val evidence: List<DrugInteractionEvidenceResponse> = emptyList(),
    val coverage: DrugInteractionCoverageResponse? = null,
    val failedSteps: List<String> = emptyList(),
    val analyzedAt: String,
)

@Serializable
data class DrugInteractionProductResponse(
    val productCode: String,
    val productName: String,
    val manufacturer: String? = null,
)

@Serializable
data class DrugInteractionCoverageResponse(
    val totalIngredients: Int,
    val resolvedIngredients: Int,
    val totalPairs: Int,
    val completedPairs: Int,
    val failedPairs: Int,
    val percentage: Int,
    val complete: Boolean,
)

@Serializable
data class DrugInteractionEvidenceResponse(
    val ingredientA: String,
    val ingredientB: String,
    val evidenceType: String,
    val sourceName: String,
    val sourceUrl: String,
    val sourceRecordId: String? = null,
    val retrievedAt: String,
    val originalSummary: String? = null,
    val sourceType: String,
)
