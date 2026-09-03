package com.glimmer.app.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

@Serializable
@Entity(tableName = "birthdays", indices = [Index("monthDayKey")])
data class Birthday(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val name: String,
    // Always a real calendar date (month/day are always meaningful); the YEAR component is a
    // placeholder when [birthYear] is null — see FEAT-05. Never read dateOfBirth's year directly;
    // go through [birthYear] instead.
    val dateOfBirth: Long,
    val relationship: String,
    // Master switch: whether this person has any reminders scheduled at all. The actual set of
    // offsets (which can now be more than one — see FEAT-04) lives in the `reminders` table, not
    // on this entity.
    val reminderEnabled: Boolean = true,
    @Deprecated(
        "Superseded by the reminders table (FEAT-04) — a person can have several reminders now, " +
            "so a single string here can't represent that. Kept only so existing rows don't need " +
            "a destructive column-drop migration; never read for scheduling or display anymore."
    )
    val reminderTime: String = "1 day before",
    val photoUri: String? = null,
    val notes: String? = null,
    val phoneNumber: String? = null,
    // FEAT-01: the Contacts row this person was linked from (if any), via the no-permission-needed
    // contact picker — see ContactPicker.kt. Lets a future "refresh from contacts" re-pull a
    // number that changed, without re-requesting the picker.
    val contactLookupKey: String? = null,
    // FEAT-05: the real birth year, or null when it isn't known (many address books — Facebook
    // imports especially — only ever record month/day). Age is hidden wherever this is null;
    // dateOfBirth still carries a placeholder year so month/day-only logic elsewhere never needs
    // to special-case it.
    val birthYear: Int? = null,
    // PERF-03: a denormalized "month*100+day" (e.g. 5 Mar -> 305) derived from [dateOfBirth],
    // recomputed by BirthdayRepository on every insert/update — never set directly by callers
    // (the default here only matters before the repository normalizes a freshly-built Birthday).
    // Lets SQLite sort "next occurrence first" and match search terms itself, instead of Room
    // handing back every row so Kotlin can re-sort/filter it on every recomposition/keystroke.
    // Named distinctly from the birthMonthDay() extension (which returns a java.time.MonthDay,
    // not an Int) so the two can never be confused for each other at a call site.
    val monthDayKey: Int = 0,
    // FEAT-12: pinned to their own section at the top of Home, above the This Week/Month/Later
    // grouping. Deliberately doesn't also change reminder timing/offsets — "default them to
    // earlier reminders" from the review's own sketch would need a whole second default-offset
    // concept; the person can already just add a "1 week before" reminder themselves.
    val isFavorite: Boolean = false
)
