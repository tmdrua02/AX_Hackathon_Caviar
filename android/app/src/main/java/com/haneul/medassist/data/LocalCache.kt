package com.haneul.medassist.data

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.RoomDatabase

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

@Database(entities = [CachedMedication::class], version = 1, exportSchema = false)
abstract class MedAssistDatabase : RoomDatabase() {
    abstract fun medicationDao(): MedicationCacheDao
}

