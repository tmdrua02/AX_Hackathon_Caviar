package com.haneul.medassist.client.dur

import com.haneul.medassist.client.common.ServiceKeyEncoder
import com.haneul.medassist.config.DurApiProperties
import com.haneul.medassist.config.PublicDataCredentialsProperties
import org.junit.jupiter.api.Test
import org.springframework.web.util.UriComponentsBuilder
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DurPublicDataUriFactoryTest {
    @Test
    fun `encoded DUR service key is present exactly once without percent re-encoding`() {
        val uri = factory("dummy%2Fsegment%2Bvalue%3D", true).lookupUri(request(), 1)
        val ascii = uri.toASCIIString()

        assertTrue(ascii.contains("serviceKey=dummy%2Fsegment%2Bvalue%3D", ignoreCase = true))
        assertFalse(ascii.contains("%252F", ignoreCase = true))
        assertFalse(ascii.contains("%253D", ignoreCase = true))
    }

    @Test
    fun `raw plus is encoded as plus and never converted to space`() {
        val uri = factory("dummy/segment+value=", false).lookupUri(request(), 1)
        val ascii = uri.toASCIIString()

        assertTrue(ascii.contains("%2B", ignoreCase = true))
        assertFalse(ascii.contains("%20"))
        assertFalse(uri.rawQuery.contains("+"))
    }

    @Test
    fun `build true preserves pre-encoded value while build false demonstrates the forbidden path`() {
        val encoded = "dummy%2Fsegment%2Bvalue%3D"
        val buildTrue = UriComponentsBuilder.fromUriString("https://example.invalid/dur")
            .queryParam("serviceKey", encoded)
            .build(true)
            .toUri()
        val buildFalse = UriComponentsBuilder.fromUriString("https://example.invalid/dur")
            .queryParam("serviceKey", encoded)
            .build(false)
            .toUri()

        assertFalse(buildTrue.rawQuery.contains("%252F", ignoreCase = true))
        assertTrue(buildFalse.rawQuery.contains("%252F", ignoreCase = true))
        assertTrue(buildFalse.rawQuery.contains("%253D", ignoreCase = true))
    }

    @Test
    fun `blank optional Korean name is omitted from URI`() {
        val uri = factory("dummy%2Fsegment%2Bvalue%3D", true).lookupUri(
            DurLookupRequest("D000762", " ", DurLookupDirection.FORWARD),
            1,
        )

        assertNull(UriComponentsBuilder.fromUri(uri).build(true).queryParams.getFirst("ingrKorName"))
    }

    private fun factory(key: String, encoded: Boolean) = DurPublicDataUriFactory(
        DurApiProperties(),
        ServiceKeyEncoder(PublicDataCredentialsProperties(key, encoded)),
    )

    private fun request() = DurLookupRequest("D000762", "이트라코나졸", DurLookupDirection.FORWARD)
}
