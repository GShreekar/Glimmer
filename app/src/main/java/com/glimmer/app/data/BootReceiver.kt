package com.glimmer.app.data

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Re-schedules all birthday reminder alarms after events that clear or invalidate them:
 * - device reboot (AlarmManager alarms don't survive it)
 * - an app update (Android clears exact alarms across updates on some OEMs/versions)
 * - a timezone change (every alarm's trigger time depends on the device's local timezone —
 *   see NotificationScheduler.scheduleReminder — so a changed zone can leave existing alarms
 *   pointing at the wrong wall-clock moment until they're rebuilt)
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            Intent.ACTION_TIMEZONE_CHANGED -> Unit
            else -> return
        }

        // onReceive would otherwise return (and the receiver's process could be killed by the
        // system) before the coroutine below finishes its DB read and re-schedules anything —
        // goAsync() keeps the process alive long enough for that to complete.
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val settings = SettingsRepository.getInstance(context)
                // Respect the global toggle: if the user turned all reminders off, don't re-arm
                // any of them just because the device rebooted.
                if (!settings.notificationsEnabled.first()) return@launch

                val db = AppDatabase.getDatabase(context)
                val birthdays = db.birthdayDao().getAllBirthdays().first()
                birthdays.forEach { birthday ->
                    if (birthday.reminderEnabled) {
                        val offset = NotificationScheduler.reminderTimeToOffset(birthday.reminderTime)
                        NotificationScheduler.scheduleReminder(context, birthday, offset)
                    }
                }
            } catch (t: Throwable) {
                // Reminders silently going stale is worse than a logged failure — at least this
                // is visible in logcat / a bug report rather than vanishing without a trace.
                Log.e("Glimmer/BootReceiver", "Failed to reschedule reminders for ${intent.action}", t)
            } finally {
                pendingResult.finish()
            }
        }
    }
}
