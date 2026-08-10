package com.haneul.medassist.data

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.RoomDatabase
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "home_medications")
data class CachedMedication(
    @PrimaryKey val id: String,
    val name: String,
    val productType: String,
    val dose: String?,
    val time: String?,
    val timing: String?,
    val taken: Boolean,
    val version: Long,
)

@Dao
interface MedicationCacheDao {
    @Query("SELECT * FROM home_medications ORDER BY time")
    suspend fun all(): List<CachedMedication>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun replace(items: List<CachedMedication>)
}

@Entity(tableName = "medication_alarms")
data class MedicationAlarmEntity(
    @PrimaryKey val id: String,
    val medicationId: String,
    val medicationName: String,
    val hour: Int,
    val minute: Int,
    /** ISO-8601 day numbers (Monday=1 ... Sunday=7), stored as comma-separated values. */
    val repeatDays: String,
    val timing: String,
    val soundEnabled: Boolean,
    val soundName: String,
    val vibrationEnabled: Boolean,
    val enabled: Boolean,
)

@Dao
interface MedicationAlarmDao {
    @Query("SELECT * FROM medication_alarms ORDER BY hour, minute, medicationName")
    fun observeAll(): Flow<List<MedicationAlarmEntity>>

    @Query("SELECT * FROM medication_alarms WHERE id = :id LIMIT 1")
    suspend fun find(id: String): MedicationAlarmEntity?

    @Query("SELECT * FROM medication_alarms WHERE enabled = 1")
    suspend fun active(): List<MedicationAlarmEntity>

    @Query("SELECT * FROM medication_alarms WHERE medicationId = :medicationId AND enabled = 1")
    suspend fun activeForMedication(medicationId: String): List<MedicationAlarmEntity>

    @Query("SELECT * FROM medication_alarms WHERE medicationId = :medicationId")
    suspend fun forMedication(medicationId: String): List<MedicationAlarmEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(alarm: MedicationAlarmEntity)

    @Query("DELETE FROM medication_alarms WHERE id = :id")
    suspend fun delete(id: String)
}

@Entity(tableName = "medication_dose_records", primaryKeys = ["alarmId", "date"])
data class MedicationDoseRecordEntity(
    val alarmId: String,
    /** Local calendar date in ISO-8601 format (yyyy-MM-dd). */
    val date: String,
    val completed: Boolean,
    val completedAt: Long?,
)

@Dao
interface MedicationDoseRecordDao {
    @Query("SELECT * FROM medication_dose_records")
    fun observeAll(): Flow<List<MedicationDoseRecordEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(record: MedicationDoseRecordEntity)

    @Query("DELETE FROM medication_dose_records WHERE alarmId = :alarmId")
    suspend fun deleteForAlarm(alarmId: String)
}

@Entity(tableName = "manual_medications")
data class ManualMedicationEntity(
    @PrimaryKey val id: String,
    val name: String,
    val productType: String,
    val ingredientDescription: String,
    val active: Boolean,
    val startDate: String? = null,
    val endDate: String? = null,
    val intakeTiming: String? = null,
    val timesPerDay: Int? = null,
    val doseValue: Double? = null,
    val doseUnit: String? = null,
)

@Dao
interface ManualMedicationDao {
    @Query("SELECT * FROM manual_medications ORDER BY name")
    suspend fun all(): List<ManualMedicationEntity>

    @Query("SELECT * FROM manual_medications WHERE active = 1 ORDER BY name")
    suspend fun allActive(): List<ManualMedicationEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(medication: ManualMedicationEntity)
}

@Database(
    entities = [CachedMedication::class, MedicationAlarmEntity::class, MedicationDoseRecordEntity::class, ManualMedicationEntity::class],
    version = 5,
    exportSchema = false,
)
abstract class MedAssistDatabase : RoomDatabase() {
    abstract fun medicationDao(): MedicationCacheDao
    abstract fun medicationAlarmDao(): MedicationAlarmDao
    abstract fun medicationDoseRecordDao(): MedicationDoseRecordDao
    abstract fun manualMedicationDao(): ManualMedicationDao
}
