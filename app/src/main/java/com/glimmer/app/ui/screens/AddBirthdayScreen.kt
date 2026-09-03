package com.glimmer.app.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.Cake
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Contacts
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.glimmer.app.R
import com.glimmer.app.data.Birthday
import com.glimmer.app.data.NotificationScheduler
import com.glimmer.app.data.placeholderDateOfBirth
import com.glimmer.app.data.yearOf
import com.glimmer.app.ui.components.NeumorphicButton
import com.glimmer.app.ui.components.NeumorphicIconButton
import com.glimmer.app.ui.components.NeumorphicSwitch
import com.glimmer.app.ui.components.NeumorphicTextField
import com.glimmer.app.ui.components.neumorphic
import com.glimmer.app.ui.components.rememberBirthDatePickerState
import com.glimmer.app.ui.components.rememberContactPickerLauncher
import com.glimmer.app.viewmodel.GlimmerViewModel
import kotlinx.coroutines.launch
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
    // FEAT-01: set when the phone number came from the contact picker below, so the person can
    // later be re-synced against the same Contacts row (a future "refresh from contacts").
    var contactLookupKey by remember { mutableStateOf<String?>(null) }
    val pickContact = rememberContactPickerLauncher { picked ->
        picked.phoneNumber?.let { phoneNumber = it }
        contactLookupKey = picked.lookupKey
        if (name.isBlank()) picked.name?.let { name = it }
    }

    var dateOfBirth by remember { mutableStateOf<Long?>(null) }
    var showDatePicker by remember { mutableStateOf(false) }
    val datePickerState = rememberBirthDatePickerState()
    // FEAT-05: many address books (Facebook imports especially) only ever record month/day.
    // The platform date picker still needs a full date, so the year the user happens to land on
    // is simply discarded at save time — see placeholderDateOfBirth.
    var yearUnknown by remember { mutableStateOf(false) }

    // The avatar was decorative — tapping it did nothing and photoUri was never populated.
    // PickVisualMedia (the system Photo Picker) needs no storage permission and its granted
    // read access to the returned URI persists for the app's lifetime, so the URI can just be
    // stored as-is.
    var photoUri by remember { mutableStateOf<String?>(null) }
    val photoPickerLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri -> if (uri != null) photoUri = uri.toString() }

    var relationship by remember { mutableStateOf("") }
    var showRelationshipDropdown by remember { mutableStateOf(false) }
    val relationships = listOf("Family", "Friend", "Partner", "Colleague", "Other")

    // FEAT-04: a person can have several reminders now — seeded from the app-wide default set in
    // Notifications settings, same starting point the old single-select dropdown used.
    var selectedOffsets by remember {
        mutableStateOf(setOf(NotificationScheduler.reminderTimeToOffset(viewModel.defaultReminderTime.value)))
    }

    // Form validation state
    var nameError by remember { mutableStateOf(false) }
    var dateError by remember { mutableStateOf(false) }
    var relationshipError by remember { mutableStateOf(false) }

    val coroutineScope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val duplicateMessageTemplate = stringResource(R.string.field_duplicate_snackbar)

    val dateFormatter = remember(yearUnknown) {
        val pattern = if (yearUnknown) "MMM dd" else "MMM dd, yyyy"
        SimpleDateFormat(pattern, Locale.getDefault()).apply {
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
                }) { Text(stringResource(R.string.common_ok)) }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) { Text(stringResource(R.string.common_cancel)) }
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
                        stringResource(R.string.add_title),
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
                            contentDescription = stringResource(R.string.add_cd_back),
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
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

            // Avatar — tap to pick a photo via the system Photo Picker
            Box(
                modifier = Modifier
                    .size(96.dp)
                    .neumorphic(isSunken = true, cornerRadius = 48.dp, shapeBackgroundColor = MaterialTheme.colorScheme.surface)
                    .clickable {
                        photoPickerLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                    },
                contentAlignment = Alignment.Center
            ) {
                if (photoUri != null) {
                    AsyncImage(
                        model = photoUri,
                        contentDescription = stringResource(R.string.add_cd_photo),
                        modifier = Modifier.fillMaxSize().clip(CircleShape),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Icon(
                        Icons.Default.AddPhotoAlternate,
                        contentDescription = stringResource(R.string.add_cd_photo),
                        modifier = Modifier.size(40.dp),
                        tint = MaterialTheme.colorScheme.outline
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
            Text(stringResource(R.string.add_heading), style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.primary)
            Text(stringResource(R.string.add_subheading), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)

            Spacer(modifier = Modifier.height(32.dp))

            // ── Form Fields ─────────────────────────────────────────────────
            Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(20.dp)) {

                FormEntry(label = stringResource(R.string.field_label_name), error = if (nameError) stringResource(R.string.field_error_name_required) else null) {
                    NeumorphicTextField(
                        value = name,
                        onValueChange = { name = it; nameError = false },
                        placeholder = stringResource(R.string.field_placeholder_name),
                        icon = Icons.Default.Person
                    )
                }

                FormEntry(label = stringResource(R.string.field_label_dob), error = if (dateError) stringResource(R.string.field_error_date_required) else null) {
                    NeumorphicTextField(
                        value = if (dateOfBirth != null) dateFormatter.format(Date(dateOfBirth!!)) else "",
                        onValueChange = {},
                        placeholder = stringResource(R.string.field_placeholder_select_date),
                        icon = Icons.Default.Cake,
                        readOnly = true,
                        onClick = { showDatePicker = true }
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = yearUnknown, onCheckedChange = { yearUnknown = it })
                        Text(
                            stringResource(R.string.field_unknown_year),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                FormEntry(label = stringResource(R.string.field_label_relationship), error = if (relationshipError) stringResource(R.string.field_error_relationship_required) else null) {
                    ExposedDropdownMenuBox(
                        expanded = showRelationshipDropdown,
                        onExpandedChange = { showRelationshipDropdown = it }
                    ) {
                        NeumorphicTextField(
                            value = relationship,
                            onValueChange = {},
                            placeholder = stringResource(R.string.field_placeholder_select_category),
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

                // Phone number — powers the Message/Call quick actions on the Detail screen and
                // notification; without it those buttons can only open an empty composer/dialer.
                FormEntry(label = stringResource(R.string.field_label_phone)) {
                    NeumorphicTextField(
                        value = phoneNumber,
                        onValueChange = { phoneNumber = it; contactLookupKey = null },
                        placeholder = stringResource(R.string.field_placeholder_phone),
                        icon = Icons.Default.Call,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    // FEAT-01: no permission needed — this opens the Contacts app's own picker
                    // and gets back only the one row chosen there.
                    TextButton(onClick = pickContact) {
                        Icon(Icons.Default.Contacts, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(stringResource(R.string.field_pick_from_contacts))
                    }
                }

                // Notes field
                FormEntry(label = stringResource(R.string.field_label_notes)) {
                    NeumorphicTextField(
                        value = notes,
                        onValueChange = { notes = it },
                        placeholder = stringResource(R.string.field_placeholder_notes),
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
                Text(stringResource(R.string.field_label_reminders), style = MaterialTheme.typography.headlineMedium)

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
                            Text(stringResource(R.string.field_send_notification), style = MaterialTheme.typography.bodyMedium)
                            Text(stringResource(R.string.field_send_notification_subtitle), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                        }
                    }
                    NeumorphicSwitch(
                        checked = notificationsEnabled,
                        onCheckedChange = { notificationsEnabled = it }
                    )
                }

                // FEAT-04: several offsets can be selected at once now — "a week before to shop,
                // the day before as a nudge" was previously mutually exclusive.
                AnimatedVisibility(visible = notificationsEnabled) {
                    FormEntry(label = stringResource(R.string.field_label_remind_me)) {
                        ReminderOffsetSelector(
                            selected = selectedOffsets,
                            onToggle = { days ->
                                selectedOffsets = if (days in selectedOffsets) {
                                    selectedOffsets - days
                                } else {
                                    selectedOffsets + days
                                }
                            }
                        )
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
                        val trimmedName = name.trim()
                        val pickedDate = dateOfBirth!!
                        val finalDateOfBirth = if (yearUnknown) placeholderDateOfBirth(pickedDate) else pickedDate
                        val finalBirthYear = if (yearUnknown) null else yearOf(pickedDate)
                        // Notification permission is primed once at app launch (MainActivity) and
                        // surfaced by a banner on Home if it's missing — not requested here, since
                        // this button navigates back in the same click, which would tear down the
                        // screen (and its permission launcher) before any system dialog's result
                        // could come back.
                        coroutineScope.launch {
                            if (viewModel.isDuplicateBirthday(trimmedName, finalDateOfBirth)) {
                                snackbarHostState.showSnackbar(String.format(duplicateMessageTemplate, trimmedName))
                                return@launch
                            }
                            viewModel.insertBirthday(
                                Birthday(
                                    name = trimmedName,
                                    dateOfBirth = finalDateOfBirth,
                                    birthYear = finalBirthYear,
                                    relationship = relationship,
                                    reminderEnabled = notificationsEnabled,
                                    notes = notes.trim().ifBlank { null },
                                    phoneNumber = phoneNumber.trim().ifBlank { null },
                                    photoUri = photoUri,
                                    contactLookupKey = contactLookupKey
                                ),
                                reminderOffsets = selectedOffsets
                            )
                            onNavigateBack()
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth().height(56.dp),
                cornerRadius = 12.dp
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Favorite, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(stringResource(R.string.add_save_button), color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.headlineMedium)
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

/** FEAT-04: the fixed set of reminder offsets, multi-selectable via chips. */
@Composable
fun ReminderOffsetSelector(selected: Set<Int>, onToggle: (Int) -> Unit) {
    val options = listOf(
        0 to stringResource(R.string.reminder_offset_on_day),
        1 to stringResource(R.string.reminder_offset_1_day),
        3 to stringResource(R.string.reminder_offset_3_days),
        7 to stringResource(R.string.reminder_offset_1_week)
    )
    Row(
        modifier = Modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        options.forEach { (days, label) ->
            FilterChip(
                selected = days in selected,
                onClick = { onToggle(days) },
                label = { Text(label) }
            )
        }
    }
}
