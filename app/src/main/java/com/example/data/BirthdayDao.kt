package com.example.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface BirthdayDao {
    @Query("SELECT * FROM birthdays ORDER BY dateOfBirth ASC")
    fun getAllBirthdays(): Flow<List<Birthday>>

    // Returns the generated row id — callers need it to schedule an alarm keyed to the real id
    // rather than the default `id = 0` every unsaved Birthday carries.
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertBirthday(birthday: Birthday): Long

    @Update
    suspend fun updateBirthday(birthday: Birthday)

    @Query("DELETE FROM birthdays WHERE id = :id")
    suspend fun deleteBirthdayById(id: Int)

    @Query("SELECT * FROM birthdays WHERE id = :id")
    fun getBirthdayById(id: Int): Flow<Birthday?>
}
