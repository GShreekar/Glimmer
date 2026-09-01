package com.example.ui.screens

import java.util.Calendar
import java.util.Locale
import java.text.SimpleDateFormat
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Cake
import androidx.compose.material.icons.filled.NotificationsNone
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.example.data.Birthday
import com.example.ui.components.NeumorphicButton
import com.example.ui.components.NeumorphicIconButton
import com.example.ui.components.neumorphic
import com.example.viewmodel.GlimmerViewModel
import com.example.viewmodel.calculateAge
import com.example.viewmodel.daysUntilBirthday

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel: GlimmerViewModel,
    onNavigateToDetail: (Int) -> Unit,
    onNavigateToAdd: () -> Unit,
    onNavigateToNotifications: () -> Unit,
    onNavigateToProfile: () -> Unit
) {
    val filteredBirthdays by viewModel.filteredBirthdays.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()

    // Split into today's birthdays and upcoming
    val todayBirthdays = filteredBirthdays.filter { daysUntilBirthday(it.dateOfBirth) == 0 }
    val upcomingBirthdays = filteredBirthdays.filter { daysUntilBirthday(it.dateOfBirth) > 0 }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "Glimmer",
                        style = MaterialTheme.typography.headlineMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                },
                actions = {
                    NeumorphicIconButton(
                        onClick = onNavigateToNotifications,
                        modifier = Modifier
                            .padding(end = 8.dp)
                            .size(36.dp),
                        cornerRadius = 18.dp,
                    ) {
                        Icon(
                            Icons.Default.NotificationsNone,
                            contentDescription = "Notifications",
                            tint = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    NeumorphicIconButton(
                        onClick = onNavigateToProfile,
                        modifier = Modifier
                            .padding(end = 16.dp)
                            .size(36.dp),
                        cornerRadius = 18.dp,
                        shapeBackgroundColor = MaterialTheme.colorScheme.primary
                    ) {
                        Icon(
                            Icons.Default.Person,
                            contentDescription = "Profile",
                            tint = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        floatingActionButton = {
            NeumorphicIconButton(
                onClick = onNavigateToAdd,
                modifier = Modifier.size(64.dp),
                cornerRadius = 32.dp,
                shapeBackgroundColor = MaterialTheme.colorScheme.primary
            ) {
                Icon(
                    Icons.Default.Add,
                    contentDescription = "Add Birthday",
                    tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(28.dp)
                )
            }
        },
        containerColor = MaterialTheme.colorScheme.surface
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            item { Spacer(modifier = Modifier.height(8.dp)) }

            // ── Search Bar ──────────────────────────────────────────────────
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .neumorphic(isSunken = true, cornerRadius = 26.dp, shapeBackgroundColor = MaterialTheme.colorScheme.surface)
                        .padding(horizontal = 20.dp),
                    contentAlignment = Alignment.CenterStart
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Default.Search, contentDescription = null, tint = MaterialTheme.colorScheme.outline)
                        Spacer(modifier = Modifier.width(12.dp))
                        androidx.compose.foundation.text.BasicTextField(
                            value = searchQuery,
                            onValueChange = { viewModel.searchQuery.value = it },
                            textStyle = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.onSurface),
                            singleLine = true,
                            modifier = Modifier.weight(1f),
                            decorationBox = { inner ->
                                if (searchQuery.isEmpty()) {
                                    Text("Search friends, family...", color = MaterialTheme.colorScheme.outlineVariant, style = MaterialTheme.typography.bodyMedium)
                                }
                                inner()
                            }
                        )
                    }
                }
            }

            // ── Today's Birthdays ─────────────────────────────────────────
            if (todayBirthdays.isNotEmpty()) {
                item {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Cake, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            "Today 🎉",
                            style = MaterialTheme.typography.headlineMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }

                items(todayBirthdays) { birthday ->
                    TodayBirthdayCard(birthday = birthday, onClick = { onNavigateToDetail(birthday.id) })
                }
            }

            // ── Upcoming Birthdays ────────────────────────────────────────
            item {
                Text(
                    if (upcomingBirthdays.isEmpty() && todayBirthdays.isEmpty()) "No Birthdays Yet" else "Upcoming",
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (upcomingBirthdays.isEmpty() && todayBirthdays.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .neumorphic(cornerRadius = 20.dp, shapeBackgroundColor = MaterialTheme.colorScheme.surface)
                            .padding(32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Icon(Icons.Default.Cake, contentDescription = null, tint = MaterialTheme.colorScheme.outline, modifier = Modifier.size(48.dp))
                            Text("No birthdays added yet", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.outline)
                            Text("Tap + to add your first one!", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.outlineVariant)
                        }
                    }
                }
            } else {
                items(upcomingBirthdays) { birthday ->
                    UpcomingBirthdayCard(birthday = birthday, onClick = { onNavigateToDetail(birthday.id) })
                }
            }

            item { Spacer(modifier = Modifier.height(96.dp)) }
        }
    }
}

@Composable
private fun TodayBirthdayCard(birthday: Birthday, onClick: () -> Unit) {
    val age = calculateAge(birthday.dateOfBirth)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .neumorphic(cornerRadius = 20.dp, shapeBackgroundColor = MaterialTheme.colorScheme.surface)
            .clickable(onClick = onClick)
            .padding(20.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .neumorphic(cornerRadius = 32.dp, shapeBackgroundColor = MaterialTheme.colorScheme.surface)
                        .padding(4.dp)
                ) {
                    Box(modifier = Modifier.fillMaxSize().clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer))
                }
                Spacer(modifier = Modifier.width(16.dp))
                Column {
                    Text(birthday.name, style = MaterialTheme.typography.headlineMedium)
                    Text("Turning $age today! 🎂", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.secondary)
                    Text(birthday.relationship, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.outline)
                }
            }

            NeumorphicButton(
                onClick = onClick,
                modifier = Modifier.fillMaxWidth().height(48.dp),
                cornerRadius = 12.dp
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.AutoMirrored.Filled.Send, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("View & Send a Wish", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelLarge)
                }
            }
        }
    }
}

@Composable
private fun UpcomingBirthdayCard(birthday: Birthday, onClick: () -> Unit) {
    val monthFormat = remember { SimpleDateFormat("MMM", Locale.getDefault()) }
    val bCal = Calendar.getInstance(java.util.TimeZone.getTimeZone("UTC")).apply { timeInMillis = birthday.dateOfBirth }
    val dayStr = "${monthFormat.format(bCal.time)} ${bCal.get(Calendar.DAY_OF_MONTH)}"
    val daysLeft = daysUntilBirthday(birthday.dateOfBirth)
    val daysLabel = when (daysLeft) {
        0 -> "Today!"
        1 -> "Tomorrow"
        else -> "$daysLeft days"
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .neumorphic(cornerRadius = 16.dp, shapeBackgroundColor = MaterialTheme.colorScheme.surface)
            .clickable(onClick = onClick)
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .neumorphic(isSunken = true, cornerRadius = 24.dp, shapeBackgroundColor = MaterialTheme.colorScheme.surface)
                        .padding(4.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Box(modifier = Modifier.fillMaxSize().clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer))
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(birthday.name, style = MaterialTheme.typography.labelLarge)
                    Text("$dayStr · ${birthday.relationship}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                }
            }
            // Days-away badge
            Box(
                modifier = Modifier
                    .neumorphic(cornerRadius = 12.dp, shapeBackgroundColor = MaterialTheme.colorScheme.primaryContainer)
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(daysLabel, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
            }
        }
    }
}
