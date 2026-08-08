package com.haneul.medassist.domain.supplement

enum class SupplementInteractionExplanationStatus {
    GENERATED,
    FALLBACK,
    UNAVAILABLE,
}

data class SupplementInteractionExplanation(
    val status: SupplementInteractionExplanationStatus,
    val summary: String,
    val rationale: String,
    val consultationAdvice: String,
    val keyPoints: List<String> = emptyList(),
    val provider: String? = null,
    val model: String? = null,
)

data class GeneratedSupplementInteractionExplanation(
    val summary: String,
    val rationale: String,
    val consultationAdvice: String,
    val keyPoints: List<String> = emptyList(),
)

data class SupplementInteractionPresentationResult(
    val analysis: SupplementInteractionAnalysisResult,
    val explanation: SupplementInteractionExplanation,
)
