package com.example.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.settingsDataStore by preferencesDataStore(name = "glimmer_settings")

/**
 * Global app preferences, persisted with DataStore rather than the SharedPreferences that used
 * to back NotificationsSettingsScreen and ProfileSettingsScreen independently:
 * - the notification prefs were written but never read by anything (every switch was decorative)
 * - the profile prefs were read once via `derivedStateOf { prefs.getString(...) }` in
 *   SettingsScreen, which only re-evaluates when a *Compose State* it reads changes —
 *   SharedPreferences isn't one, so editing your name in ProfileSettingsScreen never refreshed
 *   the copy shown on the Settings screen until the process restarted.
 *
 * A single, app-scoped instance is shared between the ViewModel and the alarm receivers (which
 * have no ViewModel of their own) via [getInstance], mirroring [AppDatabase]'s singleton pattern.
 */
class SettingsRepository private constructor(private val context: Context) {

    private object Keys {
        val NOTIFICATIONS_ENABLED = booleanPreferencesKey("notifications_enabled")
        val SOUND_ENABLED = booleanPreferencesKey("sound_enabled")
        val DEFAULT_REMINDER_TIME = stringPreferencesKey("default_reminder_time")
        val PROFILE_NAME = stringPreferencesKey("profile_name")
        val PROFILE_EMAIL = stringPreferencesKey("profile_email")
    }

    val notificationsEnabled: Flow<Boolean> =
        context.settingsDataStore.data.map { it[Keys.NOTIFICATIONS_ENABLED] ?: true }

    val soundEnabled: Flow<Boolean> =
        context.settingsDataStore.data.map { it[Keys.SOUND_ENABLED] ?: true }

    val defaultReminderTime: Flow<String> =
        context.settingsDataStore.data.map { it[Keys.DEFAULT_REMINDER_TIME] ?: "1 day before" }

    // No fake placeholder identity ("Alex Mercer" / alex.mercer@example.com, as the old
    // SettingsScreen default was) — an empty value means "not set yet", shown as such by callers.
    val profileName: Flow<String> =
        context.settingsDataStore.data.map { it[Keys.PROFILE_NAME] ?: "" }

    val profileEmail: Flow<String> =
        context.settingsDataStore.data.map { it[Keys.PROFILE_EMAIL] ?: "" }

    suspend fun setNotificationsEnabled(enabled: Boolean) {
        context.settingsDataStore.edit { it[Keys.NOTIFICATIONS_ENABLED] = enabled }
    }

    suspend fun setSoundEnabled(enabled: Boolean) {
        context.settingsDataStore.edit { it[Keys.SOUND_ENABLED] = enabled }
    }

    suspend fun setDefaultReminderTime(value: String) {
        context.settingsDataStore.edit { it[Keys.DEFAULT_REMINDER_TIME] = value }
    }

    suspend fun setProfileName(value: String) {
        context.settingsDataStore.edit { it[Keys.PROFILE_NAME] = value }
    }

    suspend fun setProfileEmail(value: String) {
        context.settingsDataStore.edit { it[Keys.PROFILE_EMAIL] = value }
    }

    companion object {
        @Volatile private var INSTANCE: SettingsRepository? = null

        fun getInstance(context: Context): SettingsRepository =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: SettingsRepository(context.applicationContext).also { INSTANCE = it }
            }
    }
}
