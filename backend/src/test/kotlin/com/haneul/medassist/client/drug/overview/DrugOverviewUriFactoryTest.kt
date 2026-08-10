package com.haneul.medassist.client.drug.overview

import com.haneul.medassist.client.common.ServiceKeyEncoder
import com.haneul.medassist.config.DrugOverviewApiProperties
import com.haneul.medassist.config.PublicDataCredentialsProperties
import org.junit.jupiter.api.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DrugOverviewUriFactoryTest {
    @Test
    fun `e약은요 uses uppercase ServiceKey and encodes it exactly once`() {
        val uri = factory("dummy%2Fsegment%2Bvalue%3D", true).lookupUri(
            DrugOverviewProviderQuery(itemSeq = "202106092"),
            1,
        )
        val ascii = uri.toASCIIString()

        assertTrue(ascii.contains("ServiceKey=dummy%2Fsegment%2Bvalue%3D", ignoreCase = false))
        assertFalse(ascii.contains("serviceKey="))
        assertFalse(ascii.contains("%252F", ignoreCase = true))
        assertFalse(ascii.contains("%253D", ignoreCase = true))
    }

    @Test
    fun `raw plus is preserved as encoded plus and not space`() {
        val uri = factory("dummy/segment+value=", false).lookupUri(
            DrugOverviewProviderQuery(itemName = "제품명"),
            1,
        )

        assertTrue(uri.rawQuery.contains("%2B", ignoreCase = true))
        assertFalse(uri.rawQuery.contains("%20"))
        assertFalse(uri.rawQuery.contains("+"))
    }

    private fun factory(key: String, encoded: Boolean) = DrugOverviewUriFactory(
        DrugOverviewApiProperties(),
        ServiceKeyEncoder(PublicDataCredentialsProperties(key, encoded)),
    )
}
