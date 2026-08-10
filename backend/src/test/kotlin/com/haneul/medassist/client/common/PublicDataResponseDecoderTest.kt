package com.haneul.medassist.client.common

import com.haneul.medassist.exception.ApiErrorCode
import com.haneul.medassist.exception.PublicDataApiException
import org.junit.jupiter.api.Test
import org.springframework.http.MediaType
import java.nio.charset.StandardCharsets
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertFailsWith

class PublicDataResponseDecoderTest {
    private val decoder = PublicDataResponseDecoder()

    @Test
    fun `json without charset is decoded as strict UTF-8`() {
        val original = """{"ITEM_NAME":"타이레놀정500밀리그람","MTRAL_NM":"아세트아미노펜","INGD_UNIT_CD":"밀리그램","ENTP_NAME":"켄뷰코리아"}"""

        val decoded = decoder.decode(original.toByteArray(StandardCharsets.UTF_8), MediaType.APPLICATION_JSON)

        assertEquals(original, decoded)
        assertFalse(decoded.contains('\uFFFD'))
    }

    @Test
    fun `malformed UTF-8 is rejected instead of replacing characters`() {
        val exception = assertFailsWith<PublicDataApiException> {
            decoder.decode(byteArrayOf(0xC3.toByte(), 0x28), MediaType.APPLICATION_JSON)
        }

        assertEquals(ApiErrorCode.PUBLIC_API_INVALID_RESPONSE, exception.errorCode)
    }

    @Test
    fun `an existing replacement character is rejected`() {
        val body = """{"ITEM_NAME":"타이레놀�"}""".toByteArray(StandardCharsets.UTF_8)

        val exception = assertFailsWith<PublicDataApiException> {
            decoder.decode(body, MediaType.APPLICATION_JSON)
        }

        assertEquals(ApiErrorCode.PUBLIC_API_INVALID_RESPONSE, exception.errorCode)
    }

    @Test
    fun `UTF-8 XML without charset remains supported`() {
        val original = """<?xml version="1.0"?><response><value>아세트아미노펜</value></response>"""

        val decoded = decoder.decode(original.toByteArray(StandardCharsets.UTF_8), MediaType.APPLICATION_XML)

        assertEquals(original, decoded)
    }
}
