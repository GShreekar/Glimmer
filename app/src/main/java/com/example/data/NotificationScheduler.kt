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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime

object NotificationScheduler {

    const val CHANNEL_ID = "glimmer_birthday_channel"
    const val EXTRA_BIRTHDAY_NAME = "birthday_name"
    const val EXTRA_BIRTHDAY_ID = "birthday_id"
    private const val REMINDER_HOUR_OF_DAY = 9

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

        // birthMonthDay() reads Birthday.dateOfBirth as UTC — the convention it's stored in —
        // then the reminder fires at REMINDER_HOUR_OF_DAY in the device's own local time. Mixing
        // a UTC-interpreted date with a default-timezone "now" (as a raw Calendar would) shifts
        // the alarm by a day for anyone west of UTC.
        val monthDay = birthday.birthMonthDay()
        val zone = ZoneId.systemDefault()

        // targetFor(year) is keyed by the BIRTHDAY's occurrence year, not the offset target's own
        // year — those differ whenever the offset crosses a year boundary (e.g. a Jan 5 birthday
        // with a 1-week-before reminder targets Dec 29 of the *previous* year).
        fun targetFor(occurrenceYear: Int): ZonedDateTime =
            monthDay.atYear(occurrenceYear)
                .minusDays(daysBeforeOffset.toLong())
                .atTime(REMINDER_HOUR_OF_DAY, 0)
                .atZone(zone)

        val nowZoned = ZonedDateTime.now(zone)
        var occurrenceYear = LocalDate.now(zone).year
        var target = targetFor(occurrenceYear)
        // If this year's occurrence has already passed, schedule next year's instead.
        if (target.isBefore(nowZoned)) {
            occurrenceYear += 1
            target = targetFor(occurrenceYear)
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

        val triggerAtMillis = target.toInstant().toEpochMilli()

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (alarmManager.canScheduleExactAlarms()) {
                    alarmManager.setExactAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP,
                        triggerAtMillis,
                        pendingIntent
                    )
                } else {
                    alarmManager.set(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent)
                }
            } else {
                alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    triggerAtMillis,
                    pendingIntent
                )
            }
        } catch (e: SecurityException) {
            // Fall back to inexact alarm
            alarmManager.set(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent)
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

        postNotification(context, birthdayId, name)

        // setExactAndAllowWhileIdle is one-shot: without re-arming here, this person's reminder
        // would never fire again. goAsync() keeps the process alive long enough for the DB read —
        // onReceive would otherwise return (and the receiver could be killed) before it completes.
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val birthday = AppDatabase.getDatabase(context).birthdayDao()
                    .getBirthdayById(birthdayId).first()
                if (birthday != null && birthday.reminderEnabled) {
                    val offset = NotificationScheduler.reminderTimeToOffset(birthday.reminderTime)
                    NotificationScheduler.scheduleReminder(context, birthday, offset)
                }
            } finally {
                pendingResult.finish()
            }
        }
    }

    private fun postNotification(context: Context, birthdayId: Int, name: String) {
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
