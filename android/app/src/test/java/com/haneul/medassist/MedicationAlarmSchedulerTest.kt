package com.haneul.medassist

import com.haneul.medassist.data.MedicationAlarm
import com.haneul.medassist.reminder.MedicationAlarmScheduler
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.DayOfWeek
import java.time.ZoneId
import java.time.ZonedDateTime

class MedicationAlarmSchedulerTest {
    private val zone = ZoneId.of("Asia/Seoul")

    @Test
    fun `same day future alarm is selected`() {
        val now = ZonedDateTime.of(2026, 8, 10, 8, 0, 0, 0, zone)
        assertEquals(
            ZonedDateTime.of(2026, 8, 10, 9, 0, 0, 0, zone),
            MedicationAlarmScheduler.nextOccurrence(alarm(9, setOf(DayOfWeek.MONDAY)), now),
        )
    }

    @Test
    fun `past alarm advances to next selected weekday`() {
        val now = ZonedDateTime.of(2026, 8, 10, 10, 0, 0, 0, zone)
        assertEquals(
            ZonedDateTime.of(2026, 8, 12, 9, 0, 0, 0, zone),
            MedicationAlarmScheduler.nextOccurrence(
                alarm(9, setOf(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY)), now,
            ),
        )
    }

    @Test
    fun `empty day set behaves as every day`() {
        val now = ZonedDateTime.of(2026, 8, 10, 10, 0, 0, 0, zone)
        assertEquals(
            ZonedDateTime.of(2026, 8, 11, 9, 0, 0, 0, zone),
            MedicationAlarmScheduler.nextOccurrence(alarm(9, emptySet()), now),
        )
    }

    private fun alarm(hour: Int, days: Set<DayOfWeek>) = MedicationAlarm(
        id = "test",
        medicationId = "medication",
        medicationName = "해열 시럽 A",
        hour = hour,
        minute = 0,
        repeatDays = days,
        timing = "식후 30분",
    )
}
