package com.glimmer.app.data

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

// FEAT-05: when the birth year isn't known, dateOfBirth still needs *some* year — every other
// date read in the app (birthMonthDay, the countdown, the calendar grid) only ever looks at the
// month/day part of it, but Room needs a concrete Long to store. 2004 is an arbitrary leap year,
// chosen only so a 29 February birthday round-trips instead of getting silently coerced.
private const val UNKNOWN_YEAR_PLACEHOLDER = 2004

/**
 * The millis to store in [Birthday.dateOfBirth] for a date whose year isn't tracked — same
 * month/day as [pickedMillis] (however it was picked, year included), forced onto a fixed
 * placeholder year. Pair with `birthYear = null` when constructing the [Birthday].
 */
fun placeholderDateOfBirth(pickedMillis: Long): Long =
    MonthDay.from(Instant.ofEpochMilli(pickedMillis).atZone(ZoneOffset.UTC).toLocalDate())
        .atYear(UNKNOWN_YEAR_PLACEHOLDER)
        .atStartOfDay(ZoneOffset.UTC)
        .toInstant()
        .toEpochMilli()

/** The calendar year encoded in a UTC-midnight millis value (see [Birthday.dateOfBirth]'s convention). */
fun yearOf(dateMillis: Long): Int = Instant.ofEpochMilli(dateMillis).atZone(ZoneOffset.UTC).toLocalDate().year
