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
import com.glimmer.app.data.PhotoStorage
import com.glimmer.app.data.Reminder
import com.glimmer.app.data.SettingsRepository
import com.glimmer.app.data.WishTemplates
import com.glimmer.app.data.birthMonthDay
import com.glimmer.app.widget.WidgetScheduler
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
import kotlinx.coroutines.flow.map
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

    // FEAT-12: null = no filter (show every relationship). Same "expose the MutableStateFlow
    // directly, UI writes .value" pattern as searchQuery above.
    val relationshipFilter = MutableStateFlow<String?>(null)
    val sortMode = MutableStateFlow(HomeSortMode.DATE)

    /** The distinct relationships currently in use, for Home's filter chip row. */
    val availableRelationships: StateFlow<List<String>> = allBirthdays
        .map { list -> list.map { it.relationship }.filter { it.isNotBlank() }.distinct().sorted() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

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
    //
    // FEAT-12: also where the flat "Upcoming" list becomes This Week/This Month/Later, and where
    // favorites get pulled out to their own section — a flat list stopped being scannable once
    // there were 60+ people in it. A favorited person whose birthday ISN'T today shows in
    // Favorites, not also in one of the time buckets; today's birthdays always stay in Today
    // regardless of favorite status, since "it's happening right now" outranks the pin.
    val homeUiState: StateFlow<HomeUiState> = combine(
        filteredBirthdays, dayTicker, relationshipFilter, sortMode
    ) { birthdays, today, filter, sort ->
        val relevant = if (filter == null) birthdays else birthdays.filter { it.relationship == filter }
        val items = relevant.map { BirthdayUi(it, daysUntilBirthday(it, today), ageOnNextBirthday(it, today)) }

        val todayItems = items.filter { it.daysUntil == 0 }
        val upcoming = items.filter { it.daysUntil > 0 }
        val favoriteItems = upcoming.filter { it.birthday.isFavorite }
        val remaining = upcoming.filterNot { it.birthday.isFavorite }

        HomeUiState(
            favorites = favoriteItems.sortedFor(sort),
            today = todayItems.sortedFor(sort),
            thisWeek = remaining.filter { it.daysUntil <= 7 }.sortedFor(sort),
            thisMonth = remaining.filter { it.daysUntil in 8..30 }.sortedFor(sort),
            later = remaining.filter { it.daysUntil > 30 }.sortedFor(sort)
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = HomeUiState()
    )

    fun toggleFavorite(id: Int) {
        viewModelScope.launch(viewModelExceptionHandler) {
            val isFavorite = allBirthdays.value.firstOrNull { it.id == id }?.isFavorite ?: return@launch
            repository.setFavorite(id, !isFavorite)
            refreshWidget()
        }
    }

    /** FEAT-06: the review's own "updateAppWidgetOnDataChange" — called after every write below. */
    private fun refreshWidget() {
        WidgetScheduler.requestImmediateUpdate(getApplication())
    }

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

    // FEAT-08
    val wishTemplates: StateFlow<WishTemplates> = settingsRepository.wishTemplates
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), WishTemplates())

    fun setWishTemplates(value: WishTemplates) {
        viewModelScope.launch(viewModelExceptionHandler) { settingsRepository.setWishTemplates(value) }
    }

    // FEAT-11
    val hasCompletedOnboarding: StateFlow<Boolean> = settingsRepository.hasCompletedOnboarding
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true) // see MainActivity: the real initial value is read before this StateFlow's default would ever be seen

    fun setHasCompletedOnboarding(completed: Boolean) {
        viewModelScope.launch(viewModelExceptionHandler) { settingsRepository.setHasCompletedOnboarding(completed) }
    }

    fun setNotificationsEnabled(enabled: Boolean) {
        viewModelScope.launch(viewModelExceptionHandler) {
            settingsRepository.setNotificationsEnabled(enabled)
            // Flipping the global switch has to actually (dis)arm every alarm immediately —
            // otherwise turning it off would look like it worked while every reminder kept firing.
            rescheduleEveryBirthday(onlyIfNotificationsEnabled = false, forceDisableAll = !enabled)
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
            rescheduleEveryBirthday(onlyIfNotificationsEnabled = true, forceDisableAll = false)
        }
    }

    /** Re-arms (or cancels) every person's reminders — used when a global setting changes. */
    private suspend fun rescheduleEveryBirthday(onlyIfNotificationsEnabled: Boolean, forceDisableAll: Boolean) {
        if (onlyIfNotificationsEnabled && !settingsRepository.notificationsEnabled.first()) return
        val hour = settingsRepository.reminderHour.first()
        val minute = settingsRepository.reminderMinute.first()
        repository.allBirthdays.first().forEach { birthday ->
            if (forceDisableAll) {
                NotificationScheduler.cancelAllReminders(getApplication(), birthday.id)
            } else {
                val reminders = repository.getRemindersForBirthdayOnce(birthday.id)
                NotificationScheduler.rescheduleAll(getApplication(), birthday, reminders, hour, minute)
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

    val profileBirthday: StateFlow<Long?> = settingsRepository.profileBirthday
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    fun setProfileName(value: String) {
        viewModelScope.launch(viewModelExceptionHandler) { settingsRepository.setProfileName(value) }
    }

    fun setProfileEmail(value: String) {
        viewModelScope.launch(viewModelExceptionHandler) { settingsRepository.setProfileEmail(value) }
    }

    fun setProfileBirthday(value: Long?) {
        viewModelScope.launch(viewModelExceptionHandler) { settingsRepository.setProfileBirthday(value) }
    }

    /**
     * Whether a birthday for this name (case-insensitive) on this date already exists. Add/Edit
     * call this before saving and surface a warning instead of silently creating a duplicate
     * person — there was previously no uniqueness check at all.
     */
    suspend fun isDuplicateBirthday(name: String, dateOfBirth: Long, excludeId: Int = -1): Boolean =
        repository.existsByNameAndDate(name, dateOfBirth, excludeId)

    /**
     * FEAT-04: [reminderOffsets] is the full set of days-before offsets this person should be
     * reminded at (e.g. {7, 1} for "a week before to shop, the day before as a nudge") — replaces
     * the old single `reminderTime` string entirely for scheduling purposes.
     */
    fun insertBirthday(birthday: Birthday, reminderOffsets: Set<Int> = emptySet()) {
        viewModelScope.launch(viewModelExceptionHandler) {
            // Room assigns the real primary key here; the incoming `birthday` still carries the
            // default id = 0. Scheduling with that would collide with every other new birthday's
            // alarm (they'd all share PendingIntent request code 0).
            val newId = repository.insert(birthday).toInt()
            repository.setReminders(newId, reminderOffsets.toList())
            scheduleNotification(birthday.copy(id = newId))
            refreshWidget()
        }
    }

    fun updateBirthday(birthday: Birthday, reminderOffsets: Set<Int>) {
        viewModelScope.launch(viewModelExceptionHandler) {
            repository.update(birthday)
            repository.setReminders(birthday.id, reminderOffsets.toList())
            scheduleNotification(birthday)
            refreshWidget()
        }
    }

    /** FEAT-02: bulk-inserts an entire Contacts import in one go, skipping exact duplicates. */
    suspend fun importBirthdays(candidates: List<Birthday>): Int {
        var imported = 0
        candidates.forEach { candidate ->
            if (!repository.existsByNameAndDate(candidate.name, candidate.dateOfBirth)) {
                val newId = repository.insert(candidate).toInt()
                if (candidate.reminderEnabled) {
                    val offset = NotificationScheduler.reminderTimeToOffset(settingsRepository.defaultReminderTime.first())
                    repository.setReminders(newId, listOf(offset))
                    scheduleNotification(candidate.copy(id = newId))
                }
                imported++
            }
        }
        // ImportContactsScreen navigates back the moment this returns, tearing down its own
        // SnackbarHost — the confirmation goes out on the same shared channel Add/Edit's failure
        // events use, which HomeScreen (still around after the navigation) renders as a snackbar.
        if (imported > 0) {
            _events.tryEmit(UiEvent.ImportSuccess(imported))
            refreshWidget()
        }
        return imported
    }

    // Holds the just-deleted row (and its reminder offsets, since those are cascade-deleted with
    // it) so undoDelete() can restore both. A single in-memory slot is enough — there's only ever
    // one "undo" affordance live at a time (the most recent delete), and it's implicitly cleared
    // by being overwritten on the next delete.
    private var lastDeleted: Pair<Birthday, List<Int>>? = null

    private val _deleteEvents = MutableSharedFlow<String>(extraBufferCapacity = 1)

    /** Emits the deleted person's name once per delete, for the Home screen to offer Undo on. */
    val deleteEvents: SharedFlow<String> = _deleteEvents.asSharedFlow()

    fun deleteBirthday(id: Int) {
        viewModelScope.launch(viewModelExceptionHandler) {
            val birthday = allBirthdays.value.firstOrNull { it.id == id }
            if (birthday != null) {
                val offsets = repository.getRemindersForBirthdayOnce(id).map { it.daysBefore }
                // The PREVIOUS lastDeleted (if any) is now unrecoverable — undo only ever offers
                // the most recent delete — so this is the last point its photo file can be
                // removed without risking breaking an undo that's still actually available.
                lastDeleted?.first?.photoUri?.let { PhotoStorage.deleteManagedPhoto(getApplication(), it) }
                lastDeleted = birthday to offsets
            }
            NotificationScheduler.cancelAllReminders(getApplication(), id)
            repository.deleteById(id) // reminders row cascade-deletes with it
            birthday?.let { _deleteEvents.emit(it.name) }
            refreshWidget()
        }
    }

    /**
     * Re-inserts the most recently deleted birthday (a fresh row — the original id is gone with
     * the delete, so this behaves like adding it again, alarm included) with the same reminder
     * offsets it had, and re-arms them. No-op if nothing was deleted since the last undo, or
     * another delete has since overwritten it.
     */
    fun undoDelete() {
        val (birthday, offsets) = lastDeleted ?: return
        lastDeleted = null
        insertBirthday(birthday.copy(id = 0), offsets.toSet())
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

    /**
     * FEAT-04: this person's configured reminder offsets. Same `remember(id)` rule as
     * [getBirthdayById] — and the same reason for a nullable "not loaded yet" initial value
     * rather than emptyList(): a caller seeding a local editable Set from the first emission
     * needs to tell "hasn't answered yet" apart from "this person genuinely has zero reminders".
     */
    fun getRemindersForBirthday(id: Int): StateFlow<List<Reminder>?> = repository.getRemindersForBirthday(id).stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = null
    )

    private suspend fun scheduleNotification(birthday: Birthday) {
        if (!settingsRepository.notificationsEnabled.first() || !birthday.reminderEnabled) {
            // Global toggle is off, or this person's own switch is off: make sure nothing of
            // theirs is armed rather than silently scheduling reminders the user doesn't want.
            NotificationScheduler.cancelAllReminders(getApplication(), birthday.id)
            return
        }
        val reminders = repository.getRemindersForBirthdayOnce(birthday.id)
        NotificationScheduler.rescheduleAll(
            getApplication(), birthday, reminders,
            hour24 = settingsRepository.reminderHour.first(),
            minute = settingsRepository.reminderMinute.first()
        )
    }
}

/** A one-shot event a screen renders and then forgets. */
sealed interface UiEvent {
    data class Error(@param:StringRes val messageRes: Int) : UiEvent
    /** FEAT-02: how many birthdays a Contacts import just added — always > 0 when emitted. */
    data class ImportSuccess(val count: Int) : UiEvent
}

/** A [Birthday] plus the per-day-dependent values HomeScreen renders, computed once in the ViewModel. */
data class BirthdayUi(
    val birthday: Birthday,
    val daysUntil: Int,
    // FEAT-05: null when the birth year isn't known — HomeScreen hides "Turning N" for these.
    val ageTurning: Int?
) {
    // FEAT-12: 18/21 (the two that matter before the decade pattern kicks in) plus every round
    // decade — "these are the ones worth planning for," per the review's own reasoning for why
    // this exists alongside the "week before" reminder offset.
    val isMilestone: Boolean get() = ageTurning != null && (ageTurning == 18 || ageTurning == 21 || ageTurning % 10 == 0)
}

/** FEAT-12: Home's flat "Upcoming" list split into scannable sections. */
data class HomeUiState(
    val favorites: List<BirthdayUi> = emptyList(),
    val today: List<BirthdayUi> = emptyList(),
    val thisWeek: List<BirthdayUi> = emptyList(),
    val thisMonth: List<BirthdayUi> = emptyList(),
    val later: List<BirthdayUi> = emptyList()
) {
    val isEmpty: Boolean get() = favorites.isEmpty() && today.isEmpty() && thisWeek.isEmpty() && thisMonth.isEmpty() && later.isEmpty()
}

enum class HomeSortMode { DATE, NAME }

/** Within a section, DATE keeps the natural next-occurrence order the section was built in. */
private fun List<BirthdayUi>.sortedFor(mode: HomeSortMode): List<BirthdayUi> = when (mode) {
    HomeSortMode.DATE -> this
    HomeSortMode.NAME -> sortedBy { it.birthday.name.lowercase() }
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
 * The age the person turns on their next birthday — or turns today, if today is the day. Returns
 * null when [Birthday.birthYear] isn't known (FEAT-05) — there is no meaningful age to show.
 *
 * `today` counts as "already happened", not "about to happen": someone born in 2000 turns 26,
 * not 27, on their birthday itself.
 */
fun ageOnNextBirthday(birthday: Birthday, today: LocalDate = LocalDate.now()): Int? {
    val birthYear = birthday.birthYear ?: return null
    val thisYear = birthday.birthMonthDay().atYear(today.year)
    val nextOccurrenceYear = if (thisYear.isBefore(today)) today.year + 1 else today.year
    return nextOccurrenceYear - birthYear
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
