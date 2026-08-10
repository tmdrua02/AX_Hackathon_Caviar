package com.haneul.medassist.client.supplement

import com.haneul.medassist.client.common.ServiceKeyEncoder
import com.haneul.medassist.config.HealthFunctionalFoodApiProperties
import com.haneul.medassist.config.PublicDataCredentialsProperties
import org.junit.jupiter.api.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class HealthFunctionalFoodUriFactoryTest {
    @Test
    fun `list uses actual gateway parameter spelling and encodes service key once`() {
        val uri = factory().listUri(
            HealthFunctionalFoodListQuery("혼합유산균", "공식업체", "S-1"),
            1,
        )
        val raw = uri.rawQuery

        assertTrue(raw.contains("ServiceKey=dummy%2Fsegment%2Bvalue%3D"))
        assertTrue(raw.contains("Prduct="))
        assertTrue(raw.contains("Entrps="))
        assertTrue(raw.contains("Sttemnt_no=S-1"))
        assertFalse(raw.contains("Product="))
        assertFalse(raw.contains("%252F", ignoreCase = true))
    }

    @Test
    fun `detail uses uppercase statement number parameter`() {
        val raw = factory().detailUri("S-1", 1).rawQuery

        assertTrue(raw.contains("STTEMNT_NO=S-1"))
        assertFalse(raw.contains("Sttemnt_no="))
    }

    private fun factory() = HealthFunctionalFoodUriFactory(
        HealthFunctionalFoodApiProperties(),
        ServiceKeyEncoder(PublicDataCredentialsProperties("dummy%2Fsegment%2Bvalue%3D", true)),
    )
}
