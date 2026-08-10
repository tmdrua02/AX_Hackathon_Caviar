package com.haneul.medassist

import com.haneul.medassist.data.MedicationAlarm
import com.haneul.medassist.ui.alarmsForDate
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate

class HomeMedicationAlarmTest {
    private val monday = LocalDate.of(2026, 8, 10)

    @Test
    fun `only enabled alarms scheduled for today are shown`() {
        val alarms = listOf(
            alarm("monday", 13, true, setOf(DayOfWeek.MONDAY)),
            alarm("tuesday", 9, true, setOf(DayOfWeek.TUESDAY)),
            alarm("disabled", 8, false, setOf(DayOfWeek.MONDAY)),
        )

        assertEquals(listOf("monday"), alarmsForDate(alarms, monday).map { it.id })
    }

    @Test
    fun `today alarms are ordered by time`() {
        val alarms = listOf(
            alarm("evening", 20, true, setOf(DayOfWeek.MONDAY)),
            alarm("morning", 9, true, setOf(DayOfWeek.MONDAY)),
            alarm("afternoon", 13, true, setOf(DayOfWeek.MONDAY)),
        )

        assertEquals(
            listOf("morning", "afternoon", "evening"),
            alarmsForDate(alarms, monday).map { it.id },
        )
    }

    private fun alarm(id: String, hour: Int, enabled: Boolean, days: Set<DayOfWeek>) = MedicationAlarm(
        id = id,
        medicationId = "med-$id",
        medicationName = id,
        hour = hour,
        minute = 0,
        repeatDays = days,
        timing = "식후 30분",
        enabled = enabled,
    )
}
