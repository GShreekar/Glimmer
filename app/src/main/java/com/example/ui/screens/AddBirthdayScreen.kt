package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.Cake
import androidx.compose.material.icons.filled.Call
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
import androidx.compose.ui.text.input.KeyboardType
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
fun AddBirthdayScreen(
    viewModel: GlimmerViewModel,
    onNavigateBack: () -> Unit
) {
    var name by remember { mutableStateOf("") }
    var notificationsEnabled by remember { mutableStateOf(true) }
    var notes by remember { mutableStateOf("") }
    var phoneNumber by remember { mutableStateOf("") }

    var dateOfBirth by remember { mutableStateOf<Long?>(null) }
    var showDatePicker by remember { mutableStateOf(false) }
    val datePickerState = rememberDatePickerState()

    var relationship by remember { mutableStateOf("") }
    var showRelationshipDropdown by remember { mutableStateOf(false) }
    val relationships = listOf("Family", "Friend", "Partner", "Colleague", "Other")

    // Seeded from the app-wide default set in Notifications settings (falls back to whatever
    // that StateFlow's own initial value is if DataStore's first read hasn't landed yet).
    var reminderType by remember { mutableStateOf(viewModel.defaultReminderTime.value) }
    var showReminderDropdown by remember { mutableStateOf(false) }
    val reminderOptions = listOf("On the day", "1 day before", "3 days before", "1 week before")

    // Form validation state
    var nameError by remember { mutableStateOf(false) }
    var dateError by remember { mutableStateOf(false) }
    var relationshipError by remember { mutableStateOf(false) }

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
                        "Add Birthday",
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
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = MaterialTheme.colorScheme.onSurface
                        )
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

            // Avatar placeholder
            Box(
                modifier = Modifier
                    .size(96.dp)
                    .neumorphic(isSunken = true, cornerRadius = 48.dp, shapeBackgroundColor = MaterialTheme.colorScheme.surface),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.AddPhotoAlternate,
                    contentDescription = "Add Photo",
                    modifier = Modifier.size(40.dp),
                    tint = MaterialTheme.colorScheme.outline
                )
            }

            Spacer(modifier = Modifier.height(12.dp))
            Text("New Birthday", style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.primary)
            Text("Who are we celebrating?", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)

            Spacer(modifier = Modifier.height(32.dp))

            // ── Form Fields ─────────────────────────────────────────────────
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

                FormEntry(label = "Relationship", error = if (relationshipError) "Please select a relationship" else null) {
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
                                        relationshipError = false
                                        showRelationshipDropdown = false
                                    }
                                )
                            }
                        }
                    }
                }

                // Phone number — powers the Message/Call quick actions on the Detail screen;
                // without it those buttons can only open an empty composer/dialer.
                FormEntry(label = "Phone Number (optional)") {
                    NeumorphicTextField(
                        value = phoneNumber,
                        onValueChange = { phoneNumber = it },
                        placeholder = "e.g., (555) 123-4567",
                        icon = Icons.Default.Call,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone)
                    )
                }

                // Notes field
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
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(4.dp)
                    .neumorphic(isSunken = true, cornerRadius = 2.dp)
            )
            Spacer(modifier = Modifier.height(24.dp))

            // ── Reminders ───────────────────────────────────────────────────
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
                            modifier = Modifier
                                .size(40.dp)
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
                    NeumorphicSwitch(
                        checked = notificationsEnabled,
                        onCheckedChange = { notificationsEnabled = it }
                    )
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
                                    onClick = {
                                        reminderType = option
                                        showReminderDropdown = false
                                    }
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            NeumorphicButton(
                onClick = {
                    // Validate
                    nameError = name.isBlank()
                    dateError = dateOfBirth == null
                    relationshipError = relationship.isBlank()

                    if (!nameError && !dateError && !relationshipError) {
                        // Notification permission is primed once at app launch (MainActivity) and
                        // surfaced by a banner on Home if it's missing — not requested here, since
                        // this button navigates back in the same click, which would tear down the
                        // screen (and its permission launcher) before any system dialog's result
                        // could come back.
                        viewModel.insertBirthday(
                            Birthday(
                                name = name.trim(),
                                dateOfBirth = dateOfBirth!!,
                                relationship = relationship,
                                reminderEnabled = notificationsEnabled,
                                reminderTime = if (notificationsEnabled) reminderType else "",
                                notes = notes.trim().ifBlank { null },
                                phoneNumber = phoneNumber.trim().ifBlank { null }
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
                    Text("Save Reminder", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.headlineMedium)
                }
            }

            Spacer(modifier = Modifier.height(100.dp))
        }
    }
}

@Composable
fun FormEntry(label: String, error: String? = null, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label, style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(start = 4.dp))
        content()
        AnimatedVisibility(visible = error != null) {
            Text(
                text = error ?: "",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(start = 4.dp)
            )
        }
    }
}
