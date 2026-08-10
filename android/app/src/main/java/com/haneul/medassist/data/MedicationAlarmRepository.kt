package com.haneul.medassist.data

import android.content.Context
import com.haneul.medassist.reminder.MedicationAlarmScheduler
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.Instant
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

data class MedicationAlarm(
    val id: String = UUID.randomUUID().toString(),
    val medicationId: String,
    val medicationName: String,
    val hour: Int,
    val minute: Int,
    val repeatDays: Set<DayOfWeek>,
    val timing: String,
    val soundEnabled: Boolean = true,
    val soundName: String = "기본 알림음",
    val vibrationEnabled: Boolean = true,
    val enabled: Boolean = true,
)

data class MedicationDoseRecord(
    val alarmId: String,
    val date: LocalDate,
    val completed: Boolean,
    val completedAt: Instant?,
)

@Singleton
class MedicationAlarmRepository @Inject constructor(
    private val database: MedAssistDatabase,
    @ApplicationContext private val context: Context,
) {
    private val dao get() = database.medicationAlarmDao()

    fun observeAll(): Flow<List<MedicationAlarm>> = dao.observeAll().map { rows -> rows.map { it.toModel() } }

    fun observeDoseRecords(): Flow<List<MedicationDoseRecord>> =
        database.medicationDoseRecordDao().observeAll().map { rows -> rows.map { it.toModel() } }

    suspend fun find(id: String): MedicationAlarm? = dao.find(id)?.toModel()

    suspend fun save(alarm: MedicationAlarm) {
        dao.upsert(alarm.toEntity())
        if (alarm.enabled) MedicationAlarmScheduler.schedule(context, alarm)
        else MedicationAlarmScheduler.cancel(context, alarm.id)
    }

    suspend fun setEnabled(alarm: MedicationAlarm, enabled: Boolean) = save(alarm.copy(enabled = enabled))

    suspend fun delete(alarm: MedicationAlarm) {
        MedicationAlarmScheduler.cancel(context, alarm.id)
        dao.delete(alarm.id)
        database.medicationDoseRecordDao().deleteForAlarm(alarm.id)
    }

    suspend fun disableForMedication(medicationId: String) {
        dao.activeForMedication(medicationId).map { it.toModel() }.forEach { alarm ->
            save(alarm.copy(enabled = false))
        }
    }

    suspend fun updateMedicationName(medicationId: String, medicationName: String) {
        dao.forMedication(medicationId).map { it.toModel() }.forEach { alarm ->
            save(alarm.copy(medicationName = medicationName))
        }
    }

    suspend fun markCompleted(alarmId: String, date: LocalDate = LocalDate.now()) {
        database.medicationDoseRecordDao().upsert(
            MedicationDoseRecordEntity(
                alarmId = alarmId,
                date = date.toString(),
                completed = true,
                completedAt = System.currentTimeMillis(),
            ),
        )
    }

    suspend fun markIncomplete(alarmId: String, date: LocalDate = LocalDate.now()) {
        database.medicationDoseRecordDao().upsert(
            MedicationDoseRecordEntity(
                alarmId = alarmId,
                date = date.toString(),
                completed = false,
                completedAt = null,
            ),
        )
    }

    suspend fun rescheduleActive() {
        dao.active().map { it.toModel() }.forEach { MedicationAlarmScheduler.schedule(context, it) }
    }
}

private fun MedicationAlarm.toEntity() = MedicationAlarmEntity(
    id = id,
    medicationId = medicationId,
    medicationName = medicationName,
    hour = hour,
    minute = minute,
    repeatDays = repeatDays.sortedBy { it.value }.joinToString(",") { it.value.toString() },
    timing = timing,
    soundEnabled = soundEnabled,
    soundName = soundName,
    vibrationEnabled = vibrationEnabled,
    enabled = enabled,
)

private fun MedicationAlarmEntity.toModel() = MedicationAlarm(
    id = id,
    medicationId = medicationId,
    medicationName = medicationName,
    hour = hour,
    minute = minute,
    repeatDays = repeatDays.split(',').mapNotNull { it.toIntOrNull() }
        .filter { it in 1..7 }.mapTo(linkedSetOf()) { DayOfWeek.of(it) },
    timing = timing,
    soundEnabled = soundEnabled,
    soundName = soundName,
    vibrationEnabled = vibrationEnabled,
    enabled = enabled,
)

private fun MedicationDoseRecordEntity.toModel() = MedicationDoseRecord(
    alarmId = alarmId,
    date = LocalDate.parse(date),
    completed = completed,
    completedAt = completedAt?.let(Instant::ofEpochMilli),
)
