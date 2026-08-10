package com.haneul.medassist

import com.haneul.medassist.data.Medication
import com.haneul.medassist.data.ProductType
import com.haneul.medassist.ui.medicationCounts
import org.junit.Assert.assertEquals
import org.junit.Test

class HomeMedicationCountTest {
    @Test
    fun `counts active medications independent of alarm count`() {
        val medications = listOf(
            Medication("rx", "처방약", ProductType.PRESCRIPTION_DRUG),
            Medication("otc", "일반약", ProductType.OTC_DRUG),
            Medication("supplement", "건강기능식품", ProductType.HEALTH_SUPPLEMENT),
            Medication("inactive", "중단한 약", ProductType.PRESCRIPTION_DRUG, active = false),
        )

        val counts = medicationCounts(medications)

        assertEquals(3, counts.total)
        assertEquals(1, counts.prescriptions)
        assertEquals(1, counts.otc)
        assertEquals(1, counts.supplements)
    }
}
