package com.glimmer.app.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.glimmer.app.data.Birthday
import com.glimmer.app.data.BirthdayRepository
import com.glimmer.app.data.NotificationScheduler
import com.glimmer.app.data.SettingsRepository
import com.glimmer.app.data.birthLocalDate
import com.glimmer.app.data.birthMonthDay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.MonthDay
import java.time.temporal.ChronoUnit

class GlimmerViewModel(
    application: Application,
    private val repository: BirthdayRepository,
    private val settingsRepository: SettingsRepository
) : AndroidViewModel(application) {

    val allBirthdays: StateFlow<List<Birthday>> = repository.allBirthdays
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    // Search query state
    val searchQuery = MutableStateFlow("")

    // Filtered + sorted birthdays based on search query
    val filteredBirthdays: StateFlow<List<Birthday>> = combine(allBirthdays, searchQuery) { birthdays, query ->
        val filtered = if (query.isBlank()) birthdays else birthdays.filter {
            it.name.contains(query, ignoreCase = true) || it.relationship.contains(query, ignoreCase = true)
        }
        // Sort by next occurrence (days until next birthday, irrespective of birth year)
        filtered.sortedBy { daysUntilBirthday(it) }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    // ── Global notification settings (backed by SettingsRepository/DataStore) ─────────────
    // NotificationsSettingsScreen used to write these to a SharedPreferences file that nothing
    // else ever read, so every switch there was decorative. They now gate scheduleNotification
    // below and are read by the alarm receivers directly via SettingsRepository.getInstance.

    val notificationsEnabled: StateFlow<Boolean> = settingsRepository.notificationsEnabled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    val soundEnabled: StateFlow<Boolean> = settingsRepository.soundEnabled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    val defaultReminderTime: StateFlow<String> = settingsRepository.defaultReminderTime
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "1 day before")

    val reminderHour: StateFlow<Int> = settingsRepository.reminderHour
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), SettingsRepository.DEFAULT_REMINDER_HOUR)

    val reminderMinute: StateFlow<Int> = settingsRepository.reminderMinute
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), SettingsRepository.DEFAULT_REMINDER_MINUTE)

    fun setNotificationsEnabled(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.setNotificationsEnabled(enabled)
            // Flipping the global switch has to actually (dis)arm every alarm immediately —
            // otherwise turning it off would look like it worked while every reminder kept firing.
            val birthdays = repository.allBirthdays.first()
            if (enabled) {
                val hour = settingsRepository.reminderHour.first()
                val minute = settingsRepository.reminderMinute.first()
                birthdays.forEach { birthday ->
                    if (birthday.reminderEnabled) {
                        val offset = NotificationScheduler.reminderTimeToOffset(birthday.reminderTime)
                        NotificationScheduler.scheduleReminder(getApplication(), birthday, offset, hour, minute)
                    }
                }
            } else {
                birthdays.forEach { birthday ->
                    NotificationScheduler.cancelReminder(getApplication(), birthday.id)
                }
            }
        }
    }

    fun setSoundEnabled(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.setSoundEnabled(enabled) }
    }

    fun setDefaultReminderTime(value: String) {
        viewModelScope.launch { settingsRepository.setDefaultReminderTime(value) }
    }

    /**
     * Sets the time of day reminders fire at and immediately re-arms every currently-enabled
     * alarm to the new time — otherwise the change would only take effect the next time each
     * alarm happens to re-arm itself (see BirthdayAlarmReceiver), which could be up to a year out.
     */
    fun setReminderTimeOfDay(hour: Int, minute: Int) {
        viewModelScope.launch {
            settingsRepository.setReminderTimeOfDay(hour, minute)
            if (!settingsRepository.notificationsEnabled.first()) return@launch
            repository.allBirthdays.first().forEach { birthday ->
                if (birthday.reminderEnabled) {
                    val offset = NotificationScheduler.reminderTimeToOffset(birthday.reminderTime)
                    NotificationScheduler.scheduleReminder(getApplication(), birthday, offset, hour, minute)
                }
            }
        }
    }

    // ── Profile (backed by the same DataStore) ─────────────────────────────────────────────
    // A single source of truth means SettingsScreen's display and ProfileSettingsScreen's editor
    // can never drift apart — the old SharedPreferences + derivedStateOf combination in
    // SettingsScreen only refreshed on process restart, since derivedStateOf only re-evaluates
    // when a Compose State it reads changes, and SharedPreferences isn't one.

    val profileName: StateFlow<String> = settingsRepository.profileName
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "")

    val profileEmail: StateFlow<String> = settingsRepository.profileEmail
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "")

    fun setProfileName(value: String) {
        viewModelScope.launch { settingsRepository.setProfileName(value) }
    }

    fun setProfileEmail(value: String) {
        viewModelScope.launch { settingsRepository.setProfileEmail(value) }
    }

    /**
     * Whether a birthday for this name (case-insensitive) on this date already exists. Add/Edit
     * call this before saving and surface a warning instead of silently creating a duplicate
     * person — there was previously no uniqueness check at all.
     */
    suspend fun isDuplicateBirthday(name: String, dateOfBirth: Long, excludeId: Int = -1): Boolean =
        repository.existsByNameAndDate(name, dateOfBirth, excludeId)

    fun insertBirthday(birthday: Birthday) {
        viewModelScope.launch {
            // Room assigns the real primary key here; the incoming `birthday` still carries the
            // default id = 0. Scheduling with that would collide with every other new birthday's
            // alarm (they'd all share PendingIntent request code 0).
            val newId = repository.insert(birthday).toInt()
            scheduleNotification(birthday.copy(id = newId))
        }
    }

    fun updateBirthday(birthday: Birthday) {
        viewModelScope.launch {
            repository.update(birthday)
            scheduleNotification(birthday)
        }
    }

    // Holds the just-deleted row so undoDelete() can restore it. A single in-memory slot is
    // enough — there's only ever one "undo" affordance live at a time (the most recent delete),
    // and it's implicitly cleared by being overwritten on the next delete.
    private var lastDeleted: Birthday? = null

    private val _deleteEvents = MutableSharedFlow<String>(extraBufferCapacity = 1)

    /** Emits the deleted person's name once per delete, for the Home screen to offer Undo on. */
    val deleteEvents: SharedFlow<String> = _deleteEvents.asSharedFlow()

    fun deleteBirthday(id: Int) {
        viewModelScope.launch {
            val birthday = allBirthdays.value.firstOrNull { it.id == id }
            lastDeleted = birthday
            NotificationScheduler.cancelReminder(getApplication(), id)
            repository.deleteById(id)
            birthday?.let { _deleteEvents.emit(it.name) }
        }
    }

    /**
     * Re-inserts the most recently deleted birthday (a fresh row — the original id is gone with
     * the delete, so this behaves like adding it again, alarm included) and re-arms its reminder.
     * No-op if nothing was deleted since the last undo, or another delete has since overwritten it.
     */
    fun undoDelete() {
        val birthday = lastDeleted ?: return
        lastDeleted = null
        insertBirthday(birthday.copy(id = 0))
    }

    /**
     * A live [Birthday] by id, for the Detail and Edit screens.
     *
     * Callers MUST wrap this in `remember(id) { ... }` rather than calling it directly in the
     * composition body — this builds a fresh `stateIn`-backed collector every time it's called,
     * and calling it unmemoized meant a new one was created on every recomposition (including
     * every keystroke in EditBirthdayScreen's form), each starting at `null` before the query
     * resolved, none of them ever cancelled early since they all lived in viewModelScope.
     * `remember(id)` makes this run once per distinct id, which is also the only time a new
     * collector is actually wanted.
     */
    fun getBirthdayById(id: Int): StateFlow<Birthday?> = repository.getBirthdayById(id).stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = null
    )

    private suspend fun scheduleNotification(birthday: Birthday) {
        if (!settingsRepository.notificationsEnabled.first()) {
            // Global toggle is off: make sure this birthday doesn't have a stray alarm armed
            // rather than silently scheduling one that the user has already said they don't want.
            NotificationScheduler.cancelReminder(getApplication(), birthday.id)
            return
        }
        val offset = NotificationScheduler.reminderTimeToOffset(birthday.reminderTime)
        NotificationScheduler.scheduleReminder(
            getApplication(), birthday, offset,
            hour24 = settingsRepository.reminderHour.first(),
            minute = settingsRepository.reminderMinute.first()
        )
    }
}

/**
 * Returns how many days until the next occurrence of this birthday (0 = today).
 *
 * Reads [Birthday.dateOfBirth] exclusively through [birthMonthDay] — the one place that value is
 * interpreted as a calendar date — rather than a local Calendar, so this can't drift out of sync
 * with how the date was stored or how it's displayed elsewhere.
 */
fun daysUntilBirthday(birthday: Birthday, today: LocalDate = LocalDate.now()): Int {
    val monthDay = birthday.birthMonthDay()

    // MonthDay.atYear resolves Feb 29 to Feb 28 in a non-leap year instead of rolling into March.
    var next = monthDay.atYear(today.year)
    if (next.isBefore(today)) next = monthDay.atYear(today.year + 1)

    return ChronoUnit.DAYS.between(today, next).toInt()
}

/**
 * The age the person turns on their next birthday — or turns today, if today is the day.
 *
 * `today` counts as "already happened", not "about to happen": someone born in 2000 turns 26,
 * not 27, on their birthday itself.
 */
fun ageOnNextBirthday(birthday: Birthday, today: LocalDate = LocalDate.now()): Int {
    val birth = birthday.birthLocalDate()
    val thisYear = MonthDay.from(birth).atYear(today.year)
    val nextOccurrenceYear = if (thisYear.isBefore(today)) today.year + 1 else today.year
    return nextOccurrenceYear - birth.year
}

class GlimmerViewModelFactory(
    private val application: Application,
    private val repository: BirthdayRepository,
    private val settingsRepository: SettingsRepository
) : ViewModelProvider.AndroidViewModelFactory(application) {
    @Suppress("UNCHECKED_CAST")
    override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(GlimmerViewModel::class.java)) {
            return GlimmerViewModel(application, repository, settingsRepository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
