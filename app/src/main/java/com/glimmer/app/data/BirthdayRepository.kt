package com.glimmer.app.data

import kotlinx.coroutines.flow.Flow

class BirthdayRepository(private val birthdayDao: BirthdayDao) {
    val allBirthdays: Flow<List<Birthday>> = birthdayDao.getAllBirthdays()

    suspend fun insert(birthday: Birthday): Long = birthdayDao.insertBirthday(birthday)

    suspend fun update(birthday: Birthday) = birthdayDao.updateBirthday(birthday)

    suspend fun deleteById(id: Int) = birthdayDao.deleteBirthdayById(id)

    fun getBirthdayById(id: Int) = birthdayDao.getBirthdayById(id)

    suspend fun existsByNameAndDate(name: String, dateOfBirth: Long, excludeId: Int = -1): Boolean =
        birthdayDao.existsByNameAndDate(name, dateOfBirth, excludeId)
}
