package com.haneul.medassist.domain.interaction

import com.haneul.medassist.domain.medication.Ingredient
import java.time.Instant
import java.util.UUID

enum class InteractionSeverity {
    PROHIBITED,
    CAUTION,
    DUPLICATE_OR_SIMILAR,
    NO_KNOWN_ISSUE,
    UNKNOWN,
}

enum class InteractionCheckStatus {
    PENDING,
    RUNNING,
    COMPLETED,
    PARTIAL,
    FAILED,
}

data class Evidence(
    val sourceType: String,
    val sourceName: String,
    val sourceRecordId: String?,
    val providerReference: String,
    val retrievedAt: Instant,
    val originalMessage: String?,
    val normalizedMessage: String?,
    val authority: String,
    val reviewStatus: String,
)

data class IngredientPairResult(
    val left: Ingredient,
    val right: Ingredient,
    val status: PairStatus,
    val evidence: List<Evidence>,
    val safeErrorCode: String? = null,
)

enum class PairStatus {
    PROHIBITED,
    CAUTION,
    DUPLICATE,
    NO_MATCH,
    FAILED,
}

data class Coverage(
    val totalProducts: Int,
    val resolvedProducts: Int,
    val totalIngredients: Int,
    val resolvedIngredients: Int,
    val totalPairs: Int,
    val completedPairs: Int,
    val failedPairs: Int,
    val percentage: Int,
    val complete: Boolean,
)

data class InteractionResult(
    val status: InteractionSeverity,
    val summary: String,
    val ingredientPairs: List<IngredientPairResult>,
    val evidence: List<Evidence>,
    val coverage: Coverage,
    val consultationNotice: String = CONSULTATION_NOTICE,
) {
    companion object {
        const val CONSULTATION_NOTICE =
            "정보 제공용이며 복용을 시작·중단·변경하기 전에 의사 또는 약사와 상담하세요."
    }
}

data class InteractionCheck(
    val id: UUID,
    val userId: UUID,
    val status: InteractionCheckStatus,
    val coverage: Coverage?,
    val finalResult: InteractionResult?,
    val createdAt: Instant,
    val completedAt: Instant?,
)
