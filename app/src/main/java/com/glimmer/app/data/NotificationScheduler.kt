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
import java.util.concurrent.TimeUnit

object NotificationScheduler {

    // Two channels so the in-app "Notification Sounds" toggle (NotificationsSettingsScreen) has a
    // real effect — a channel's sound is fixed at creation and can't be changed afterwards, so a
    // single mutable channel can't represent both states.
    const val CHANNEL_ID_SOUND = "glimmer_birthday_channel"
    const val CHANNEL_ID_SILENT = "glimmer_birthday_channel_silent"
    const val EXTRA_BIRTHDAY_NAME = "birthday_name"
    const val EXTRA_BIRTHDAY_ID = "birthday_id"
    const val EXTRA_DAYS_BEFORE = "days_before"
    const val ACTION_SNOOZE = "com.glimmer.app.ACTION_SNOOZE"

    // Not private: BirthdayAlarmReceiver (a separate top-level class in this file) needs these too.
    const val SNOOZE_HOURS = 3L
    // Large, disjoint bases so a message/call/snooze PendingIntent for one person can never
    // collide with a scheduled-reminder PendingIntent for another (see requestCode below) — the
    // request code alone doesn't strictly need to be globally unique (differing Intent data/action
    // already disambiguates them), but keeping the ranges disjoint makes that obviously true
    // rather than relying on it.
    const val REQUEST_CODE_MESSAGE_BASE = 2_000_000
    const val REQUEST_CODE_CALL_BASE = 3_000_000
    const val REQUEST_CODE_SNOOZE_BASE = 4_000_000

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

    /** FEAT-04: a stable, collision-free request code derived from (person, offset) alone. */
    private fun requestCode(birthdayId: Int, offset: ReminderOffset): Int = birthdayId * 10 + offset.ordinal

    /**
     * Schedules or re-schedules one person's reminder for one offset.
     * @param hour24 hour of day (0-23) the reminder should fire at — the user's configured time
     *   from Notifications settings (defaults to [SettingsRepository.DEFAULT_REMINDER_HOUR]).
     */
    fun scheduleReminder(
        context: Context,
        birthday: Birthday,
        offset: ReminderOffset,
        hour24: Int = SettingsRepository.DEFAULT_REMINDER_HOUR,
        minute: Int = SettingsRepository.DEFAULT_REMINDER_MINUTE
    ) {
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
                .minusDays(offset.daysBefore.toLong())
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

        val requestCode = requestCode(birthday.id, offset)
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            requestCode,
            alarmIntent(context, birthday, offset),
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
            // it). Logged so a "why is this reminder late" report is diagnosable, then falls back
            // to an inexact alarm rather than not scheduling anything.
            GLog.w("Scheduler", "setExactAndAllowWhileIdle denied for birthdayId=${birthday.id}; falling back to an inexact alarm", e)
            alarmManager.set(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent)
        }
    }

    fun cancelReminder(context: Context, birthdayId: Int, offset: ReminderOffset) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        // No extras on this Intent — deliberately. PendingIntent.getBroadcast equality (and thus
        // whether this cancels the alarm scheduleReminder registered) is based on the Intent's
        // component/action/data/categories plus the request code, NOT its extras, so a bare
        // Intent with a matching request code still matches the one that was armed.
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            requestCode(birthdayId, offset),
            alarmIntent(context, birthday = null, offset = null),
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
        )
        pendingIntent?.let { alarmManager.cancel(it) }
    }

    /** Cancels every possible offset's alarm for this person — used when a person is deleted. */
    fun cancelAllReminders(context: Context, birthdayId: Int) {
        ReminderOffset.entries.forEach { cancelReminder(context, birthdayId, it) }
    }

    /**
     * FEAT-04: reconciles AlarmManager with the current `reminders` table for this person —
     * schedules an alarm for every currently-selected offset, cancels every other possible
     * offset's alarm. This is idempotent and is how an edit (adding/removing offsets, or turning
     * the master switch off) takes effect, and it's also safe to call on every alarm fire (a
     * reminder that hasn't occurred yet this cycle is simply re-registered at the same target
     * time — a no-op in practice).
     */
    fun rescheduleAll(context: Context, birthday: Birthday, reminders: List<Reminder>, hour24: Int, minute: Int) {
        val selected = reminders.mapNotNull { ReminderOffset.fromDaysBefore(it.daysBefore) }.toSet()
        ReminderOffset.entries.forEach { offset ->
            if (birthday.reminderEnabled && offset in selected) {
                scheduleReminder(context, birthday, offset, hour24, minute)
            } else {
                cancelReminder(context, birthday.id, offset)
            }
        }
    }

    /**
     * Builds the [BirthdayAlarmReceiver] Intent shared by [scheduleReminder] and [cancelReminder].
     * `birthday`/`offset` are omitted when only cancelling — see the comment there.
     */
    private fun alarmIntent(context: Context, birthday: Birthday?, offset: ReminderOffset?): Intent =
        Intent(context, BirthdayAlarmReceiver::class.java).apply {
            if (birthday != null && offset != null) {
                putExtra(EXTRA_BIRTHDAY_NAME, birthday.name)
                putExtra(EXTRA_BIRTHDAY_ID, birthday.id)
                // So the notification's copy can say "today" / "tomorrow" / "in N days"
                // correctly — without this the receiver can't tell which offset fired.
                putExtra(EXTRA_DAYS_BEFORE, offset.daysBefore)
            }
        }

    /**
     * Maps the "Default Reminder" setting's display string (SettingsRepository.defaultReminderTime)
     * to a days-before offset — used to seed a new person's reminder chips. Unrelated to (and not
     * to be confused with) the deprecated Birthday.reminderTime column, which this no longer reads.
     */
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
        val birthdayId = intent.getIntExtra(NotificationScheduler.EXTRA_BIRTHDAY_ID, 0)
        val name = intent.getStringExtra(NotificationScheduler.EXTRA_BIRTHDAY_NAME) ?: "Someone"
        val daysBefore = intent.getIntExtra(NotificationScheduler.EXTRA_DAYS_BEFORE, 0)

        if (intent.action == NotificationScheduler.ACTION_SNOOZE) {
            handleSnooze(context, birthdayId, name, daysBefore)
            return
        }

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
                val db = AppDatabase.getDatabase(context)
                // FEAT-03: the birthday itself is fetched up front (not just at re-arm time, as
                // before) so the notification can carry working Message/Call actions.
                val birthday = db.birthdayDao().getBirthdayById(birthdayId).first()

                try {
                    val soundEnabled = settings.soundEnabled.first()
                    val showOnLockScreen = settings.showOnLockScreen.first()
                    postNotification(context, birthday, birthdayId, name, daysBefore, soundEnabled, showOnLockScreen)
                } catch (t: Throwable) {
                    GLog.e("Alarm", "Failed to post the notification for birthdayId=$birthdayId", t)
                }

                try {
                    // Only re-arm if the global toggle is still on — otherwise this birthday's
                    // alarm stays cancelled, matching whatever the user set in Notifications
                    // settings.
                    if (settings.notificationsEnabled.first() && birthday != null && birthday.reminderEnabled) {
                        val reminders = db.reminderDao().getRemindersForBirthdayOnce(birthdayId)
                        NotificationScheduler.rescheduleAll(
                            context, birthday, reminders,
                            hour24 = settings.reminderHour.first(),
                            minute = settings.reminderMinute.first()
                        )
                    }
                } catch (t: Throwable) {
                    GLog.e("Alarm", "Failed to re-arm reminders for birthdayId=$birthdayId " +
                        "after one fired — it will not fire again until the app is opened or " +
                        "the device reboots", t)
                }
            } finally {
                pendingResult.finish()
            }
        }
    }

    /** FEAT-03: "Remind me later" — dismiss now, re-post the same notification in a few hours. */
    private fun handleSnooze(context: Context, birthdayId: Int, name: String, daysBefore: Int) {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.cancel(birthdayId)

        val snoozeIntent = Intent(context, BirthdayAlarmReceiver::class.java).apply {
            putExtra(NotificationScheduler.EXTRA_BIRTHDAY_ID, birthdayId)
            putExtra(NotificationScheduler.EXTRA_BIRTHDAY_NAME, name)
            putExtra(NotificationScheduler.EXTRA_DAYS_BEFORE, daysBefore)
            // No action set: when this fires, onReceive falls through to the normal path above,
            // which re-posts the notification and (harmlessly) re-arms next year's occurrence.
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            NotificationScheduler.REQUEST_CODE_SNOOZE_BASE + birthdayId,
            snoozeIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val triggerAt = System.currentTimeMillis() + TimeUnit.HOURS.toMillis(NotificationScheduler.SNOOZE_HOURS)
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        try {
            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pendingIntent)
        } catch (e: SecurityException) {
            alarmManager.set(AlarmManager.RTC_WAKEUP, triggerAt, pendingIntent)
        }
    }

    private fun postNotification(
        context: Context,
        birthday: Birthday?,
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

        // FEAT-03: deep-links straight to this person's Detail screen (glimmer://birthday/<id>,
        // matched by DetailRoute's navDeepLink in GlimmerApp.kt) instead of just opening Home —
        // "tap to send a wish" used to mean tapping, then finding the person again by hand.
        val deepLinkUri = Uri.parse("glimmer://birthday/$birthdayId")
        val tapIntent = Intent(Intent.ACTION_VIEW, deepLinkUri, context, MainActivity::class.java).apply {
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

        // FEAT-03: actionable notifications — "Message"/"Call" only when there's a number to
        // reach (otherwise they'd open an empty composer/dialer, same as BUG-10), "Remind me
        // later" always available since it needs no data about the person at all.
        val phone = birthday?.phoneNumber
        if (!phone.isNullOrBlank()) {
            val smsIntent = Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:$phone")).apply {
                putExtra("sms_body", "Happy Birthday ${birthday.name}! 🎂🎉")
            }
            val smsPending = PendingIntent.getActivity(
                context, NotificationScheduler.REQUEST_CODE_MESSAGE_BASE + birthdayId, smsIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            builder.addAction(R.drawable.ic_notification, context.getString(R.string.detail_action_message), smsPending)

            val dialIntent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:$phone"))
            val dialPending = PendingIntent.getActivity(
                context, NotificationScheduler.REQUEST_CODE_CALL_BASE + birthdayId, dialIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            builder.addAction(R.drawable.ic_notification, context.getString(R.string.detail_action_call), dialPending)
        }

        val snoozeIntent = Intent(context, BirthdayAlarmReceiver::class.java).apply {
            action = NotificationScheduler.ACTION_SNOOZE
            putExtra(NotificationScheduler.EXTRA_BIRTHDAY_ID, birthdayId)
            putExtra(NotificationScheduler.EXTRA_BIRTHDAY_NAME, name)
            putExtra(NotificationScheduler.EXTRA_DAYS_BEFORE, daysBefore)
        }
        val snoozePending = PendingIntent.getBroadcast(
            context, NotificationScheduler.REQUEST_CODE_SNOOZE_BASE + birthdayId, snoozeIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        builder.addAction(R.drawable.ic_notification, context.getString(R.string.notif_action_snooze), snoozePending)

        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(birthdayId, builder.build())
    }
}
