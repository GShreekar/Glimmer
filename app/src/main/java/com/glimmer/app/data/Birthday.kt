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
    val dateOfBirth: Long,
    val relationship: String,
    val reminderEnabled: Boolean = true,
    val reminderTime: String = "1 day before",
    val photoUri: String? = null,
    val notes: String? = null,
    val phoneNumber: String? = null,
    // PERF-03: a denormalized "month*100+day" (e.g. 5 Mar -> 305) derived from [dateOfBirth],
    // recomputed by BirthdayRepository on every insert/update — never set directly by callers
    // (the default here only matters before the repository normalizes a freshly-built Birthday).
    // Lets SQLite sort "next occurrence first" and match search terms itself, instead of Room
    // handing back every row so Kotlin can re-sort/filter it on every recomposition/keystroke.
    // Named distinctly from the birthMonthDay() extension (which returns a java.time.MonthDay,
    // not an Int) so the two can never be confused for each other at a call site.
    val monthDayKey: Int = 0
)
