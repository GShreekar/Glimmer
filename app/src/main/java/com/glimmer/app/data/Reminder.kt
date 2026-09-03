package com.glimmer.app.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * FEAT-04: one row per (person, offset) reminder. Previously a [Birthday] carried a single
 * `reminderTime` string, so "a week before to buy a gift" and "the day of" were mutually
 * exclusive — the vision explicitly wants both. `onDelete = CASCADE` means deleting a birthday
 * cleans up its reminders automatically; no separate delete call needed elsewhere.
 *
 * There's no `timeOfDay` column (the review's own sketch has one) — every reminder fires at the
 * single app-wide time configured in Notifications settings (see SettingsRepository.reminderHour/
 * reminderMinute, added for BUG-27). Per-reminder times would mean a time picker per offset chip,
 * which is a lot of added UI for a want nobody asked for; one global time users already control
 * covers the "week before to shop, day-of to wish them" need this feature exists for.
 */
@Entity(
    tableName = "reminders",
    foreignKeys = [
        ForeignKey(
            entity = Birthday::class,
            parentColumns = ["id"],
            childColumns = ["birthdayId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("birthdayId")]
)
data class Reminder(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val birthdayId: Int,
    val daysBefore: Int
)

/**
 * The fixed, small set of offsets a reminder can use. Deliberately small and fixed (rather than
 * an arbitrary Int) so [NotificationScheduler] can derive a stable, collision-free PendingIntent
 * request code from (birthdayId, offset) alone — birthdayId * 10 + ordinal — without needing to
 * know a Reminder row's own database id, which is what lets edits cancel exactly the alarms that
 * are no longer selected (see NotificationScheduler.rescheduleAll).
 */
enum class ReminderOffset(val daysBefore: Int) {
    ON_DAY(0),
    ONE_DAY_BEFORE(1),
    THREE_DAYS_BEFORE(3),
    ONE_WEEK_BEFORE(7);

    companion object {
        fun fromDaysBefore(days: Int): ReminderOffset? = entries.find { it.daysBefore == days }
    }
}
