package com.haneul.medassist.client.llm

import com.haneul.medassist.client.common.PublicDataResponseDecoder
import com.haneul.medassist.config.OpenAiExplanationProperties
import com.haneul.medassist.domain.supplement.GeneratedSupplementInteractionExplanation
import com.haneul.medassist.domain.supplement.SupplementInteractionExplanationRequest
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.http.HttpHeaders
import org.springframework.stereotype.Component
import org.springframework.web.client.ResourceAccessException
import org.springframework.web.client.RestClient
import org.springframework.web.client.RestClientException
import org.springframework.web.client.RestClientResponseException
import tools.jackson.databind.JsonNode
import tools.jackson.databind.ObjectMapper

interface SupplementInteractionExplanationClient {
    val provider: String
    val model: String
    fun isConfigured(): Boolean
    fun generate(request: SupplementInteractionExplanationRequest): GeneratedSupplementInteractionExplanation
}

@Component
class OpenAiSupplementInteractionExplanationClient(
    @Qualifier("openAiExplanationRestClient") private val restClient: RestClient,
    private val properties: OpenAiExplanationProperties,
    private val objectMapper: ObjectMapper,
    private val responseDecoder: PublicDataResponseDecoder,
    @Qualifier("llmCallExecutor") private val callExecutor: LlmCallExecutor,
) : SupplementInteractionExplanationClient {
    override val provider: String = "OPENAI"
    override val model: String get() = properties.model

    override fun isConfigured(): Boolean = properties.apiKey.isNotBlank() && properties.model.isNotBlank()

    override fun generate(request: SupplementInteractionExplanationRequest): GeneratedSupplementInteractionExplanation {
        if (!isConfigured()) throw LlmProviderException(LlmFailureCode.NOT_CONFIGURED)
        return callExecutor.execute {
            val payload = payload(request)
            val bytes = try {
                restClient.post()
                    .uri(properties.responsesPath)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer ${properties.apiKey}")
                    .header(HttpHeaders.CONTENT_TYPE, "application/json")
                    .body(payload)
                    .retrieve()
                    .toEntity(ByteArray::class.java)
            } catch (exception: RestClientResponseException) {
                throw mapHttpError(exception)
            } catch (exception: ResourceAccessException) {
                throw LlmProviderException(LlmFailureCode.TIMEOUT, retryable = true, cause = safeCause(exception))
            } catch (exception: RestClientException) {
                throw LlmProviderException(LlmFailureCode.TIMEOUT, retryable = true, cause = safeCause(exception))
            }
            val decoded = responseDecoder.decode(bytes.body, bytes.headers.contentType)
            parseEnvelope(decoded)
        }
    }

    private fun payload(request: SupplementInteractionExplanationRequest): JsonNode {
        val payload = objectMapper.createObjectNode()
        payload.put("model", properties.model)
        payload.put("store", false)
        payload.put("instructions", SYSTEM_INSTRUCTIONS)
        payload.put("input", objectMapper.writeValueAsString(request))
        val format = payload.putObject("text").putObject("format")
        format.put("type", "json_schema")
        format.put("name", "supplement_interaction_explanation")
        format.put("strict", true)
        format.set("schema", responseSchema())
        return payload
    }

    private fun responseSchema(): JsonNode {
        val schema = objectMapper.createObjectNode()
        schema.put("type", "object")
        schema.put("additionalProperties", false)
        schema.putArray("required").add("summary").add("rationale").add("consultationAdvice").add("keyPoints")
        val properties = schema.putObject("properties")
        properties.putObject("summary").put("type", "string").put("maxLength", 1200)
        properties.putObject("rationale").put("type", "string").put("maxLength", 2500)
        properties.putObject("consultationAdvice").put("type", "string").put("maxLength", 1000)
        properties.putObject("keyPoints")
            .put("type", "array")
            .put("maxItems", 5)
            .set("items", objectMapper.createObjectNode().put("type", "string").put("maxLength", 500))
        return schema
    }

    private fun parseEnvelope(raw: String): GeneratedSupplementInteractionExplanation {
        val envelope = runCatching { objectMapper.readTree(raw) }
            .getOrElse { throw LlmProviderException(LlmFailureCode.INVALID_RESPONSE, cause = it) }
        var outputText = ""
        val outputs = envelope.at("/output")
        for (outputIndex in 0 until outputs.size()) {
            val contents = outputs.get(outputIndex).at("/content")
            for (contentIndex in 0 until contents.size()) {
                val content = contents.get(contentIndex)
                if (content.get("type")?.asString("") == "output_text") {
                    outputText = content.get("text")?.asString("")?.trim().orEmpty()
                    break
                }
            }
            if (outputText.isNotBlank()) break
        }
        if (outputText.isBlank()) throw LlmProviderException(LlmFailureCode.EMPTY_RESPONSE)
        return runCatching {
            objectMapper.readValue(outputText, GeneratedSupplementInteractionExplanation::class.java)
        }.getOrElse { throw LlmProviderException(LlmFailureCode.INVALID_RESPONSE, cause = it) }
    }

    private fun mapHttpError(exception: RestClientResponseException): LlmProviderException = when (exception.statusCode.value()) {
        401, 403 -> LlmProviderException(LlmFailureCode.AUTH_FAILED, cause = safeCause(exception))
        429 -> LlmProviderException(LlmFailureCode.RATE_LIMITED, retryable = true, cause = safeCause(exception))
        in 500..599 -> LlmProviderException(LlmFailureCode.UNAVAILABLE, retryable = true, cause = safeCause(exception))
        else -> LlmProviderException(LlmFailureCode.INVALID_RESPONSE, cause = safeCause(exception))
    }

    private fun safeCause(cause: Throwable): Throwable = IllegalStateException(cause::class.simpleName)

    companion object {
        const val SYSTEM_INSTRUCTIONS = """
            당신은 의료 판정을 수행하지 않는 설명 전용 presentation layer다.
            backend가 제공한 immutableDecision, severity 의미, coverage, failedSteps와 모든 식별자를 변경하지 않는다.
            제공된 Evidence에 없는 의료 사실이나 약물·건강기능식품 상호작용을 생성하거나 추론하지 않는다.
            UNKNOWN을 안전하다고 표현하지 않고, NO_VERIFIED_RULE_FOUND를 병용 가능 또는 안전으로 표현하지 않는다.
            약 복용 시작·중단·증량·감량을 직접 지시하지 않는다.
            제공된 공식 근거와 불확실성을 사용자가 이해하기 쉽게 한국어로 설명한다.
            근거가 불완전하면 확인하지 못한 단계만 설명하고 의사 또는 약사 상담을 안내한다.
            출력은 지정된 JSON schema만 따르며 판정·코드·ID 필드를 새로 출력하지 않는다.
        """
    }
}
