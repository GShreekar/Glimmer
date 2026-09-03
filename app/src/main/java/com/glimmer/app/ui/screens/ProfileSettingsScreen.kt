package com.glimmer.app.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Cake
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import com.glimmer.app.R
import com.glimmer.app.ui.components.NeumorphicButton
import com.glimmer.app.ui.components.NeumorphicIconButton
import com.glimmer.app.ui.components.NeumorphicSnackbarHost
import com.glimmer.app.ui.components.NeumorphicTextField
import com.glimmer.app.ui.components.neumorphic
import com.glimmer.app.ui.components.rememberBirthDatePickerState
import com.glimmer.app.viewmodel.GlimmerViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileSettingsScreen(
    viewModel: GlimmerViewModel,
    onNavigateBack: () -> Unit
) {
    // Seeded once from the ViewModel's current value rather than collected live — this screen
    // owns the draft while it's being edited, and writes it back to the shared StateFlow (which
    // SettingsScreen observes) only on Save.
    var displayName by remember { mutableStateOf(viewModel.profileName.value) }
    var email by remember { mutableStateOf(viewModel.profileEmail.value) }
    // Profile info only — a stored date, not a Birthday row: it doesn't appear on Home/Calendar
    // and never gets a reminder of its own (you don't need Glimmer to remind you of your own
    // birthday). Reuses the same date-picker component and UTC-midnight-millis convention as
    // Add/Edit purely for consistency, not because this participates in any of that logic.
    var myBirthday by remember { mutableStateOf(viewModel.profileBirthday.value) }
    var showDatePicker by remember { mutableStateOf(false) }
    val datePickerState = rememberBirthDatePickerState(initialSelectedDateMillis = myBirthday)
    val dateFormatter = remember {
        SimpleDateFormat("MMM dd, yyyy", Locale.getDefault()).apply { timeZone = TimeZone.getTimeZone("UTC") }
    }
    var savedSnackbar by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }
    // stringResource() can't be called from inside LaunchedEffect (it runs as a coroutine, not
    // during composition), so the message is resolved here and captured by the effect below.
    val profileSavedMessage = stringResource(R.string.profile_saved_snackbar)

    LaunchedEffect(savedSnackbar) {
        if (savedSnackbar) {
            snackbarHostState.showSnackbar(profileSavedMessage)
            savedSnackbar = false
        }
    }

    if (showDatePicker) {
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    showDatePicker = false
                    myBirthday = datePickerState.selectedDateMillis
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
                    Text(stringResource(R.string.profile_title), style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.primary)
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
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.profile_cd_back), tint = MaterialTheme.colorScheme.onSurface)
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
            Spacer(modifier = Modifier.height(24.dp))

            // Avatar
            Box(
                modifier = Modifier
                    .size(100.dp)
                    .neumorphic(cornerRadius = 50.dp, shapeBackgroundColor = MaterialTheme.colorScheme.surface),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.Person,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(52.dp)
                )
            }

            Spacer(modifier = Modifier.height(8.dp))
            Text(
                displayName.ifBlank { stringResource(R.string.settings_default_name) },
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                email.ifBlank { stringResource(R.string.settings_default_email) },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(32.dp))

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .neumorphic(cornerRadius = 20.dp, shapeBackgroundColor = MaterialTheme.colorScheme.surface)
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                Text(stringResource(R.string.profile_personal_details), style = MaterialTheme.typography.headlineMedium)

                FormEntry(label = stringResource(R.string.profile_label_display_name)) {
                    NeumorphicTextField(
                        value = displayName,
                        onValueChange = { displayName = it },
                        placeholder = stringResource(R.string.profile_placeholder_name),
                        icon = Icons.Default.Person
                    )
                }

                FormEntry(label = stringResource(R.string.profile_label_email)) {
                    NeumorphicTextField(
                        value = email,
                        onValueChange = { email = it },
                        placeholder = stringResource(R.string.profile_placeholder_email),
                        icon = Icons.Default.Email,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email)
                    )
                }

                FormEntry(label = stringResource(R.string.profile_label_birthday)) {
                    NeumorphicTextField(
                        value = myBirthday?.let { dateFormatter.format(Date(it)) } ?: "",
                        onValueChange = {},
                        placeholder = stringResource(R.string.profile_placeholder_birthday),
                        icon = Icons.Default.Cake,
                        readOnly = true,
                        onClick = { showDatePicker = true }
                    )
                    if (myBirthday != null) {
                        TextButton(onClick = { myBirthday = null }) {
                            Text(stringResource(R.string.profile_clear_birthday), style = MaterialTheme.typography.labelMedium)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            NeumorphicButton(
                onClick = {
                    viewModel.setProfileName(displayName.trim())
                    viewModel.setProfileEmail(email.trim())
                    viewModel.setProfileBirthday(myBirthday)
                    savedSnackbar = true
                },
                modifier = Modifier.fillMaxWidth().height(56.dp),
                cornerRadius = 12.dp
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Save, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(stringResource(R.string.profile_save_button), color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.headlineMedium)
                }
            }

            Spacer(modifier = Modifier.height(80.dp))
        }
    }
}
