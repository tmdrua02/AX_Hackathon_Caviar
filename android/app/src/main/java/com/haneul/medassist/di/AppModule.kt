package com.haneul.medassist.di

import android.content.Context
import androidx.room.Room
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.haneul.medassist.BuildConfig
import com.haneul.medassist.data.ApiService
import com.haneul.medassist.data.MedAssistDatabase
import com.haneul.medassist.data.SupplementApiService
import com.haneul.medassist.ocr.MlKitKoreanOcrEngine
import com.haneul.medassist.ocr.OcrEngine
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import java.util.concurrent.TimeUnit
import javax.inject.Qualifier
import javax.inject.Singleton

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class MainHttpClient

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class SupplementHttpClient

@Module
@InstallIn(SingletonComponent::class)
object AppModule {
    @Provides
    @Singleton
    fun ocrEngine(implementation: MlKitKoreanOcrEngine): OcrEngine = implementation

    @Provides
    @Singleton
    fun json(): Json = Json { ignoreUnknownKeys = true; explicitNulls = false }

    @Provides
    @Singleton
    @MainHttpClient
    fun mainHttpClient(): OkHttpClient = baseHttpClient()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(45, TimeUnit.SECONDS)
        .writeTimeout(45, TimeUnit.SECONDS)
        .addInterceptor { chain ->
            val path = chain.request().url.encodedPath
            when {
                path.endsWith("/stream") -> chain.withReadTimeout(150, TimeUnit.SECONDS).proceed(chain.request())
                path == "/api/v1/consultations" -> chain.withWriteTimeout(180, TimeUnit.SECONDS)
                    .withReadTimeout(60, TimeUnit.SECONDS).proceed(chain.request())
                else -> chain.proceed(chain.request())
            }
        }.build()

    @Provides
    @Singleton
    @SupplementHttpClient
    fun supplementHttpClient(): OkHttpClient = baseHttpClient()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    private fun baseHttpClient(): OkHttpClient.Builder = OkHttpClient.Builder()
        .addInterceptor { chain ->
            val request = chain.request().newBuilder().apply {
                if (BuildConfig.DEMO_API_TOKEN.isNotBlank()) {
                    header("X-Demo-Api-Key", BuildConfig.DEMO_API_TOKEN)
                }
            }.build()
            chain.proceed(request)
        }
        .addInterceptor(HttpLoggingInterceptor().apply {
            level = if (BuildConfig.DEBUG) HttpLoggingInterceptor.Level.BASIC else HttpLoggingInterceptor.Level.NONE
            redactHeader("Authorization")
            redactHeader("X-Demo-Api-Key")
            redactHeader("X-Demo-User-Id")
        })

    @Provides
    @Singleton
    fun api(@MainHttpClient client: OkHttpClient, json: Json): ApiService = Retrofit.Builder()
        .baseUrl(BuildConfig.API_BASE_URL)
        .client(client)
        .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
        .build().create(ApiService::class.java)

    @Provides
    @Singleton
    fun supplementApi(@SupplementHttpClient client: OkHttpClient, json: Json): SupplementApiService = Retrofit.Builder()
        .baseUrl(BuildConfig.SUPPLEMENT_API_BASE_URL)
        .client(client)
        .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
        .build().create(SupplementApiService::class.java)

    @Provides
    @Singleton
    fun database(@ApplicationContext context: Context): MedAssistDatabase =
        Room.databaseBuilder(context, MedAssistDatabase::class.java, "med-assist.db")
            .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5)
            .build()

    private val MIGRATION_1_2 = object : Migration(1, 2) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                """CREATE TABLE IF NOT EXISTS `medication_alarms` (
                    `id` TEXT NOT NULL,
                    `medicationId` TEXT NOT NULL,
                    `medicationName` TEXT NOT NULL,
                    `hour` INTEGER NOT NULL,
                    `minute` INTEGER NOT NULL,
                    `repeatDays` TEXT NOT NULL,
                    `timing` TEXT NOT NULL,
                    `soundEnabled` INTEGER NOT NULL,
                    `soundName` TEXT NOT NULL,
                    `vibrationEnabled` INTEGER NOT NULL,
                    `enabled` INTEGER NOT NULL,
                    PRIMARY KEY(`id`)
                )""".trimIndent(),
            )
        }
    }

    private val MIGRATION_2_3 = object : Migration(2, 3) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                """CREATE TABLE IF NOT EXISTS `medication_dose_records` (
                    `alarmId` TEXT NOT NULL,
                    `date` TEXT NOT NULL,
                    `completed` INTEGER NOT NULL,
                    `completedAt` INTEGER,
                    PRIMARY KEY(`alarmId`, `date`)
                )""".trimIndent(),
            )
        }
    }

    private val MIGRATION_3_4 = object : Migration(3, 4) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                """CREATE TABLE IF NOT EXISTS `manual_medications` (
                    `id` TEXT NOT NULL,
                    `name` TEXT NOT NULL,
                    `productType` TEXT NOT NULL,
                    `ingredientDescription` TEXT NOT NULL,
                    `active` INTEGER NOT NULL,
                    PRIMARY KEY(`id`)
                )""".trimIndent(),
            )
        }
    }

    private val MIGRATION_4_5 = object : Migration(4, 5) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE `manual_medications` ADD COLUMN `startDate` TEXT")
            db.execSQL("ALTER TABLE `manual_medications` ADD COLUMN `endDate` TEXT")
            db.execSQL("ALTER TABLE `manual_medications` ADD COLUMN `intakeTiming` TEXT")
            db.execSQL("ALTER TABLE `manual_medications` ADD COLUMN `timesPerDay` INTEGER")
            db.execSQL("ALTER TABLE `manual_medications` ADD COLUMN `doseValue` REAL")
            db.execSQL("ALTER TABLE `manual_medications` ADD COLUMN `doseUnit` TEXT")
        }
    }
}
