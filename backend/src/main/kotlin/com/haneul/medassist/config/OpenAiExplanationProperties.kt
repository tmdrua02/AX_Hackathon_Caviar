package com.haneul.medassist.config

import org.springframework.boot.context.properties.ConfigurationProperties
import java.time.Duration

@ConfigurationProperties("medassist.llm.openai")
data class OpenAiExplanationProperties(
    var baseUrl: String = "https://api.openai.com",
    var responsesPath: String = "/v1/responses",
    var apiKey: String = "",
    var model: String = "gpt-4o-mini",
    var connectTimeout: Duration = Duration.ofSeconds(3),
    var readTimeout: Duration = Duration.ofSeconds(20),
    var maxRetries: Int = 1,
    var retryBackoff: Duration = Duration.ofMillis(250),
    var maxConcurrentCalls: Int = 4,
    var circuitFailureThreshold: Int = 3,
    var circuitOpenDuration: Duration = Duration.ofSeconds(30),
) {
    override fun toString(): String =
        "OpenAiExplanationProperties(baseUrl=$baseUrl, responsesPath=$responsesPath, apiKey=***, " +
            "model=$model, connectTimeout=$connectTimeout, readTimeout=$readTimeout, maxRetries=$maxRetries)"
}
