package com.example.data

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Re-schedules all birthday reminder alarms after the device reboots,
 * since AlarmManager alarms are cleared on reboot.
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return

        val db = AppDatabase.getDatabase(context)
        val settings = SettingsRepository.getInstance(context)
        CoroutineScope(Dispatchers.IO).launch {
            // Respect the global toggle: if the user turned all reminders off, don't re-arm any
            // of them just because the device rebooted.
            if (!settings.notificationsEnabled.first()) return@launch

            val birthdays = db.birthdayDao().getAllBirthdays().first()
            birthdays.forEach { birthday ->
                if (birthday.reminderEnabled) {
                    val offset = NotificationScheduler.reminderTimeToOffset(birthday.reminderTime)
                    NotificationScheduler.scheduleReminder(context, birthday, offset)
                }
            }
        }
    }
}
