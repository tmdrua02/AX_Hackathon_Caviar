package com.haneul.medassist.client.llm

import com.haneul.medassist.client.common.PublicDataResponseDecoder
import com.haneul.medassist.config.OpenAiExplanationProperties
import com.haneul.medassist.config.RestClientConfig
import com.haneul.medassist.domain.supplement.SupplementInteractionExplanationStatus
import com.haneul.medassist.service.SupplementInteractionExplanationService
import com.haneul.medassist.support.explanationRequest
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable
import tools.jackson.databind.json.JsonMapper
import java.time.Duration
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@Tag("external-llm")
@EnabledIfEnvironmentVariable(named = "OPENAI_API_KEY", matches = ".+")
class OpenAiExplanationExternalTest {
    @Test
    fun `OpenAI explains synthetic UNKNOWN evidence as structured text`() {
        val properties = OpenAiExplanationProperties(
            baseUrl = System.getenv("OPENAI_BASE_URL")?.takeIf(String::isNotBlank) ?: "https://api.openai.com",
            responsesPath = System.getenv("OPENAI_RESPONSES_PATH")?.takeIf(String::isNotBlank) ?: "/v1/responses",
            apiKey = requireNotNull(System.getenv("OPENAI_API_KEY")),
            model = System.getenv("OPENAI_CHAT_MODEL")?.takeIf(String::isNotBlank) ?: "gpt-4o-mini",
            connectTimeout = Duration.ofSeconds(5),
            readTimeout = Duration.ofSeconds(30),
            maxRetries = 0,
        )
        val client = OpenAiSupplementInteractionExplanationClient(
            RestClientConfig().openAiExplanationRestClient(properties),
            properties,
            JsonMapper.builder().findAndAddModules().build(),
            PublicDataResponseDecoder(),
            LlmCallExecutor(properties),
        )

        val request = explanationRequest()
        val immutableSnapshot = request.copy()
        val explanation = SupplementInteractionExplanationService(client).explain(request)

        assertEquals(SupplementInteractionExplanationStatus.GENERATED, explanation.status)
        assertEquals(properties.model, explanation.model)
        assertTrue(explanation.summary.isNotBlank())
        assertTrue(explanation.rationale.isNotBlank())
        assertTrue(explanation.consultationAdvice.isNotBlank())
        assertEquals(immutableSnapshot, request)
        assertEquals(immutableSnapshot.immutableDecision, request.immutableDecision)
        assertEquals(immutableSnapshot.coverage, request.coverage)
        assertEquals(immutableSnapshot.failedSteps, request.failedSteps)
        assertEquals(
            immutableSnapshot.officialDrugIngredients.map { it.providerCode },
            request.officialDrugIngredients.map { it.providerCode },
        )
        assertEquals(
            immutableSnapshot.verifiedSupplementIngredients.map { it.canonicalId },
            request.verifiedSupplementIngredients.map { it.canonicalId },
        )
        assertEquals(immutableSnapshot.matchedRules.map { it.ruleId }, request.matchedRules.map { it.ruleId })
        assertEquals(
            immutableSnapshot.evidence.map { it.sourceReferenceId },
            request.evidence.map { it.sourceReferenceId },
        )
    }
}
