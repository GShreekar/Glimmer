package com.glimmer.app.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
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
        val REMINDER_HOUR = intPreferencesKey("reminder_hour")
        val REMINDER_MINUTE = intPreferencesKey("reminder_minute")
        val PROFILE_NAME = stringPreferencesKey("profile_name")
        val PROFILE_EMAIL = stringPreferencesKey("profile_email")
        val PROFILE_BIRTHDAY = longPreferencesKey("profile_birthday")
        val SHOW_ON_LOCK_SCREEN = booleanPreferencesKey("show_on_lock_screen")
        val WISH_TEMPLATES_JSON = stringPreferencesKey("wish_templates_json")
        val HAS_COMPLETED_ONBOARDING = booleanPreferencesKey("has_completed_onboarding")
    }

    val notificationsEnabled: Flow<Boolean> =
        context.settingsDataStore.data.map { it[Keys.NOTIFICATIONS_ENABLED] ?: true }

    val soundEnabled: Flow<Boolean> =
        context.settingsDataStore.data.map { it[Keys.SOUND_ENABLED] ?: true }

    val defaultReminderTime: Flow<String> =
        context.settingsDataStore.data.map { it[Keys.DEFAULT_REMINDER_TIME] ?: "1 day before" }

    // Was hardcoded as NotificationScheduler.REMINDER_HOUR_OF_DAY with no way to change it —
    // every reminder fired at 09:00 regardless of the person's or the user's own schedule.
    val reminderHour: Flow<Int> =
        context.settingsDataStore.data.map { it[Keys.REMINDER_HOUR] ?: DEFAULT_REMINDER_HOUR }

    val reminderMinute: Flow<Int> =
        context.settingsDataStore.data.map { it[Keys.REMINDER_MINUTE] ?: DEFAULT_REMINDER_MINUTE }

    // No fake placeholder identity ("Alex Mercer" / alex.mercer@example.com, as the old
    // SettingsScreen default was) — an empty value means "not set yet", shown as such by callers.
    val profileName: Flow<String> =
        context.settingsDataStore.data.map { it[Keys.PROFILE_NAME] ?: "" }

    val profileEmail: Flow<String> =
        context.settingsDataStore.data.map { it[Keys.PROFILE_EMAIL] ?: "" }

    // Profile info only, same convention as dateOfBirth elsewhere (UTC-midnight millis) — not a
    // Birthday row, so it never appears on Home/Calendar and never gets a reminder of its own.
    val profileBirthday: Flow<Long?> =
        context.settingsDataStore.data.map { it[Keys.PROFILE_BIRTHDAY] }

    // SEC-03: defaults to PRIVATE — a birthday notification naming a specific person is the kind
    // of thing that shouldn't be readable by anyone glancing at a locked phone by default. Off by
    // default, opt-in to full content, rather than the other way around.
    val showOnLockScreen: Flow<Boolean> =
        context.settingsDataStore.data.map { it[Keys.SHOW_ON_LOCK_SCREEN] ?: false }

    // FEAT-08: see WishTemplate.kt.
    val wishTemplates: Flow<WishTemplates> =
        context.settingsDataStore.data.map { WishTemplates.fromJson(it[Keys.WISH_TEMPLATES_JSON]) }

    // FEAT-11: gates whether MainActivity routes to the onboarding flow or straight to Home.
    val hasCompletedOnboarding: Flow<Boolean> =
        context.settingsDataStore.data.map { it[Keys.HAS_COMPLETED_ONBOARDING] ?: false }

    suspend fun setNotificationsEnabled(enabled: Boolean) {
        context.settingsDataStore.edit { it[Keys.NOTIFICATIONS_ENABLED] = enabled }
    }

    suspend fun setSoundEnabled(enabled: Boolean) {
        context.settingsDataStore.edit { it[Keys.SOUND_ENABLED] = enabled }
    }

    suspend fun setDefaultReminderTime(value: String) {
        context.settingsDataStore.edit { it[Keys.DEFAULT_REMINDER_TIME] = value }
    }

    suspend fun setReminderTimeOfDay(hour: Int, minute: Int) {
        context.settingsDataStore.edit {
            it[Keys.REMINDER_HOUR] = hour
            it[Keys.REMINDER_MINUTE] = minute
        }
    }

    suspend fun setProfileName(value: String) {
        context.settingsDataStore.edit { it[Keys.PROFILE_NAME] = value }
    }

    suspend fun setProfileEmail(value: String) {
        context.settingsDataStore.edit { it[Keys.PROFILE_EMAIL] = value }
    }

    suspend fun setProfileBirthday(value: Long?) {
        context.settingsDataStore.edit {
            if (value != null) it[Keys.PROFILE_BIRTHDAY] = value else it.remove(Keys.PROFILE_BIRTHDAY)
        }
    }

    suspend fun setShowOnLockScreen(show: Boolean) {
        context.settingsDataStore.edit { it[Keys.SHOW_ON_LOCK_SCREEN] = show }
    }

    suspend fun setWishTemplates(value: WishTemplates) {
        context.settingsDataStore.edit { it[Keys.WISH_TEMPLATES_JSON] = value.toJson() }
    }

    suspend fun setHasCompletedOnboarding(completed: Boolean) {
        context.settingsDataStore.edit { it[Keys.HAS_COMPLETED_ONBOARDING] = completed }
    }

    companion object {
        const val DEFAULT_REMINDER_HOUR = 9
        const val DEFAULT_REMINDER_MINUTE = 0

        @Volatile private var INSTANCE: SettingsRepository? = null

        fun getInstance(context: Context): SettingsRepository =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: SettingsRepository(context.applicationContext).also { INSTANCE = it }
            }
    }
}
