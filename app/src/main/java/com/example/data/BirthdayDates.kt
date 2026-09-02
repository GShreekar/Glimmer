package com.example.data

import java.time.Instant
import java.time.LocalDate
import java.time.MonthDay
import java.time.ZoneOffset

/**
 * The single place [Birthday.dateOfBirth] is interpreted as a calendar date.
 *
 * The field is stored as UTC midnight — Compose's `DatePickerState.selectedDateMillis` returns
 * UTC midnight for the selected day (see the DatePickerDialog in AddBirthdayScreen /
 * EditBirthdayScreen) — so it must always be read back in UTC. Reading it with the device's
 * default-timezone Calendar shifts the calendar date by a day for anyone west of UTC, and mixing
 * UTC with the default zone across two reads of the same value can produce a month/day pair that
 * never existed together. Every caller — countdowns, ages, alarm scheduling, and every date
 * display — must go through this accessor (or [birthMonthDay]) rather than building its own
 * Calendar/SimpleDateFormat, so the convention cannot drift again.
 */
fun Birthday.birthLocalDate(): LocalDate =
    Instant.ofEpochMilli(dateOfBirth).atZone(ZoneOffset.UTC).toLocalDate()

/** The birthday's month and day, ignoring birth year. */
fun Birthday.birthMonthDay(): MonthDay = MonthDay.from(birthLocalDate())
