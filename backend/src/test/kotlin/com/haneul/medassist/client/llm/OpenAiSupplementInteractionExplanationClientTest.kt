package com.haneul.medassist.client.llm

import com.haneul.medassist.client.common.PublicDataResponseDecoder
import com.haneul.medassist.config.OpenAiExplanationProperties
import com.haneul.medassist.config.RestClientConfig
import com.haneul.medassist.domain.supplement.GeneratedSupplementInteractionExplanation
import com.haneul.medassist.support.explanationRequest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import tools.jackson.databind.ObjectMapper
import tools.jackson.databind.json.JsonMapper
import java.time.Duration
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class OpenAiSupplementInteractionExplanationClientTest {
    private lateinit var server: MockWebServer
    private val mapper: ObjectMapper = JsonMapper.builder().findAndAddModules().build()

    @BeforeEach
    fun start() {
        server = MockWebServer()
        server.start()
    }

    @AfterEach
    fun stop() {
        server.shutdown()
    }

    @Test
    fun `structured Responses API output is parsed without decision fields`() {
        server.enqueue(successResponse())
        val client = client(maxRetries = 0)

        val result = client.generate(explanationRequest())
        val recorded = server.takeRequest()
        val requestBody = recorded.body.readUtf8()

        assertEquals("설명 요약", result.summary)
        assertEquals("/v1/responses", recorded.path)
        assertEquals("Bearer explicit-dummy-key", recorded.getHeader("Authorization"))
        assertTrue(requestBody.contains("immutableDecision"))
        assertTrue(requestBody.contains("Evidence에 없는 의료 사실"))
        assertTrue(requestBody.contains("json_schema"))
        assertFalse(requestBody.contains("canTakeTogether"))
    }

    @Test
    fun `401 and 403 are non-retryable authentication failures`() {
        server.enqueue(jsonResponse(401, "{}"))
        server.enqueue(jsonResponse(403, "{}"))
        val client = client(maxRetries = 2)

        repeat(2) {
            val error = assertFailsWith<LlmProviderException> { client.generate(explanationRequest()) }
            assertEquals(LlmFailureCode.AUTH_FAILED, error.failureCode)
            assertFalse(error.retryable)
        }
        assertEquals(2, server.requestCount)
    }

    @Test
    fun `429 and 500 retry once then expose safe failure category`() {
        server.enqueue(jsonResponse(429, "{}"))
        server.enqueue(jsonResponse(429, "{}"))
        val rateError = assertFailsWith<LlmProviderException> { client(maxRetries = 1).generate(explanationRequest()) }
        assertEquals(LlmFailureCode.RATE_LIMITED, rateError.failureCode)

        server.enqueue(jsonResponse(500, "{}"))
        server.enqueue(jsonResponse(500, "{}"))
        val serverError = assertFailsWith<LlmProviderException> { client(maxRetries = 1).generate(explanationRequest()) }
        assertEquals(LlmFailureCode.UNAVAILABLE, serverError.failureCode)
        assertEquals(4, server.requestCount)
    }

    @Test
    fun `read timeout is categorized and bounded`() {
        server.enqueue(
            successResponse().setBodyDelay(250, TimeUnit.MILLISECONDS),
        )

        val error = assertFailsWith<LlmProviderException> {
            client(maxRetries = 0, readTimeout = Duration.ofMillis(50)).generate(explanationRequest())
        }

        assertEquals(LlmFailureCode.TIMEOUT, error.failureCode)
    }

    @Test
    fun `malformed envelope empty output and malformed structured JSON are rejected`() {
        server.enqueue(jsonResponse(200, "not-json"))
        var error = assertFailsWith<LlmProviderException> { client(maxRetries = 0).generate(explanationRequest()) }
        assertEquals(LlmFailureCode.INVALID_RESPONSE, error.failureCode)

        server.enqueue(jsonResponse(200, """{"output":[]}"""))
        error = assertFailsWith { client(maxRetries = 0).generate(explanationRequest()) }
        assertEquals(LlmFailureCode.EMPTY_RESPONSE, error.failureCode)

        val malformedStructured = """{"output":[{"content":[{"type":"output_text","text":"not-json"}]}]}"""
        server.enqueue(jsonResponse(200, malformedStructured))
        error = assertFailsWith { client(maxRetries = 0).generate(explanationRequest()) }
        assertEquals(LlmFailureCode.INVALID_RESPONSE, error.failureCode)
    }

    @Test
    fun `missing API key is rejected before network request`() {
        val client = client(maxRetries = 0, apiKey = "")

        val error = assertFailsWith<LlmProviderException> { client.generate(explanationRequest()) }

        assertEquals(LlmFailureCode.NOT_CONFIGURED, error.failureCode)
        assertEquals(0, server.requestCount)
    }

    @Test
    fun `configuration string masks API key`() {
        val properties = OpenAiExplanationProperties(apiKey = "must-never-appear")

        assertFalse(properties.toString().contains("must-never-appear"))
        assertTrue(properties.toString().contains("apiKey=***"))
    }

    private fun client(
        maxRetries: Int,
        readTimeout: Duration = Duration.ofSeconds(2),
        apiKey: String = "explicit-dummy-key",
    ): OpenAiSupplementInteractionExplanationClient {
        val properties = OpenAiExplanationProperties(
            baseUrl = server.url("/").toString().removeSuffix("/"),
            responsesPath = "/v1/responses",
            apiKey = apiKey,
            model = "test-model",
            connectTimeout = Duration.ofSeconds(1),
            readTimeout = readTimeout,
            maxRetries = maxRetries,
            retryBackoff = Duration.ofMillis(1),
            circuitFailureThreshold = 100,
        )
        return OpenAiSupplementInteractionExplanationClient(
            RestClientConfig().openAiExplanationRestClient(properties),
            properties,
            mapper,
            PublicDataResponseDecoder(),
            LlmCallExecutor(properties),
        )
    }

    private fun successResponse(): MockResponse {
        val generated = GeneratedSupplementInteractionExplanation(
            summary = "설명 요약",
            rationale = "근거 범위 설명",
            consultationAdvice = "의사 또는 약사와 상담하세요.",
            keyPoints = listOf("TEST point"),
        )
        val envelope = mapper.createObjectNode()
        val content = mapper.createObjectNode()
            .put("type", "output_text")
            .put("text", mapper.writeValueAsString(generated))
        val output = mapper.createObjectNode()
        output.putArray("content").add(content)
        envelope.putArray("output").add(output)
        return jsonResponse(200, mapper.writeValueAsString(envelope))
    }

    private fun jsonResponse(status: Int, body: String) = MockResponse()
        .setResponseCode(status)
        .setHeader("Content-Type", "application/json")
        .setBody(body)
}
