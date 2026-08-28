package com.example.data

import kotlinx.coroutines.flow.Flow

class BirthdayRepository(private val birthdayDao: BirthdayDao) {
    val allBirthdays: Flow<List<Birthday>> = birthdayDao.getAllBirthdays()

    suspend fun insert(birthday: Birthday) = birthdayDao.insertBirthday(birthday)

    suspend fun update(birthday: Birthday) = birthdayDao.updateBirthday(birthday)

    suspend fun deleteById(id: Int) = birthdayDao.deleteBirthdayById(id)

    fun getBirthdayById(id: Int) = birthdayDao.getBirthdayById(id)
}
