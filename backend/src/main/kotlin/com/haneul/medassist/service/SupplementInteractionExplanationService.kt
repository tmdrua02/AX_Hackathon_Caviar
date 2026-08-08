package com.haneul.medassist.service

import com.haneul.medassist.client.llm.SupplementInteractionExplanationClient
import com.haneul.medassist.domain.supplement.GeneratedSupplementInteractionExplanation
import com.haneul.medassist.domain.supplement.SupplementInteractionExplanation
import com.haneul.medassist.domain.supplement.SupplementInteractionExplanationRequest
import com.haneul.medassist.domain.supplement.SupplementInteractionExplanationStatus
import com.haneul.medassist.domain.supplement.SupplementInteractionFailureCode
import com.haneul.medassist.domain.supplement.SupplementInteractionSeverity
import org.springframework.stereotype.Service

@Service
class SupplementInteractionExplanationService(
    private val client: SupplementInteractionExplanationClient,
) {
    fun explain(request: SupplementInteractionExplanationRequest): SupplementInteractionExplanation {
        if (!client.isConfigured()) {
            return fallback(request, SupplementInteractionExplanationStatus.UNAVAILABLE)
        }
        val generated = try {
            client.generate(request)
        } catch (_: RuntimeException) {
            return fallback(request, SupplementInteractionExplanationStatus.FALLBACK)
        }
        return if (isValid(generated) && !containsUnsafeClaim(request.immutableDecision, generated)) {
            SupplementInteractionExplanation(
                status = SupplementInteractionExplanationStatus.GENERATED,
                summary = generated.summary.trim(),
                rationale = generated.rationale.trim(),
                consultationAdvice = generated.consultationAdvice.trim(),
                keyPoints = generated.keyPoints.map(String::trim),
                provider = client.provider,
                model = client.model,
            )
        } else {
            fallback(request, SupplementInteractionExplanationStatus.FALLBACK)
        }
    }

    private fun isValid(value: GeneratedSupplementInteractionExplanation): Boolean =
        value.summary.isNotBlank() && value.summary.length <= 1200 &&
            value.rationale.isNotBlank() && value.rationale.length <= 2500 &&
            value.consultationAdvice.isNotBlank() && value.consultationAdvice.length <= 1000 &&
            value.keyPoints.size <= 5 && value.keyPoints.all { it.isNotBlank() && it.length <= 500 }

    private fun containsUnsafeClaim(
        decision: SupplementInteractionSeverity,
        value: GeneratedSupplementInteractionExplanation,
    ): Boolean {
        if (decision != SupplementInteractionSeverity.UNKNOWN &&
            decision != SupplementInteractionSeverity.NO_VERIFIED_RULE_FOUND
        ) {
            return false
        }
        val text = buildString {
            append(value.summary)
            append(' ')
            append(value.rationale)
            append(' ')
            append(value.consultationAdvice)
            value.keyPoints.forEach { append(' ').append(it) }
        }.replace(" ", "")
        return UNSAFE_SAFE_CLAIMS.any { text.contains(it.replace(" ", ""), ignoreCase = true) }
    }

    private fun fallback(
        request: SupplementInteractionExplanationRequest,
        status: SupplementInteractionExplanationStatus,
    ): SupplementInteractionExplanation {
        val content = when (request.immutableDecision) {
            SupplementInteractionSeverity.AVOID_COMBINATION -> Triple(
                "검수된 병용 회피 근거가 확인되었습니다.",
                "확인된 근거와 전체 분석 범위는 함께 제공된 Evidence와 coverage에서 확인할 수 있습니다.",
                CONSULTATION_ADVICE,
            )
            SupplementInteractionSeverity.CAUTION -> Triple(
                "검수된 병용섭취 주의 근거가 확인되었습니다.",
                "세부 근거와 분석 범위는 함께 제공된 Evidence와 coverage에서 확인할 수 있습니다.",
                CONSULTATION_ADVICE,
            )
            SupplementInteractionSeverity.NO_VERIFIED_RULE_FOUND -> Triple(
                SupplementInteractionAnalysisService.NO_VERIFIED_RULE_MESSAGE,
                "분석 대상은 확인됐지만 현재 검수 catalog에서 일치하는 주의 규칙을 찾지 못했습니다.",
                CONSULTATION_ADVICE,
            )
            SupplementInteractionSeverity.UNKNOWN -> Triple(
                "현재 확보된 데이터만으로 병용 여부를 충분히 확인할 수 없습니다.",
                unknownRationale(request.failedSteps),
                CONSULTATION_ADVICE,
            )
        }
        return SupplementInteractionExplanation(
            status = status,
            summary = content.first,
            rationale = content.second,
            consultationAdvice = content.third,
            provider = client.provider,
            model = client.model,
        )
    }

    private fun unknownRationale(failedSteps: Set<SupplementInteractionFailureCode>): String =
        if (failedSteps.isEmpty()) {
            "분석 coverage가 완전하지 않아 추가 의료 사실을 추론하지 않았습니다."
        } else {
            "확인하지 못한 단계: ${failedSteps.joinToString(", ") { it.name }}. 추가 의료 사실을 추론하지 않았습니다."
        }

    companion object {
        const val CONSULTATION_ADVICE = "복용 전 의사 또는 약사와 상담하세요."
        private val UNSAFE_SAFE_CLAIMS = listOf(
            "안전합니다",
            "같이 드셔도 됩니다",
            "함께 드셔도 됩니다",
            "문제없습니다",
            "복용 가능합니다",
            "함께 복용해도 괜찮습니다",
            "병용 가능합니다",
        )
    }
}
