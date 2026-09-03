package com.glimmer.app.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.glimmer.app.R
import com.glimmer.app.data.PhotoStorage
import com.glimmer.app.data.placeholderDateOfBirth
import com.glimmer.app.data.yearOf
import com.glimmer.app.ui.components.NeumorphicButton
import com.glimmer.app.ui.components.NeumorphicIconButton
import com.glimmer.app.ui.components.NeumorphicSnackbarHost
import com.glimmer.app.ui.components.NeumorphicSwitch
import com.glimmer.app.ui.components.NeumorphicTextField
import com.glimmer.app.ui.components.neumorphic
import com.glimmer.app.ui.components.rememberBirthDatePickerState
import com.glimmer.app.ui.components.rememberContactPickerLauncher
import com.glimmer.app.viewmodel.GlimmerViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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

    // FEAT-04: reminders load as a second, independent query — wait for its first real emission
    // too (initialValue = null, not emptyList()) so the offset chips below aren't seeded from a
    // premature "no reminders" snapshot that hasn't actually answered yet.
    val remindersState by remember(birthday.id) { viewModel.getRemindersForBirthday(birthday.id) }.collectAsState()
    val loadedReminders = remindersState
    if (loadedReminders == null) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
        }
        return
    }

    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    // Keyed on birthday.id, not the birthday object itself: any background re-emission of the
    // same row from Room (e.g. after a re-arm elsewhere) produces a new Birthday instance with
    // the same id, and remember(birthday) would treat that as a reason to re-seed every field —
    // silently discarding whatever the user had typed but not yet saved.
    var name by remember(birthday.id) { mutableStateOf(birthday.name) }
    var notificationsEnabled by remember(birthday.id) { mutableStateOf(birthday.reminderEnabled) }
    var notes by remember(birthday.id) { mutableStateOf(birthday.notes ?: "") }
    var phoneNumber by remember(birthday.id) { mutableStateOf(birthday.phoneNumber ?: "") }
    var contactLookupKey by remember(birthday.id) { mutableStateOf(birthday.contactLookupKey) }
    // See AddBirthdayScreen: the avatar is now tappable and actually saves what's picked. The raw
    // picker URIs only grant read access for this process's lifetime — exactly why a picked photo
    // used to vanish after the app was killed and reopened — so persistAndReplacePhoto below
    // copies them into app-private storage (PhotoStorage) immediately. Unlike Add, this person's
    // CURRENT photo (originalPhotoUri) is still referenced by the DB row until Save actually
    // commits the change, so it must survive a re-pick-then-back-out-without-saving — only a
    // DRAFT from this same editing session (i.e. not originalPhotoUri) is safe to delete right
    // away when replaced again; the original itself is only cleaned up in the Save handler below.
    val originalPhotoUri = remember(birthday.id) { birthday.photoUri }
    var photoUri by remember(birthday.id) { mutableStateOf(birthday.photoUri) }
    suspend fun persistAndReplacePhoto(sourceUri: Uri) {
        val old = photoUri
        val persisted = withContext(Dispatchers.IO) {
            PhotoStorage.persistPickedPhoto(context, sourceUri)?.also {
                if (old != null && old != originalPhotoUri) PhotoStorage.deleteManagedPhoto(context, old)
            }
        }
        if (persisted != null) photoUri = persisted
    }

    val pickContact = rememberContactPickerLauncher { picked ->
        picked.phoneNumber?.let { phoneNumber = it }
        contactLookupKey = picked.lookupKey
        picked.photoUri?.let { rawUri ->
            coroutineScope.launch { persistAndReplacePhoto(Uri.parse(rawUri)) }
        }
    }

    var dateOfBirth by remember(birthday.id) { mutableStateOf<Long?>(birthday.dateOfBirth) }
    var showDatePicker by remember { mutableStateOf(false) }
    val datePickerState = rememberBirthDatePickerState(initialSelectedDateMillis = birthday.dateOfBirth)
    var yearUnknown by remember(birthday.id) { mutableStateOf(birthday.birthYear == null) }

    val photoPickerLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            coroutineScope.launch { persistAndReplacePhoto(uri) }
        }
    }

    var relationship by remember(birthday.id) { mutableStateOf(birthday.relationship) }

    // FEAT-04: seeded once from whatever this person's reminders actually were (remember(id),
    // same reasoning as every other field above — a background re-emission of `reminders` must
    // not clobber an in-progress edit).
    var selectedOffsets by remember(birthday.id) {
        mutableStateOf(loadedReminders.map { it.daysBefore }.toSet())
    }

    var nameError by remember { mutableStateOf(false) }
    var dateError by remember { mutableStateOf(false) }

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
                        stringResource(R.string.edit_title),
                        style = MaterialTheme.typography.headlineMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                },
                navigationIcon = {
                    NeumorphicIconButton(
                        onClick = onNavigateBack,
                        modifier = Modifier.padding(start = 12.dp).size(40.dp),
                        cornerRadius = 20.dp,
                        // BUG: see NeumorphicIconButton's doc — its default shadow gets clipped
                        // by the TopAppBar's own Surface at this size unless it's reduced.
                        elevation = 3.dp,
                        blur = 6.dp
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.edit_cd_back), tint = MaterialTheme.colorScheme.onSurface)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
            )
        },
        snackbarHost = { NeumorphicSnackbarHost(snackbarHostState) },
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
            Text(stringResource(R.string.edit_heading), style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.primary)
            Text(stringResource(R.string.edit_subheading, birthday.name), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)

            Spacer(modifier = Modifier.height(32.dp))

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

                // FEAT-08: free text now, with the old 5 presets as suggestion chips — see
                // AddBirthdayScreen.
                FormEntry(label = stringResource(R.string.field_label_relationship)) {
                    NeumorphicTextField(
                        value = relationship,
                        onValueChange = { relationship = it },
                        placeholder = stringResource(R.string.field_placeholder_select_category),
                        icon = Icons.Default.Group
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    RelationshipSuggestions(onSelect = { relationship = it })
                }

                FormEntry(label = stringResource(R.string.field_label_phone)) {
                    NeumorphicTextField(
                        value = phoneNumber,
                        onValueChange = { phoneNumber = it; contactLookupKey = null },
                        placeholder = stringResource(R.string.field_placeholder_phone),
                        icon = Icons.Default.Call,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    TextButton(onClick = pickContact) {
                        Icon(Icons.Default.Contacts, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(stringResource(R.string.field_pick_from_contacts))
                    }
                }

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
            Box(modifier = Modifier.fillMaxWidth().height(4.dp).neumorphic(isSunken = true, cornerRadius = 2.dp))
            Spacer(modifier = Modifier.height(24.dp))

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
                            modifier = Modifier.size(40.dp)
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
                    NeumorphicSwitch(checked = notificationsEnabled, onCheckedChange = { notificationsEnabled = it })
                }

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
                    nameError = name.isBlank()
                    dateError = dateOfBirth == null
                    if (!nameError && !dateError) {
                        val trimmedName = name.trim()
                        val pickedDate = dateOfBirth!!
                        val finalDateOfBirth = if (yearUnknown) placeholderDateOfBirth(pickedDate) else pickedDate
                        val finalBirthYear = if (yearUnknown) null else yearOf(pickedDate)
                        // See AddBirthdayScreen: notification permission is primed at app launch
                        // and surfaced by a Home banner, not requested from a button that
                        // navigates back (and tears this screen down) in the same click.
                        coroutineScope.launch {
                            // excludeId = birthday.id — otherwise saving without changing name or
                            // date would always flag itself as a duplicate of itself.
                            if (viewModel.isDuplicateBirthday(trimmedName, finalDateOfBirth, excludeId = birthday.id)) {
                                snackbarHostState.showSnackbar(String.format(duplicateMessageTemplate, trimmedName))
                                return@launch
                            }
                            viewModel.updateBirthday(
                                birthday.copy(
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
                            // Only now — with the change actually handed off to be saved — is it
                            // safe to remove the photo this person used to have. See
                            // persistAndReplacePhoto above for why this couldn't happen earlier.
                            if (originalPhotoUri != null && originalPhotoUri != photoUri) {
                                withContext(Dispatchers.IO) { PhotoStorage.deleteManagedPhoto(context, originalPhotoUri) }
                            }
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
                    Text(stringResource(R.string.edit_save_button), color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.headlineMedium)
                }
            }

            Spacer(modifier = Modifier.height(100.dp))
        }
    }
}
