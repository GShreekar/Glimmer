package com.example.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.Birthday
import com.example.data.BirthdayRepository
import com.example.data.NotificationScheduler
import com.example.data.SettingsRepository
import com.example.data.birthLocalDate
import com.example.data.birthMonthDay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
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

    fun setNotificationsEnabled(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.setNotificationsEnabled(enabled)
            // Flipping the global switch has to actually (dis)arm every alarm immediately —
            // otherwise turning it off would look like it worked while every reminder kept firing.
            val birthdays = repository.allBirthdays.first()
            if (enabled) {
                birthdays.forEach { birthday ->
                    if (birthday.reminderEnabled) {
                        val offset = NotificationScheduler.reminderTimeToOffset(birthday.reminderTime)
                        NotificationScheduler.scheduleReminder(getApplication(), birthday, offset)
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

    fun deleteBirthday(id: Int) {
        viewModelScope.launch {
            NotificationScheduler.cancelReminder(getApplication(), id)
            repository.deleteById(id)
        }
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
        NotificationScheduler.scheduleReminder(getApplication(), birthday, offset)
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
