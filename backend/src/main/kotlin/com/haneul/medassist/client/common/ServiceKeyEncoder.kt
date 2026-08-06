package com.haneul.medassist.client.common

import com.haneul.medassist.config.PublicDataCredentialsProperties
import com.haneul.medassist.exception.ApiErrorCode
import com.haneul.medassist.exception.PublicDataApiException
import org.springframework.stereotype.Component
import org.springframework.web.util.UriUtils
import java.nio.charset.StandardCharsets

@Component
class ServiceKeyEncoder(
    private val credentials: PublicDataCredentialsProperties,
) {
    fun encodedQueryValue(): String {
        val key = credentials.serviceKey.trim()
        if (key.isBlank()) throw PublicDataApiException(ApiErrorCode.PUBLIC_API_NOT_CONFIGURED)
        val decoded = if (credentials.serviceKeyEncoded) {
            // Percent escape만 해제하므로 URLDecoder와 달리 '+'를 공백으로 바꾸지 않는다.
            UriUtils.decode(key, StandardCharsets.UTF_8)
        } else {
            key
        }
        return UriUtils.encode(decoded, StandardCharsets.UTF_8)
    }
}
