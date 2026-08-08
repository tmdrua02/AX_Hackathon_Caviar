package com.haneul.medassist.service

import com.haneul.medassist.client.llm.LlmFailureCode
import com.haneul.medassist.client.llm.LlmProviderException
import com.haneul.medassist.client.llm.SupplementInteractionExplanationClient
import com.haneul.medassist.domain.supplement.GeneratedSupplementInteractionExplanation
import com.haneul.medassist.domain.supplement.SupplementInteractionExplanationRequest
import com.haneul.medassist.domain.supplement.SupplementInteractionExplanationStatus
import com.haneul.medassist.domain.supplement.SupplementInteractionFailureCode
import com.haneul.medassist.domain.supplement.SupplementInteractionSeverity
import com.haneul.medassist.support.explanationRequest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SupplementInteractionExplanationServiceTest {
    @Test
    fun `AVOID and CAUTION receive generated explanations without changing immutable decisions`() {
        for (severity in listOf(
            SupplementInteractionSeverity.AVOID_COMBINATION,
            SupplementInteractionSeverity.CAUTION,
        )) {
            val request = explanationRequest(severity, emptySet())
            val client = fakeClient { generated("확인된 근거를 설명합니다.") }

            val explanation = SupplementInteractionExplanationService(client).explain(request)

            assertEquals(SupplementInteractionExplanationStatus.GENERATED, explanation.status)
            assertEquals(severity, request.immutableDecision)
        }
    }

    @Test
    fun `UNKNOWN unsafe safe-claim is discarded for deterministic fallback`() {
        val request = explanationRequest(SupplementInteractionSeverity.UNKNOWN)
        val client = fakeClient { generated("같이 드셔도 됩니다") }

        val explanation = SupplementInteractionExplanationService(client).explain(request)

        assertEquals(SupplementInteractionExplanationStatus.FALLBACK, explanation.status)
        assertTrue(explanation.summary.contains("충분히 확인할 수 없습니다"))
        assertFalse(explanation.summary.contains("같이 드셔도 됩니다"))
    }

    @Test
    fun `NO_VERIFIED_RULE_FOUND can never be presented as safe`() {
        val request = explanationRequest(SupplementInteractionSeverity.NO_VERIFIED_RULE_FOUND, emptySet())
        val client = fakeClient { generated("문제없습니다") }

        val explanation = SupplementInteractionExplanationService(client).explain(request)

        assertEquals(SupplementInteractionExplanationStatus.FALLBACK, explanation.status)
        assertTrue(explanation.summary.contains("안전하다는 의미가 아닙니다"))
    }

    @Test
    fun `provider timeout auth rate limit server and parse failures preserve fallback`() {
        val failures = listOf(
            LlmFailureCode.TIMEOUT,
            LlmFailureCode.AUTH_FAILED,
            LlmFailureCode.RATE_LIMITED,
            LlmFailureCode.UNAVAILABLE,
            LlmFailureCode.INVALID_RESPONSE,
            LlmFailureCode.EMPTY_RESPONSE,
        )
        failures.forEach { failure ->
            val service = SupplementInteractionExplanationService(fakeClient { throw LlmProviderException(failure) })

            val result = service.explain(explanationRequest(SupplementInteractionSeverity.CAUTION, emptySet()))

            assertEquals(SupplementInteractionExplanationStatus.FALLBACK, result.status, failure.name)
            assertTrue(result.summary.contains("주의 근거"), failure.name)
        }
    }

    @Test
    fun `missing API key is unavailable while deterministic fallback remains`() {
        val client = fakeClient(configured = false) { error("must not be called") }

        val result = SupplementInteractionExplanationService(client).explain(explanationRequest())

        assertEquals(SupplementInteractionExplanationStatus.UNAVAILABLE, result.status)
        assertTrue(result.consultationAdvice.contains("의사 또는 약사"))
    }

    @Test
    fun `blank or oversized structured fields use fallback`() {
        val blank = SupplementInteractionExplanationService(fakeClient { generated("") }).explain(explanationRequest())
        val oversized = SupplementInteractionExplanationService(fakeClient {
            generated("x".repeat(1201))
        }).explain(explanationRequest())

        assertEquals(SupplementInteractionExplanationStatus.FALLBACK, blank.status)
        assertEquals(SupplementInteractionExplanationStatus.FALLBACK, oversized.status)
    }

    private fun generated(summary: String) = GeneratedSupplementInteractionExplanation(
        summary = summary,
        rationale = "제공된 Evidence 범위만 설명합니다.",
        consultationAdvice = "의사 또는 약사와 상담하세요.",
        keyPoints = listOf("새로운 의료 사실을 생성하지 않습니다."),
    )

    private fun fakeClient(
        configured: Boolean = true,
        answer: (SupplementInteractionExplanationRequest) -> GeneratedSupplementInteractionExplanation,
    ) = object : SupplementInteractionExplanationClient {
        override val provider = "OPENAI"
        override val model = "TEST_MODEL"
        override fun isConfigured() = configured
        override fun generate(request: SupplementInteractionExplanationRequest) = answer(request)
    }
}
