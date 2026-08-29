package com.example.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.Birthday
import com.example.data.BirthdayRepository
import com.example.data.NotificationScheduler
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.Calendar

class GlimmerViewModel(
    application: Application,
    private val repository: BirthdayRepository
) : AndroidViewModel(application) {

    val allBirthdays: StateFlow<List<Birthday>> = repository.allBirthdays
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    // Search query state
    val searchQuery = MutableStateFlow("")

    // Filtered + sorted birthdays based on search query
    val filteredBirthdays: StateFlow<List<Birthday>> = combine(allBirthdays, searchQuery) { birthdays, query ->
        val filtered = if (query.isBlank()) birthdays else birthdays.filter {
            it.name.contains(query, ignoreCase = true) || it.relationship.contains(query, ignoreCase = true)
        }
        // Sort by next occurrence (days until next birthday, irrespective of birth year)
        filtered.sortedBy { daysUntilBirthday(it.dateOfBirth) }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    fun insertBirthday(birthday: Birthday) {
        viewModelScope.launch {
            repository.insert(birthday)
            scheduleNotification(birthday)
        }
    }

    fun updateBirthday(birthday: Birthday) {
        viewModelScope.launch {
            repository.update(birthday)
            scheduleNotification(birthday)
        }
    }

    fun deleteBirthday(id: Int) {
        viewModelScope.launch {
            NotificationScheduler.cancelReminder(getApplication(), id)
            repository.deleteById(id)
        }
    }

    fun getBirthdayById(id: Int): StateFlow<Birthday?> {
        return repository.getBirthdayById(id).stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = null
        )
    }

    private fun scheduleNotification(birthday: Birthday) {
        val offset = NotificationScheduler.reminderTimeToOffset(birthday.reminderTime)
        NotificationScheduler.scheduleReminder(getApplication(), birthday, offset)
    }
}

/** Returns how many days until the next occurrence of this birthday (0 = today). */
fun daysUntilBirthday(dateOfBirth: Long): Int {
    val now = Calendar.getInstance()
    val bCal = Calendar.getInstance().apply { timeInMillis = dateOfBirth }

    val next = Calendar.getInstance().apply {
        set(Calendar.MONTH, bCal.get(Calendar.MONTH))
        set(Calendar.DAY_OF_MONTH, bCal.get(Calendar.DAY_OF_MONTH))
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }
    if (next.before(now)) next.add(Calendar.YEAR, 1)
    // If today
    val todayStart = Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
    }
    return ((next.timeInMillis - todayStart.timeInMillis) / (1000 * 60 * 60 * 24)).toInt()
}

/** Calculates the age the person will turn on their next birthday. */
fun calculateAge(dateOfBirth: Long): Int {
    val now = Calendar.getInstance()
    val bCal = Calendar.getInstance().apply { timeInMillis = dateOfBirth }
    var age = now.get(Calendar.YEAR) - bCal.get(Calendar.YEAR)
    if (now.get(Calendar.MONTH) < bCal.get(Calendar.MONTH) ||
        (now.get(Calendar.MONTH) == bCal.get(Calendar.MONTH) &&
                now.get(Calendar.DAY_OF_MONTH) < bCal.get(Calendar.DAY_OF_MONTH))
    ) {
        // Birthday hasn't happened yet this year, so they haven't turned that age
    } else {
        // Birthday already passed or is today, next birthday they turn age+1
        age += 1
    }
    return age
}

class GlimmerViewModelFactory(
    private val application: Application,
    private val repository: BirthdayRepository
) : ViewModelProvider.AndroidViewModelFactory(application) {
    @Suppress("UNCHECKED_CAST")
    override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(GlimmerViewModel::class.java)) {
            return GlimmerViewModel(application, repository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
