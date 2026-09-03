package com.glimmer.app.data

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.ActivityNotFoundException
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.glimmer.app.MainActivity
import com.glimmer.app.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime

object NotificationScheduler {

    // Two channels so the in-app "Notification Sounds" toggle (NotificationsSettingsScreen) has a
    // real effect — a channel's sound is fixed at creation and can't be changed afterwards, so a
    // single mutable channel can't represent both states.
    const val CHANNEL_ID_SOUND = "glimmer_birthday_channel"
    const val CHANNEL_ID_SILENT = "glimmer_birthday_channel_silent"
    const val EXTRA_BIRTHDAY_NAME = "birthday_name"
    const val EXTRA_BIRTHDAY_ID = "birthday_id"
    const val EXTRA_DAYS_BEFORE = "days_before"

    fun createNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

            val soundChannel = NotificationChannel(
                CHANNEL_ID_SOUND,
                "Birthday Reminders",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Notifications for upcoming birthdays"
                enableVibration(true)
            }
            val silentChannel = NotificationChannel(
                CHANNEL_ID_SILENT,
                "Birthday Reminders (Silent)",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Notifications for upcoming birthdays, without sound"
                enableVibration(false)
                setSound(null, null)
            }

            manager.createNotificationChannel(soundChannel)
            manager.createNotificationChannel(silentChannel)
        }
    }

    /**
     * Schedules or re-schedules a birthday reminder alarm.
     * @param daysBeforeOffset 0 = on the day, 1 = 1 day before, etc.
     * @param hour24 hour of day (0-23) the reminder should fire at — the user's configured time
     *   from Notifications settings (defaults to [SettingsRepository.DEFAULT_REMINDER_HOUR]),
     *   previously a fixed 09:00 with no way to change it.
     */
    fun scheduleReminder(
        context: Context,
        birthday: Birthday,
        daysBeforeOffset: Int = 1,
        hour24: Int = SettingsRepository.DEFAULT_REMINDER_HOUR,
        minute: Int = SettingsRepository.DEFAULT_REMINDER_MINUTE
    ) {
        if (!birthday.reminderEnabled) {
            cancelReminder(context, birthday.id)
            return
        }
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

        // birthMonthDay() reads Birthday.dateOfBirth as UTC — the convention it's stored in —
        // then the reminder fires at hour24:minute in the device's own local time. Mixing a
        // UTC-interpreted date with a default-timezone "now" (as a raw Calendar would) shifts
        // the alarm by a day for anyone west of UTC.
        val monthDay = birthday.birthMonthDay()
        val zone = ZoneId.systemDefault()

        // targetFor(year) is keyed by the BIRTHDAY's occurrence year, not the offset target's own
        // year — those differ whenever the offset crosses a year boundary (e.g. a Jan 5 birthday
        // with a 1-week-before reminder targets Dec 29 of the *previous* year).
        fun targetFor(occurrenceYear: Int): ZonedDateTime =
            monthDay.atYear(occurrenceYear)
                .minusDays(daysBeforeOffset.toLong())
                .atTime(hour24, minute)
                .atZone(zone)

        val nowZoned = ZonedDateTime.now(zone)
        var occurrenceYear = LocalDate.now(zone).year
        var target = targetFor(occurrenceYear)
        // If this year's occurrence has already passed, schedule next year's instead.
        if (target.isBefore(nowZoned)) {
            occurrenceYear += 1
            target = targetFor(occurrenceYear)
        }

        val pendingIntent = PendingIntent.getBroadcast(
            context,
            birthday.id,
            alarmIntent(context, birthday, daysBeforeOffset),
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
            // Thrown if canScheduleExactAlarms() was true when checked above but the permission
            // was revoked in the tiny window before this call (or on OEM builds that lie about
            // it). Previously swallowed with no trace at all — logged now so a "why is this
            // reminder late" report is diagnosable — then falls back to an inexact alarm rather
            // than not scheduling anything.
            GLog.w("Scheduler", "setExactAndAllowWhileIdle denied for birthdayId=${birthday.id}; falling back to an inexact alarm", e)
            alarmManager.set(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent)
        }
    }

    fun cancelReminder(context: Context, birthdayId: Int) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        // No extras on this Intent — deliberately. PendingIntent.getBroadcast equality (and thus
        // whether this cancels the alarm scheduleReminder registered) is based on the Intent's
        // component/action/data/categories plus the request code, NOT its extras, so a bare
        // Intent with a matching request code (birthdayId) still matches the one that was armed.
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            birthdayId,
            alarmIntent(context),
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
        )
        pendingIntent?.let { alarmManager.cancel(it) }
    }

    /**
     * Builds the [BirthdayAlarmReceiver] Intent shared by [scheduleReminder] and [cancelReminder].
     * `birthday`/`daysBeforeOffset` are omitted when only cancelling — see the comment above.
     */
    private fun alarmIntent(context: Context, birthday: Birthday? = null, daysBeforeOffset: Int = 0): Intent =
        Intent(context, BirthdayAlarmReceiver::class.java).apply {
            if (birthday != null) {
                putExtra(EXTRA_BIRTHDAY_NAME, birthday.name)
                putExtra(EXTRA_BIRTHDAY_ID, birthday.id)
                // So the notification's copy can say "today" / "tomorrow" / "in N days"
                // correctly — without this the receiver can't tell which offset fired.
                putExtra(EXTRA_DAYS_BEFORE, daysBeforeOffset)
            }
        }

    fun reminderTimeToOffset(reminderTime: String): Int = when (reminderTime) {
        "On the day" -> 0
        "1 day before" -> 1
        "3 days before" -> 3
        "1 week before" -> 7
        else -> 1
    }

    /**
     * Whether exact alarms are currently available. USE_EXACT_ALARM is deliberately not held
     * (see the manifest) since Play restricts it to apps whose core purpose is alarms/timers —
     * so on API 31+ this can be false until the user grants it via [requestExactAlarmPermission].
     * Below API 31 exact alarms need no runtime grant.
     */
    fun canScheduleExactAlarms(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return true
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        return alarmManager.canScheduleExactAlarms()
    }

    /** Opens the system screen where the user can grant SCHEDULE_EXACT_ALARM. No-op below API 31. */
    fun requestExactAlarmPermission(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return
        val intent = Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply {
            data = Uri.parse("package:${context.packageName}")
        }
        try {
            context.startActivity(intent)
        } catch (e: ActivityNotFoundException) {
            // A handful of OEM builds omit this screen; there's nothing more we can do here.
        }
    }
}

class BirthdayAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val name = intent.getStringExtra(NotificationScheduler.EXTRA_BIRTHDAY_NAME) ?: "Someone"
        val birthdayId = intent.getIntExtra(NotificationScheduler.EXTRA_BIRTHDAY_ID, 0)
        val daysBefore = intent.getIntExtra(NotificationScheduler.EXTRA_DAYS_BEFORE, 0)

        // setExactAndAllowWhileIdle is one-shot: without re-arming here, this person's reminder
        // would never fire again. goAsync() keeps the process alive long enough for the settings
        // and DB reads — onReceive would otherwise return (and the receiver could be killed)
        // before they complete.
        val pendingResult = goAsync()
        // Section 4.2: this whole block used to have no exception handling at all — a DB read
        // failing here (encryption key issue, disk full, a corrupt row) would throw out of the
        // coroutine, get silently swallowed by the default uncaught-exception behavior, and
        // leave the reminder un-re-armed with nothing in Logcat to explain why. It's now caught,
        // logged, and — this is the important part — the notification for THIS occurrence still
        // gets a best-effort attempt even if the re-arm half fails, since missing next year's
        // reminder is a much smaller problem than missing this one too.
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val settings = SettingsRepository.getInstance(context)
                try {
                    val soundEnabled = settings.soundEnabled.first()
                    val showOnLockScreen = settings.showOnLockScreen.first()
                    postNotification(context, birthdayId, name, daysBefore, soundEnabled, showOnLockScreen)
                } catch (t: Throwable) {
                    GLog.e("Alarm", "Failed to post the notification for birthdayId=$birthdayId", t)
                }

                try {
                    // Only re-arm if the global toggle is still on — otherwise this birthday's
                    // alarm stays cancelled, matching whatever the user set in Notifications
                    // settings.
                    if (settings.notificationsEnabled.first()) {
                        val birthday = AppDatabase.getDatabase(context).birthdayDao()
                            .getBirthdayById(birthdayId).first()
                        if (birthday != null && birthday.reminderEnabled) {
                            val offset = NotificationScheduler.reminderTimeToOffset(birthday.reminderTime)
                            NotificationScheduler.scheduleReminder(
                                context, birthday, offset,
                                hour24 = settings.reminderHour.first(),
                                minute = settings.reminderMinute.first()
                            )
                        }
                    }
                } catch (t: Throwable) {
                    GLog.e("Alarm", "Failed to re-arm the reminder for birthdayId=$birthdayId " +
                        "after it fired — it will not fire again until the app is opened or " +
                        "the device reboots", t)
                }
            } finally {
                pendingResult.finish()
            }
        }
    }

    private fun postNotification(
        context: Context,
        birthdayId: Int,
        name: String,
        daysBefore: Int,
        soundEnabled: Boolean,
        showOnLockScreen: Boolean
    ) {
        NotificationScheduler.createNotificationChannel(context)
        val channelId = if (soundEnabled) {
            NotificationScheduler.CHANNEL_ID_SOUND
        } else {
            NotificationScheduler.CHANNEL_ID_SILENT
        }

        val tapIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val tapPendingIntent = PendingIntent.getActivity(
            context, birthdayId, tapIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Previously every reminder said "is coming up!" regardless of which offset fired it —
        // including the "on the day" one, which should say the birthday IS today.
        val body = when (daysBefore) {
            0 -> "$name's birthday is today! 🎉"
            1 -> "$name's birthday is tomorrow."
            else -> "$name's birthday is in $daysBefore days."
        }

        // SEC-03: naming a specific person on the lock screen is exactly the kind of detail that
        // shouldn't be visible to anyone who picks up a locked phone. VISIBILITY_PRIVATE plus a
        // generic setPublicVersion is the platform-correct way to say "a notification exists"
        // without leaking its content — the redacted version is what shows on the lock screen
        // unless the user has opted into full content in Notifications settings.
        val builder = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.drawable.ic_notification)
            .setColor(ContextCompat.getColor(context, R.color.glimmer_accent))
            .setContentTitle("🎂 Birthday Reminder")
            .setContentText(body)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(tapPendingIntent)
            .setAutoCancel(true)

        if (showOnLockScreen) {
            builder.setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
        } else {
            val publicVersion = NotificationCompat.Builder(context, channelId)
                .setSmallIcon(R.drawable.ic_notification)
                .setColor(ContextCompat.getColor(context, R.color.glimmer_accent))
                .setContentTitle("🎂 Birthday Reminder")
                .setContentText("You have a birthday reminder")
                .setContentIntent(tapPendingIntent)
                .setAutoCancel(true)
                .build()
            builder.setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            builder.setPublicVersion(publicVersion)
        }

        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(birthdayId, builder.build())
    }
}
