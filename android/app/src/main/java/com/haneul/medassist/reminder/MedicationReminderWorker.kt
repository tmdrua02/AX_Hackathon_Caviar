package com.haneul.medassist.reminder

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.Worker
import androidx.work.WorkerParameters
import java.time.Duration
import java.time.ZonedDateTime
import java.util.concurrent.TimeUnit

class MedicationReminderWorker(context: Context, params: WorkerParameters) : Worker(context, params) {
    override fun doWork(): Result {
        val medicationId = inputData.getString("medicationId") ?: return Result.failure()
        val name = inputData.getString("name") ?: "복용약"
        val dose = inputData.getString("dose") ?: "정해진 용량"
        val manager = applicationContext.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(NotificationChannel(CHANNEL_ID, "복약 알림", NotificationManager.IMPORTANCE_DEFAULT))
        if (ContextCompat.checkSelfPermission(applicationContext, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) {
            NotificationManagerCompat.from(applicationContext).notify(
                medicationId.hashCode(),
                NotificationCompat.Builder(applicationContext, CHANNEL_ID)
                    .setSmallIcon(android.R.drawable.ic_dialog_info)
                    .setContentTitle("$name 복용 시간입니다")
                    .setContentText("$dose · 처방 지시를 확인하세요")
                    .setAutoCancel(true)
                    .build(),
            )
        }
        return Result.success()
    }

    companion object { const val CHANNEL_ID = "medication-reminders" }
}

object ReminderScheduler {
    fun schedule(context: Context, medicationId: String, name: String, dose: String, target: ZonedDateTime) {
        val delay = Duration.between(ZonedDateTime.now(), target).toMillis().coerceAtLeast(0)
        val data = Data.Builder().putString("medicationId", medicationId).putString("name", name).putString("dose", dose).build()
        val request = OneTimeWorkRequestBuilder<MedicationReminderWorker>()
            .setInitialDelay(delay, TimeUnit.MILLISECONDS).setInputData(data).addTag("medication-reminders").build()
        WorkManager.getInstance(context).enqueueUniqueWork("dose-$medicationId-${target.toEpochSecond()}", ExistingWorkPolicy.REPLACE, request)
    }
}
