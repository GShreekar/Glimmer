package com.glimmer.app.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface ReminderDao {
    @Query("SELECT * FROM reminders WHERE birthdayId = :birthdayId ORDER BY daysBefore ASC")
    fun getRemindersForBirthday(birthdayId: Int): Flow<List<Reminder>>

    @Query("SELECT * FROM reminders WHERE birthdayId = :birthdayId ORDER BY daysBefore ASC")
    suspend fun getRemindersForBirthdayOnce(birthdayId: Int): List<Reminder>

    @Query("SELECT * FROM reminders")
    suspend fun getAllReminders(): List<Reminder>

    @Insert
    suspend fun insertReminder(reminder: Reminder)

    @Query("DELETE FROM reminders WHERE birthdayId = :birthdayId")
    suspend fun deleteRemindersForBirthday(birthdayId: Int)

    /**
     * Replaces every reminder for [birthdayId] with one row per entry in [daysBeforeList]. Used
     * by both Add (an empty existing set) and Edit (whatever was there before) — simpler and less
     * error-prone than diffing the old and new offset sets row by row.
     */
    @Transaction
    suspend fun replaceReminders(birthdayId: Int, daysBeforeList: List<Int>) {
        deleteRemindersForBirthday(birthdayId)
        daysBeforeList.forEach { days -> insertReminder(Reminder(birthdayId = birthdayId, daysBefore = days)) }
    }
}
