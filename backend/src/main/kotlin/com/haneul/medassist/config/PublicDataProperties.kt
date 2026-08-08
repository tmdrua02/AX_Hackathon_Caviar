package com.haneul.medassist.config

import org.springframework.boot.context.properties.ConfigurationProperties
import java.time.Duration

@ConfigurationProperties("medassist.public-data.credentials")
class PublicDataCredentialsProperties(
    var serviceKey: String = "",
    var serviceKeyEncoded: Boolean = true,
) {
    override fun toString(): String =
        "PublicDataCredentialsProperties(serviceKey=***, serviceKeyEncoded=$serviceKeyEncoded)"
}

data class PublicDataClientPolicy(
    var connectTimeout: Duration = Duration.ofSeconds(2),
    var readTimeout: Duration = Duration.ofSeconds(5),
    var maxRetries: Int = 2,
    var retryBackoff: Duration = Duration.ofMillis(300),
    var permitsPerSecond: Int = 5,
    var maxConcurrentCalls: Int = 8,
    var circuitFailureThreshold: Int = 5,
    var circuitOpenDuration: Duration = Duration.ofSeconds(30),
)
