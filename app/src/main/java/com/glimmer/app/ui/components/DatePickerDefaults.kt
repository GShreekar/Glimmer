package com.glimmer.app.ui.components

import androidx.compose.material3.DatePickerState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import java.time.LocalDate
import java.time.ZoneOffset

/**
 * Restricts a DatePicker to plausible dates of birth: today or earlier, within a human lifespan.
 * Without this, Add/EditBirthdayScreen's picker accepted any date at all — a birthday set in
 * 2030 produced a negative age and a nonsensical countdown, with no validation catching it.
 */
@OptIn(ExperimentalMaterial3Api::class)
private class PastDateSelectableDates(private val currentYear: Int) : SelectableDates {
    override fun isSelectableDate(utcTimeMillis: Long): Boolean =
        utcTimeMillis <= System.currentTimeMillis()

    override fun isSelectableYear(year: Int): Boolean =
        year in (currentYear - 120)..currentYear
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun rememberBirthDatePickerState(initialSelectedDateMillis: Long? = null): DatePickerState {
    val currentYear = LocalDate.now(ZoneOffset.UTC).year
    return rememberDatePickerState(
        initialSelectedDateMillis = initialSelectedDateMillis,
        yearRange = (currentYear - 120)..currentYear,
        selectableDates = PastDateSelectableDates(currentYear)
    )
}
