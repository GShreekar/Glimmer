package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cake
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.data.Birthday
import com.example.ui.components.NeumorphicIconButton
import com.example.ui.components.neumorphic
import com.example.viewmodel.GlimmerViewModel
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CalendarScreen(
    viewModel: GlimmerViewModel,
    onNavigateToDetail: (Int) -> Unit
) {
    val allBirthdays by viewModel.allBirthdays.collectAsState()

    var currentCalendar by remember { mutableStateOf(Calendar.getInstance()) }
    var selectedDay by remember { mutableStateOf<Int?>(null) }
    val monthFormat = remember { SimpleDateFormat("MMMM", Locale.getDefault()) }
    val yearFormat = remember { SimpleDateFormat("yyyy", Locale.getDefault()) }

    val monthName = monthFormat.format(currentCalendar.time)
    val yearStr = yearFormat.format(currentCalendar.time)

    val daysInMonth = currentCalendar.getActualMaximum(Calendar.DAY_OF_MONTH)
    val tempCal = currentCalendar.clone() as Calendar
    tempCal.set(Calendar.DAY_OF_MONTH, 1)
    val startDayOfWeek = tempCal.get(Calendar.DAY_OF_WEEK) - 1

    val days = remember(currentCalendar) {
        val list = mutableListOf<String>()
        for (i in 0 until startDayOfWeek) list.add("")
        for (i in 1..daysInMonth) list.add(i.toString())
        list
    }

    // Map day-of-month -> list of birthdays (matches by MONTH and DAY, ignoring year)
    val birthdaysByDay = remember(allBirthdays, currentCalendar) {
        val map = mutableMapOf<Int, MutableList<Birthday>>()
        allBirthdays.forEach { birthday ->
            val bCal = Calendar.getInstance(java.util.TimeZone.getTimeZone("UTC")).apply {
                timeInMillis = birthday.dateOfBirth
            }
            // Compare month only (not year)
            if (bCal.get(Calendar.MONTH) == currentCalendar.get(Calendar.MONTH)) {
                val day = bCal.get(Calendar.DAY_OF_MONTH)
                map.getOrPut(day) { mutableListOf() }.add(birthday)
            }
        }
        map
    }

    // Birthdays to show in the list below calendar
    val listBirthdays = remember(birthdaysByDay, selectedDay) {
        if (selectedDay != null) {
            birthdaysByDay[selectedDay] ?: emptyList()
        } else {
            birthdaysByDay.entries.sortedBy { it.key }.flatMap { it.value }
        }
    }

    val today = Calendar.getInstance()
    val isCurrentMonth = today.get(Calendar.MONTH) == currentCalendar.get(Calendar.MONTH) &&
            today.get(Calendar.YEAR) == currentCalendar.get(Calendar.YEAR)

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "Calendar",
                        style = MaterialTheme.typography.headlineMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
            )
        },
        containerColor = MaterialTheme.colorScheme.surface
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                // ── Month Navigation ─────────────────────────────────────
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    NeumorphicIconButton(
                        onClick = {
                            val newCal = currentCalendar.clone() as Calendar
                            newCal.add(Calendar.MONTH, -1)
                            currentCalendar = newCal
                            selectedDay = null
                        },
                        modifier = Modifier.size(48.dp)
                    ) {
                        Icon(Icons.Default.ChevronLeft, contentDescription = "Previous", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }

                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(monthName, style = MaterialTheme.typography.headlineMedium)
                        Text(yearStr, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }

                    NeumorphicIconButton(
                        onClick = {
                            val newCal = currentCalendar.clone() as Calendar
                            newCal.add(Calendar.MONTH, 1)
                            currentCalendar = newCal
                            selectedDay = null
                        },
                        modifier = Modifier.size(48.dp)
                    ) {
                        Icon(Icons.Default.ChevronRight, contentDescription = "Next", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }

            item {
                // ── Calendar Grid ────────────────────────────────────────
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .neumorphic(isSunken = true, cornerRadius = 24.dp, shapeBackgroundColor = MaterialTheme.colorScheme.surface)
                        .padding(20.dp)
                ) {
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(7),
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 400.dp),
                        contentPadding = PaddingValues(vertical = 8.dp),
                        userScrollEnabled = false
                    ) {
                        val weekDays = listOf("Su", "Mo", "Tu", "We", "Th", "Fr", "Sa")
                        items(weekDays) { day ->
                            Text(
                                text = day,
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.padding(bottom = 12.dp)
                            )
                        }
                        items(days) { dayStr ->
                            if (dayStr.isEmpty()) {
                                Box(modifier = Modifier.size(40.dp))
                            } else {
                                val dayInt = dayStr.toInt()
                                val hasBirthday = birthdaysByDay.containsKey(dayInt)
                                val isToday = isCurrentMonth && dayInt == today.get(Calendar.DAY_OF_MONTH)
                                val isSelected = selectedDay == dayInt
                                val interactionSource = remember { MutableInteractionSource() }
                                val isPressed by interactionSource.collectIsPressedAsState()

                                val cellModifier = when {
                                    isSelected -> Modifier.neumorphic(
                                        isSunken = true,
                                        cornerRadius = 20.dp,
                                        shapeBackgroundColor = MaterialTheme.colorScheme.primaryContainer
                                    )
                                    hasBirthday -> Modifier.neumorphic(
                                        isSunken = isPressed,
                                        cornerRadius = 20.dp,
                                        shapeBackgroundColor = MaterialTheme.colorScheme.surface
                                    )
                                    isPressed -> Modifier.neumorphic(
                                        isSunken = true,
                                        cornerRadius = 20.dp,
                                        shapeBackgroundColor = MaterialTheme.colorScheme.surface
                                    )
                                    else -> Modifier
                                }

                                Box(
                                    modifier = Modifier
                                        .padding(3.dp)
                                        .size(40.dp)
                                        .then(cellModifier)
                                        .clip(CircleShape)
                                        .clickable(
                                            interactionSource = interactionSource,
                                            indication = null,
                                            onClick = {
                                                selectedDay = if (selectedDay == dayInt) null else dayInt
                                            }
                                        ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Column(
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.Center
                                    ) {
                                        Text(
                                            text = dayStr,
                                            color = when {
                                                isToday -> MaterialTheme.colorScheme.secondary
                                                hasBirthday || isSelected -> MaterialTheme.colorScheme.primary
                                                else -> MaterialTheme.colorScheme.onSurface
                                            },
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = if (isToday || hasBirthday) FontWeight.Bold else null
                                        )
                                        if (hasBirthday) {
                                            Spacer(modifier = Modifier.height(2.dp))
                                            Box(
                                                modifier = Modifier
                                                    .size(4.dp)
                                                    .background(MaterialTheme.colorScheme.primary, CircleShape)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // ── Birthday List ─────────────────────────────────────────────
            item {
                val headerText = when {
                    selectedDay != null -> "Birthdays on ${monthName.take(3)} $selectedDay"
                    listBirthdays.isEmpty() -> "No birthdays in $monthName"
                    else -> "Birthdays in $monthName (${listBirthdays.size})"
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(Icons.Default.Cake, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                    Text(
                        headerText,
                        style = MaterialTheme.typography.headlineMedium,
                        color = if (listBirthdays.isEmpty()) MaterialTheme.colorScheme.outline else MaterialTheme.colorScheme.onSurface
                    )
                }
            }

            if (listBirthdays.isEmpty()) {
                item {
                    if (selectedDay != null) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .neumorphic(cornerRadius = 16.dp, shapeBackgroundColor = MaterialTheme.colorScheme.surface)
                                .padding(20.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                "No birthdays on this day",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.outline
                            )
                        }
                    }
                }
            } else {
                items(listBirthdays) { birthday ->
                    CalendarBirthdayItem(birthday = birthday, onClick = { onNavigateToDetail(birthday.id) })
                }
            }

            item { Spacer(modifier = Modifier.height(96.dp)) }
        }
    }
}

@Composable
private fun CalendarBirthdayItem(birthday: Birthday, onClick: () -> Unit) {
    val bCal = Calendar.getInstance(java.util.TimeZone.getTimeZone("UTC")).apply { timeInMillis = birthday.dateOfBirth }
    val dayOfMonth = bCal.get(Calendar.DAY_OF_MONTH)
    val monthAbbr = SimpleDateFormat("MMM", Locale.getDefault()).format(bCal.time)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .neumorphic(cornerRadius = 16.dp, shapeBackgroundColor = MaterialTheme.colorScheme.surface)
            .clickable(onClick = onClick)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            // Date badge
            Box(
                modifier = Modifier
                    .size(52.dp)
                    .neumorphic(cornerRadius = 12.dp, shapeBackgroundColor = MaterialTheme.colorScheme.primaryContainer)
                    .padding(4.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(monthAbbr, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                    Text("$dayOfMonth", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                }
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text(birthday.name, style = MaterialTheme.typography.labelLarge)
                Text(birthday.relationship, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
            }
        }
        Icon(Icons.Default.Cake, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
    }
}
