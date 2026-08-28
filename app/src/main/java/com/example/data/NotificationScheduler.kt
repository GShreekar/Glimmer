package com.example.data

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.example.MainActivity
import java.util.Calendar

object NotificationScheduler {

    const val CHANNEL_ID = "glimmer_birthday_channel"
    const val EXTRA_BIRTHDAY_NAME = "birthday_name"
    const val EXTRA_BIRTHDAY_ID = "birthday_id"

    fun createNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Birthday Reminders",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Notifications for upcoming birthdays"
                enableVibration(true)
            }
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    /**
     * Schedules or re-schedules a birthday reminder alarm.
     * @param daysBeforeOffset 0 = on the day, 1 = 1 day before, etc.
     */
    fun scheduleReminder(context: Context, birthday: Birthday, daysBeforeOffset: Int = 1) {
        if (!birthday.reminderEnabled) {
            cancelReminder(context, birthday.id)
            return
        }
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

        // Build the next occurrence of the birthday
        val now = Calendar.getInstance()
        val bCal = Calendar.getInstance().apply {
            timeInMillis = birthday.dateOfBirth
        }

        val target = Calendar.getInstance().apply {
            set(Calendar.MONTH, bCal.get(Calendar.MONTH))
            set(Calendar.DAY_OF_MONTH, bCal.get(Calendar.DAY_OF_MONTH))
            set(Calendar.HOUR_OF_DAY, 9)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            add(Calendar.DAY_OF_MONTH, -daysBeforeOffset)
        }

        // If the target time has already passed this year, schedule for next year
        if (target.before(now)) {
            target.add(Calendar.YEAR, 1)
        }

        val intent = Intent(context, BirthdayAlarmReceiver::class.java).apply {
            putExtra(EXTRA_BIRTHDAY_NAME, birthday.name)
            putExtra(EXTRA_BIRTHDAY_ID, birthday.id)
        }

        val pendingIntent = PendingIntent.getBroadcast(
            context,
            birthday.id,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (alarmManager.canScheduleExactAlarms()) {
                    alarmManager.setExactAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP,
                        target.timeInMillis,
                        pendingIntent
                    )
                } else {
                    alarmManager.set(AlarmManager.RTC_WAKEUP, target.timeInMillis, pendingIntent)
                }
            } else {
                alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    target.timeInMillis,
                    pendingIntent
                )
            }
        } catch (e: SecurityException) {
            // Fall back to inexact alarm
            alarmManager.set(AlarmManager.RTC_WAKEUP, target.timeInMillis, pendingIntent)
        }
    }

    fun cancelReminder(context: Context, birthdayId: Int) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(context, BirthdayAlarmReceiver::class.java)
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            birthdayId,
            intent,
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
        )
        pendingIntent?.let { alarmManager.cancel(it) }
    }

    fun reminderTimeToOffset(reminderTime: String): Int = when (reminderTime) {
        "On the day" -> 0
        "1 day before" -> 1
        "3 days before" -> 3
        "1 week before" -> 7
        else -> 1
    }
}

class BirthdayAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val name = intent.getStringExtra(NotificationScheduler.EXTRA_BIRTHDAY_NAME) ?: "Someone"
        val birthdayId = intent.getIntExtra(NotificationScheduler.EXTRA_BIRTHDAY_ID, 0)

        NotificationScheduler.createNotificationChannel(context)

        val tapIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val tapPendingIntent = PendingIntent.getActivity(
            context, birthdayId, tapIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, NotificationScheduler.CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("🎂 Birthday Reminder")
            .setContentText("$name's birthday is coming up!")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(tapPendingIntent)
            .setAutoCancel(true)
            .build()

        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(birthdayId, notification)
    }
}
