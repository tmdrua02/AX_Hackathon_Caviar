package com.haneul.medassist

import com.haneul.medassist.ui.to12Hour
import com.haneul.medassist.ui.to24Hour
import org.junit.Assert.assertEquals
import org.junit.Test

class MedicationAlarmTimeConversionTest {
    @Test
    fun `AM PM conversion preserves eleven fifty nine display values`() {
        assertEquals(11, to24Hour(11, false))
        assertEquals(23, to24Hour(11, true))
        assertEquals(11, 11.to12Hour())
        assertEquals(11, 23.to12Hour())
    }

    @Test
    fun `AM PM conversion handles twelve o'clock`() {
        assertEquals(0, to24Hour(12, false))
        assertEquals(12, to24Hour(12, true))
        assertEquals(12, 0.to12Hour())
        assertEquals(12, 12.to12Hour())
    }

    @Test
    fun `AM PM conversion preserves one o'clock`() {
        assertEquals(1, to24Hour(1, false))
        assertEquals(13, to24Hour(1, true))
        assertEquals(1, 1.to12Hour())
        assertEquals(1, 13.to12Hour())
    }
}
