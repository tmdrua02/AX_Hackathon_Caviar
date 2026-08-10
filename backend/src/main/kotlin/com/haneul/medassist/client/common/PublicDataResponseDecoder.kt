package com.haneul.medassist.client.common

import com.haneul.medassist.exception.ApiErrorCode
import com.haneul.medassist.exception.PublicDataApiException
import org.springframework.http.MediaType
import org.springframework.stereotype.Component
import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.Charset
import java.nio.charset.CodingErrorAction
import java.nio.charset.StandardCharsets

@Component
class PublicDataResponseDecoder {
    fun decode(body: ByteArray?, contentType: MediaType?): String {
        if (body == null || body.isEmpty()) {
            throw PublicDataApiException(ApiErrorCode.PUBLIC_API_INVALID_RESPONSE, "공공 API 응답 본문이 비어 있습니다.")
        }

        val charset = contentType?.charset ?: declaredXmlCharset(body) ?: StandardCharsets.UTF_8
        val decoded = try {
            charset.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
                .decode(ByteBuffer.wrap(body))
                .toString()
                .removePrefix("\uFEFF")
        } catch (exception: CharacterCodingException) {
            throw invalidEncoding(exception)
        }

        if (decoded.contains(REPLACEMENT_CHARACTER)) {
            throw invalidEncoding()
        }
        return decoded
    }

    private fun declaredXmlCharset(body: ByteArray): Charset? {
        val prefix = String(body, 0, minOf(body.size, XML_DECLARATION_SCAN_BYTES), StandardCharsets.ISO_8859_1)
        val name = XML_ENCODING.find(prefix)?.groupValues?.get(1) ?: return null
        return try {
            Charset.forName(name)
        } catch (exception: Exception) {
            throw invalidEncoding(exception)
        }
    }

    private fun invalidEncoding(cause: Throwable? = null): PublicDataApiException = PublicDataApiException(
        ApiErrorCode.PUBLIC_API_INVALID_RESPONSE,
        "공공 API 응답 문자 인코딩을 안전하게 해석할 수 없습니다.",
        cause,
    )

    companion object {
        private const val REPLACEMENT_CHARACTER = '\uFFFD'
        private const val XML_DECLARATION_SCAN_BYTES = 256
        private val XML_ENCODING = Regex("""<\?xml[^>]*encoding\s*=\s*[\"']([^\"']+)[\"']""", RegexOption.IGNORE_CASE)
    }
}
