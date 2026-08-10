package com.haneul.medassist.reminder

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.haneul.medassist.MainActivity
import com.haneul.medassist.data.MedicationAlarm
import com.haneul.medassist.data.MedicationAlarmRepository
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.ZonedDateTime
import javax.inject.Inject

object MedicationAlarmScheduler {
    fun schedule(context: Context, alarm: MedicationAlarm) {
        cancel(context, alarm.id)
        if (!alarm.enabled) return

        val alarmManager = context.getSystemService(AlarmManager::class.java)
        val triggerAtMillis = nextOccurrence(alarm).toInstant().toEpochMilli()
        val pendingIntent = pendingIntent(context, alarm.id)
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarmManager.canScheduleExactAlarms()) {
            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent)
        } else {
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent)
        }
    }

    fun cancel(context: Context, alarmId: String) {
        context.getSystemService(AlarmManager::class.java).cancel(pendingIntent(context, alarmId))
    }

    private fun pendingIntent(context: Context, alarmId: String): PendingIntent {
        val intent = Intent(context, MedicationAlarmReceiver::class.java)
            .setAction(ACTION_FIRE)
            .putExtra(EXTRA_ALARM_ID, alarmId)
        return PendingIntent.getBroadcast(
            context,
            alarmId.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    internal fun nextOccurrence(alarm: MedicationAlarm, now: ZonedDateTime = ZonedDateTime.now()): ZonedDateTime {
        val days = alarm.repeatDays.ifEmpty { DayOfWeek.entries.toSet() }
        return (0..7).asSequence()
            .map { now.toLocalDate().plusDays(it.toLong()) }
            .filter { it.dayOfWeek in days }
            .map { it.atTime(alarm.hour, alarm.minute).atZone(now.zone) }
            .first { it.isAfter(now) }
    }

    const val ACTION_FIRE = "com.haneul.medassist.action.MEDICATION_ALARM"
    const val EXTRA_ALARM_ID = "alarmId"
}

@AndroidEntryPoint
class MedicationAlarmReceiver : BroadcastReceiver() {
    @Inject lateinit var repository: MedicationAlarmRepository

    override fun onReceive(context: Context, intent: Intent) {
        val alarmId = intent.getStringExtra(MedicationAlarmScheduler.EXTRA_ALARM_ID) ?: return
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val alarm = repository.find(alarmId) ?: return@launch
                if (!alarm.enabled) return@launch
                showNotification(context, alarm)
                MedicationAlarmScheduler.schedule(context, alarm)
            } finally {
                pendingResult.finish()
            }
        }
    }

    private fun showNotification(context: Context, alarm: MedicationAlarm) {
        if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(
                context, Manifest.permission.POST_NOTIFICATIONS,
            ) != PackageManager.PERMISSION_GRANTED
        ) return

        val channelId = "medication-${if (alarm.soundEnabled) alarm.soundName.hashCode() else "silent"}-${if (alarm.vibrationEnabled) "vibrate" else "steady"}"
        val soundType = if (alarm.soundName == "기본 알림음") RingtoneManager.TYPE_NOTIFICATION else RingtoneManager.TYPE_ALARM
        val soundUri = if (alarm.soundEnabled) RingtoneManager.getDefaultUri(soundType) else null
        val manager = context.getSystemService(NotificationManager::class.java)
        val channel = NotificationChannel(channelId, "복용 알람", NotificationManager.IMPORTANCE_HIGH).apply {
            description = "등록한 시간에 복용할 약을 알려드립니다."
            enableVibration(alarm.vibrationEnabled)
            vibrationPattern = if (alarm.vibrationEnabled) longArrayOf(0, 350, 180, 350) else longArrayOf(0)
            setSound(
                soundUri,
                AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM).build(),
            )
        }
        manager.createNotificationChannel(channel)

        val openApp = PendingIntent.getActivity(
            context,
            alarm.id.hashCode(),
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val displayTime = "%02d:%02d".format(alarm.hour, alarm.minute)
        val notification = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle("${alarm.medicationName} 복용 시간이에요")
            .setContentText("$displayTime · ${alarm.timing}")
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(openApp)
            .setAutoCancel(true)
            .setSound(soundUri)
            .setVibrate(if (alarm.vibrationEnabled) longArrayOf(0, 350, 180, 350) else longArrayOf(0))
            .build()
        NotificationManagerCompat.from(context).notify(alarm.id.hashCode(), notification)
    }
}

@AndroidEntryPoint
class MedicationAlarmRestoreReceiver : BroadcastReceiver() {
    @Inject lateinit var repository: MedicationAlarmRepository

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action !in setOf(Intent.ACTION_BOOT_COMPLETED, Intent.ACTION_MY_PACKAGE_REPLACED, AlarmManager.ACTION_SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED)) return
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                repository.rescheduleActive()
            } finally {
                pendingResult.finish()
            }
        }
    }
}
