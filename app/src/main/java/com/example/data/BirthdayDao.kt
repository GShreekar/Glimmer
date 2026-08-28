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

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBirthday(birthday: Birthday)

    @Update
    suspend fun updateBirthday(birthday: Birthday)

    @Query("DELETE FROM birthdays WHERE id = :id")
    suspend fun deleteBirthdayById(id: Int)

    @Query("SELECT * FROM birthdays WHERE id = :id")
    fun getBirthdayById(id: Int): Flow<Birthday?>
}
