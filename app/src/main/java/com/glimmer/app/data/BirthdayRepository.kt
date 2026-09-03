package com.glimmer.app.data

import kotlinx.coroutines.flow.Flow

class BirthdayRepository(private val birthdayDao: BirthdayDao) {
    val allBirthdays: Flow<List<Birthday>> = birthdayDao.getAllBirthdays()

    suspend fun insert(birthday: Birthday): Long = birthdayDao.insertBirthday(normalizeForStorage(birthday))

    suspend fun update(birthday: Birthday) = birthdayDao.updateBirthday(normalizeForStorage(birthday))

    suspend fun deleteById(id: Int) = birthdayDao.deleteBirthdayById(id)

    fun getBirthdayById(id: Int) = birthdayDao.getBirthdayById(id)

    suspend fun existsByNameAndDate(name: String, dateOfBirth: Long, excludeId: Int = -1): Boolean =
        birthdayDao.existsByNameAndDate(name, dateOfBirth, excludeId)

    /** See PERF-03 / BirthdayDao.searchBirthdaysSorted. */
    fun searchBirthdaysSorted(query: String, todayMonthDay: Int): Flow<List<Birthday>> =
        birthdayDao.searchBirthdaysSorted(query, todayMonthDay)

    // This is the ONLY path birthdays are written through (insert/update above), so it's the one
    // place that needs to keep monthDayKey in sync with dateOfBirth — callers building a
    // Birthday(...) never need to think about it.
    private fun normalizeForStorage(birthday: Birthday): Birthday {
        val monthDay = birthday.birthMonthDay()
        return birthday.copy(monthDayKey = monthDay.monthValue * 100 + monthDay.dayOfMonth)
    }
}
