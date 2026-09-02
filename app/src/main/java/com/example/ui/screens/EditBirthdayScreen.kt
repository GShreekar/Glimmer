package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Cake
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.data.Birthday
import com.example.ui.components.NeumorphicButton
import com.example.ui.components.NeumorphicIconButton
import com.example.ui.components.NeumorphicSwitch
import com.example.ui.components.NeumorphicTextField
import com.example.ui.components.neumorphic
import com.example.viewmodel.GlimmerViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditBirthdayScreen(
    id: Int,
    viewModel: GlimmerViewModel,
    onNavigateBack: () -> Unit
) {
    val birthdayState by remember(id) { viewModel.getBirthdayById(id) }.collectAsState()
    val birthday = birthdayState

    // Wait until birthday is loaded
    if (birthday == null) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
        }
        return
    }

    var name by remember(birthday) { mutableStateOf(birthday.name) }
    var notificationsEnabled by remember(birthday) { mutableStateOf(birthday.reminderEnabled) }
    var notes by remember(birthday) { mutableStateOf(birthday.notes ?: "") }

    var dateOfBirth by remember(birthday) { mutableStateOf<Long?>(birthday.dateOfBirth) }
    var showDatePicker by remember { mutableStateOf(false) }
    val datePickerState = rememberDatePickerState(initialSelectedDateMillis = birthday.dateOfBirth)

    var relationship by remember(birthday) { mutableStateOf(birthday.relationship) }
    var showRelationshipDropdown by remember { mutableStateOf(false) }
    val relationships = listOf("Family", "Friend", "Partner", "Colleague", "Other")

    var reminderType by remember(birthday) { mutableStateOf(birthday.reminderTime.ifBlank { "1 day before" }) }
    var showReminderDropdown by remember { mutableStateOf(false) }
    val reminderOptions = listOf("On the day", "1 day before", "3 days before", "1 week before")

    var nameError by remember { mutableStateOf(false) }
    var dateError by remember { mutableStateOf(false) }

    val dateFormatter = remember {
        SimpleDateFormat("MMM dd, yyyy", Locale.getDefault()).apply {
            timeZone = java.util.TimeZone.getTimeZone("UTC")
        }
    }

    if (showDatePicker) {
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    showDatePicker = false
                    dateOfBirth = datePickerState.selectedDateMillis
                    dateError = false
                }) { Text("OK") }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) { Text("Cancel") }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "Edit Birthday",
                        style = MaterialTheme.typography.headlineMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                },
                navigationIcon = {
                    NeumorphicIconButton(
                        onClick = onNavigateBack,
                        modifier = Modifier.padding(start = 12.dp).size(40.dp),
                        cornerRadius = 20.dp
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = MaterialTheme.colorScheme.onSurface)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
            )
        },
        containerColor = MaterialTheme.colorScheme.surface
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 24.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(20.dp))
            Text("Update Birthday", style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.primary)
            Text("Edit ${birthday.name}'s details", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)

            Spacer(modifier = Modifier.height(32.dp))

            Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(20.dp)) {

                FormEntry(label = "Name", error = if (nameError) "Name is required" else null) {
                    NeumorphicTextField(
                        value = name,
                        onValueChange = { name = it; nameError = false },
                        placeholder = "e.g., Alex Johnson",
                        icon = Icons.Default.Person
                    )
                }

                FormEntry(label = "Date of Birth", error = if (dateError) "Please select a date" else null) {
                    NeumorphicTextField(
                        value = if (dateOfBirth != null) dateFormatter.format(Date(dateOfBirth!!)) else "",
                        onValueChange = {},
                        placeholder = "Select Date",
                        icon = Icons.Default.Cake,
                        readOnly = true,
                        onClick = { showDatePicker = true }
                    )
                }

                FormEntry(label = "Relationship") {
                    ExposedDropdownMenuBox(
                        expanded = showRelationshipDropdown,
                        onExpandedChange = { showRelationshipDropdown = it }
                    ) {
                        NeumorphicTextField(
                            value = relationship,
                            onValueChange = {},
                            placeholder = "Select category",
                            icon = Icons.Default.Group,
                            trailingIcon = if (showRelationshipDropdown) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                            readOnly = true,
                            onClick = { showRelationshipDropdown = true },
                            modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable)
                        )
                        ExposedDropdownMenu(
                            expanded = showRelationshipDropdown,
                            onDismissRequest = { showRelationshipDropdown = false }
                        ) {
                            relationships.forEach { rel ->
                                DropdownMenuItem(
                                    text = { Text(rel) },
                                    onClick = {
                                        relationship = rel
                                        showRelationshipDropdown = false
                                    }
                                )
                            }
                        }
                    }
                }

                FormEntry(label = "Notes (optional)") {
                    NeumorphicTextField(
                        value = notes,
                        onValueChange = { notes = it },
                        placeholder = "Gift ideas, preferences...",
                        icon = Icons.Default.Person
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
            Box(modifier = Modifier.fillMaxWidth().height(4.dp).neumorphic(isSunken = true, cornerRadius = 2.dp))
            Spacer(modifier = Modifier.height(24.dp))

            Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(20.dp)) {
                Text("Reminders", style = MaterialTheme.typography.headlineMedium)

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .neumorphic(cornerRadius = 12.dp, shapeBackgroundColor = MaterialTheme.colorScheme.surface)
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier.size(40.dp)
                                .neumorphic(isSunken = true, cornerRadius = 20.dp, shapeBackgroundColor = MaterialTheme.colorScheme.surface),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.Notifications, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text("Send Notification", style = MaterialTheme.typography.bodyMedium)
                            Text("Get reminded in advance", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                        }
                    }
                    NeumorphicSwitch(checked = notificationsEnabled, onCheckedChange = { notificationsEnabled = it })
                }

                AnimatedVisibility(visible = notificationsEnabled) {
                    ExposedDropdownMenuBox(
                        expanded = showReminderDropdown,
                        onExpandedChange = { showReminderDropdown = it }
                    ) {
                        FormEntry(label = "Remind me") {
                            NeumorphicTextField(
                                value = reminderType,
                                onValueChange = {},
                                placeholder = "Select time",
                                icon = Icons.Default.Schedule,
                                trailingIcon = if (showReminderDropdown) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                readOnly = true,
                                onClick = { showReminderDropdown = true },
                                modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable)
                            )
                        }
                        ExposedDropdownMenu(
                            expanded = showReminderDropdown,
                            onDismissRequest = { showReminderDropdown = false }
                        ) {
                            reminderOptions.forEach { option ->
                                DropdownMenuItem(
                                    text = { Text(option) },
                                    onClick = { reminderType = option; showReminderDropdown = false }
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            NeumorphicButton(
                onClick = {
                    nameError = name.isBlank()
                    dateError = dateOfBirth == null
                    if (!nameError && !dateError) {
                        // See AddBirthdayScreen: notification permission is primed at app launch
                        // and surfaced by a Home banner, not requested from a button that
                        // navigates back (and tears this screen down) in the same click.
                        viewModel.updateBirthday(
                            birthday.copy(
                                name = name.trim(),
                                dateOfBirth = dateOfBirth!!,
                                relationship = relationship,
                                reminderEnabled = notificationsEnabled,
                                reminderTime = if (notificationsEnabled) reminderType else "",
                                notes = notes.trim().ifBlank { null }
                            )
                        )
                        onNavigateBack()
                    }
                },
                modifier = Modifier.fillMaxWidth().height(56.dp),
                cornerRadius = 12.dp
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Favorite, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Update Reminder", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.headlineMedium)
                }
            }

            Spacer(modifier = Modifier.height(100.dp))
        }
    }
}
