package com.glimmer.app.viewmodel

import android.app.Application
import androidx.annotation.StringRes
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.glimmer.app.R
import com.glimmer.app.data.Birthday
import com.glimmer.app.data.BirthdayRepository
import com.glimmer.app.data.GLog
import com.glimmer.app.data.NotificationScheduler
import com.glimmer.app.data.SettingsRepository
import com.glimmer.app.data.birthLocalDate
import com.glimmer.app.data.birthMonthDay
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Duration
import java.time.LocalDate
import java.time.MonthDay
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit

@OptIn(ExperimentalCoroutinesApi::class) // flatMapLatest, used by filteredBirthdays below
class GlimmerViewModel(
    application: Application,
    private val repository: BirthdayRepository,
    private val settingsRepository: SettingsRepository
) : AndroidViewModel(application) {

    // Section 4.2 (Error handling & logging): previously NOTHING in this ViewModel handled a
    // thrown exception — a failed insert/update/delete (DB error, disk full, an encryption-key
    // issue) would propagate out of the coroutine, get silently dropped, and "look identical to
    // a successful one" from the user's perspective. Every `viewModelScope.launch` below now
    // carries this handler as a safety net: it's logged (GLog.e), and a generic, localized
    // failure event goes out on [events] for a screen to show as a snackbar.
    private val viewModelExceptionHandler = CoroutineExceptionHandler { _, throwable ->
        GLog.e("ViewModel", "Unhandled exception in a ViewModel coroutine", throwable)
        _events.tryEmit(UiEvent.Error(R.string.error_generic))
    }

    private val _events = MutableSharedFlow<UiEvent>(extraBufferCapacity = 1)
    val events: SharedFlow<UiEvent> = _events.asSharedFlow()

    val allBirthdays: StateFlow<List<Birthday>> = repository.allBirthdays
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    // Search query state
    val searchQuery = MutableStateFlow("")

    // Ticks once immediately and then again at every local-midnight rollover, so a countdown
    // ("3 days" -> "2 days") and the today/upcoming split update on their own if the app is left
    // open overnight — previously they only ever refreshed when the process restarted (PERF-02).
    private val dayTicker: Flow<LocalDate> = flow {
        while (true) {
            val today = LocalDate.now()
            emit(today)
            val zone = ZoneId.systemDefault()
            val nextMidnight = today.plusDays(1).atStartOfDay(zone)
            val millisUntilMidnight = Duration.between(ZonedDateTime.now(zone), nextMidnight).toMillis()
            delay(millisUntilMidnight.coerceAtLeast(1_000L))
        }
    }

    // PERF-03: search AND "next occurrence" ordering are now done by SQLite (see
    // BirthdayDao.searchBirthdaysSorted), not by fetching every row and filtering/re-sorting it
    // in Kotlin on every keystroke. flatMapLatest re-issues the query (and cancels the previous
    // one) whenever the search text OR the day boundary changes.
    val filteredBirthdays: StateFlow<List<Birthday>> = combine(searchQuery, dayTicker) { query, today ->
        query to (today.monthValue * 100 + today.dayOfMonth)
    }.flatMapLatest { (query, todayMonthDay) ->
        repository.searchBirthdaysSorted(query, todayMonthDay)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    // PERF-02: HomeScreen used to compute `daysUntilBirthday`/partitioning directly in the
    // composable body — recomputed on every recomposition (a keystroke in the search box, the
    // reminder-warning banner being dismissed, …), not just when the underlying data changed.
    // This is now a single derived StateFlow the screen just renders.
    val homeUiState: StateFlow<HomeUiState> = combine(filteredBirthdays, dayTicker) { birthdays, today ->
        val items = birthdays.map { BirthdayUi(it, daysUntilBirthday(it, today), ageOnNextBirthday(it, today)) }
        HomeUiState(
            today = items.filter { it.daysUntil == 0 },
            upcoming = items.filter { it.daysUntil > 0 }
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = HomeUiState()
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

    // SEC-03: off by default — a birthday notification names a specific person, which shouldn't
    // be readable on a locked screen unless the user opts in.
    val showOnLockScreen: StateFlow<Boolean> = settingsRepository.showOnLockScreen
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    fun setNotificationsEnabled(enabled: Boolean) {
        viewModelScope.launch(viewModelExceptionHandler) {
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
        viewModelScope.launch(viewModelExceptionHandler) { settingsRepository.setSoundEnabled(enabled) }
    }

    fun setDefaultReminderTime(value: String) {
        viewModelScope.launch(viewModelExceptionHandler) { settingsRepository.setDefaultReminderTime(value) }
    }

    fun setShowOnLockScreen(show: Boolean) {
        viewModelScope.launch(viewModelExceptionHandler) { settingsRepository.setShowOnLockScreen(show) }
    }

    /**
     * Sets the time of day reminders fire at and immediately re-arms every currently-enabled
     * alarm to the new time — otherwise the change would only take effect the next time each
     * alarm happens to re-arm itself (see BirthdayAlarmReceiver), which could be up to a year out.
     */
    fun setReminderTimeOfDay(hour: Int, minute: Int) {
        viewModelScope.launch(viewModelExceptionHandler) {
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
        viewModelScope.launch(viewModelExceptionHandler) { settingsRepository.setProfileName(value) }
    }

    fun setProfileEmail(value: String) {
        viewModelScope.launch(viewModelExceptionHandler) { settingsRepository.setProfileEmail(value) }
    }

    /**
     * Whether a birthday for this name (case-insensitive) on this date already exists. Add/Edit
     * call this before saving and surface a warning instead of silently creating a duplicate
     * person — there was previously no uniqueness check at all.
     */
    suspend fun isDuplicateBirthday(name: String, dateOfBirth: Long, excludeId: Int = -1): Boolean =
        repository.existsByNameAndDate(name, dateOfBirth, excludeId)

    fun insertBirthday(birthday: Birthday) {
        viewModelScope.launch(viewModelExceptionHandler) {
            // Room assigns the real primary key here; the incoming `birthday` still carries the
            // default id = 0. Scheduling with that would collide with every other new birthday's
            // alarm (they'd all share PendingIntent request code 0).
            val newId = repository.insert(birthday).toInt()
            scheduleNotification(birthday.copy(id = newId))
        }
    }

    fun updateBirthday(birthday: Birthday) {
        viewModelScope.launch(viewModelExceptionHandler) {
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
        viewModelScope.launch(viewModelExceptionHandler) {
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

/** A one-shot event a screen renders and then forgets — currently just the generic failure case. */
sealed interface UiEvent {
    data class Error(@StringRes val messageRes: Int) : UiEvent
}

/** A [Birthday] plus the per-day-dependent values HomeScreen renders, computed once in the ViewModel. */
data class BirthdayUi(
    val birthday: Birthday,
    val daysUntil: Int,
    val ageTurning: Int
)

data class HomeUiState(
    val today: List<BirthdayUi> = emptyList(),
    val upcoming: List<BirthdayUi> = emptyList()
)

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
