package com.glimmer.app.ui.screens

import java.time.format.TextStyle
import java.util.Locale
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Cake
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.NotificationsNone
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.glimmer.app.R
import com.glimmer.app.data.birthMonthDay
import com.glimmer.app.ui.components.BirthdayAvatar
import com.glimmer.app.ui.components.NeumorphicButton
import com.glimmer.app.ui.components.NeumorphicIconButton
import com.glimmer.app.ui.components.neumorphic
import com.glimmer.app.ui.components.rememberExactAlarmPermissionState
import com.glimmer.app.ui.components.rememberNotificationsPermissionState
import com.glimmer.app.viewmodel.BirthdayUi
import com.glimmer.app.viewmodel.GlimmerViewModel
import com.glimmer.app.viewmodel.UiEvent

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel: GlimmerViewModel,
    onNavigateToDetail: (Int) -> Unit,
    onNavigateToAdd: () -> Unit,
    onNavigateToNotifications: () -> Unit,
    onNavigateToProfile: () -> Unit,
    onNavigateToImport: () -> Unit
) {
    // PERF-02: today/upcoming partitioning and per-item daysUntil/age are now computed once in
    // the ViewModel (see GlimmerViewModel.homeUiState), not recomputed here on every
    // recomposition — a keystroke in the search box, the reminder banner being dismissed, the
    // delete-undo snackbar appearing, none of that used to be free.
    val uiState by viewModel.homeUiState.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()

    // Reflects the REAL, current system permission state (unlike a stored preference, these can
    // change outside the app — e.g. the user revokes notifications from system Settings) so the
    // warning shows up whenever reminders genuinely can't fire, not just right after onboarding.
    val context = LocalContext.current
    val notificationsGranted by rememberNotificationsPermissionState(context)
    val exactAlarmsGranted by rememberExactAlarmPermissionState(context)
    var reminderWarningDismissed by remember { mutableStateOf(false) }
    val showReminderWarning = (!notificationsGranted || !exactAlarmsGranted) && !reminderWarningDismissed

    // BUG-33: deleting a birthday used to be instant and irreversible. deleteBirthday() still
    // deletes right away (so alarms/DB stay consistent immediately), but now emits an event this
    // screen turns into an Undo snackbar — undoDelete() re-inserts the row and re-arms its alarm.
    val snackbarHostState = remember { SnackbarHostState() }
    val undoDeleteMessageTemplate = stringResource(R.string.home_undo_delete_message)
    val undoActionLabel = stringResource(R.string.common_undo)
    LaunchedEffect(viewModel) {
        viewModel.deleteEvents.collect { name ->
            val result = snackbarHostState.showSnackbar(
                message = String.format(undoDeleteMessageTemplate, name),
                actionLabel = undoActionLabel,
                duration = SnackbarDuration.Long
            )
            if (result == SnackbarResult.ActionPerformed) {
                viewModel.undoDelete()
            }
        }
    }

    // Section 4.2: Add/Edit fire their save and navigate back in the same click without waiting
    // for the result (see AddBirthdayScreen/EditBirthdayScreen), so a failure has nowhere to
    // surface on the screen where it happened. GlimmerViewModel.events is the generic safety net
    // for exactly that — previously a failed save "looked identical to a successful one".
    // context.getString(...) (not stringResource(), which needs composable context) resolves the
    // event's resource id from inside the plain suspend collector below.
    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                is UiEvent.Error -> snackbarHostState.showSnackbar(context.getString(event.messageRes))
                is UiEvent.ImportSuccess -> snackbarHostState.showSnackbar(
                    context.resources.getQuantityString(R.plurals.import_success_snackbar, event.count, event.count)
                )
            }
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        stringResource(R.string.app_name),
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
                            contentDescription = stringResource(R.string.home_cd_notifications),
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
                            contentDescription = stringResource(R.string.home_cd_profile),
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
                    contentDescription = stringResource(R.string.home_cd_add_birthday),
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
                                    Text(
                                        stringResource(R.string.home_search_placeholder),
                                        color = MaterialTheme.colorScheme.outlineVariant,
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                }
                                inner()
                            }
                        )
                    }
                }
            }

            // ── Reminder health warning ────────────────────────────────────
            if (showReminderWarning) {
                item {
                    ReminderHealthBanner(
                        onFixClick = onNavigateToNotifications,
                        onDismiss = { reminderWarningDismissed = true }
                    )
                }
            }

            // ── Today's Birthdays ─────────────────────────────────────────
            if (uiState.today.isNotEmpty()) {
                item {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Cake, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            stringResource(R.string.home_section_today),
                            style = MaterialTheme.typography.headlineMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }

                items(uiState.today, key = { it.birthday.id }) { item ->
                    TodayBirthdayCard(item = item, onClick = { onNavigateToDetail(item.birthday.id) })
                }
            }

            // ── Upcoming Birthdays ────────────────────────────────────────
            item {
                Text(
                    if (uiState.upcoming.isEmpty() && uiState.today.isEmpty())
                        stringResource(R.string.home_section_none_yet)
                    else
                        stringResource(R.string.home_section_upcoming),
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (uiState.upcoming.isEmpty() && uiState.today.isEmpty()) {
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
                            Text(stringResource(R.string.home_empty_title), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.outline)
                            Text(stringResource(R.string.home_empty_subtitle), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.outlineVariant)
                            // FEAT-02: realistically nobody types in 60 people by hand — this is
                            // the single biggest lever on whether the app ever gets used at all.
                            TextButton(onClick = onNavigateToImport) {
                                Text(stringResource(R.string.home_import_contacts), color = MaterialTheme.colorScheme.primary)
                            }
                        }
                    }
                }
            } else {
                items(uiState.upcoming, key = { it.birthday.id }) { item ->
                    UpcomingBirthdayCard(item = item, onClick = { onNavigateToDetail(item.birthday.id) })
                }
            }

            item { Spacer(modifier = Modifier.height(96.dp)) }
        }
    }
}

/**
 * Shown when the app can't reliably fire reminders — either POST_NOTIFICATIONS is blocked, or
 * exact alarms aren't available (see NotificationScheduler.canScheduleExactAlarms). This is the
 * one place the real, current permission state surfaces; it's not a one-time onboarding prompt,
 * since either permission can be revoked from system Settings at any time after being granted.
 */
@Composable
private fun ReminderHealthBanner(onFixClick: () -> Unit, onDismiss: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .neumorphic(cornerRadius = 16.dp, shapeBackgroundColor = MaterialTheme.colorScheme.errorContainer)
            .padding(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                Icons.Default.WarningAmber,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(stringResource(R.string.home_reminder_warning_title), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onErrorContainer)
                Text(
                    stringResource(R.string.home_reminder_warning_subtitle),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onErrorContainer
                )
            }
            TextButton(onClick = onFixClick) {
                Text(stringResource(R.string.home_reminder_warning_fix), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelLarge)
            }
            IconButton(onClick = onDismiss) {
                Icon(
                    Icons.Default.Close,
                    contentDescription = stringResource(R.string.home_cd_dismiss),
                    tint = MaterialTheme.colorScheme.onErrorContainer,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

@Composable
private fun TodayBirthdayCard(item: BirthdayUi, onClick: () -> Unit) {
    val birthday = item.birthday
    val age = item.ageTurning
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
                    BirthdayAvatar(photoUri = birthday.photoUri, modifier = Modifier.fillMaxSize())
                }
                Spacer(modifier = Modifier.width(16.dp))
                Column {
                    Text(birthday.name, style = MaterialTheme.typography.headlineMedium)
                    Text(
                        // FEAT-05: age is unknowable without a birth year — show a plain
                        // celebration line instead of "Turning null today!".
                        if (age != null) stringResource(R.string.home_turning_today, age) else stringResource(R.string.home_celebrating_today),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.secondary
                    )
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
                    Text(stringResource(R.string.home_send_wish), color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelLarge)
                }
            }
        }
    }
}

@Composable
private fun UpcomingBirthdayCard(item: BirthdayUi, onClick: () -> Unit) {
    val birthday = item.birthday
    val monthDay = birthday.birthMonthDay()
    val dayStr = "${monthDay.month.getDisplayName(TextStyle.SHORT, Locale.getDefault())} ${monthDay.dayOfMonth}"
    val daysLeft = item.daysUntil
    val daysLabel = when (daysLeft) {
        0 -> stringResource(R.string.days_until_today)
        1 -> stringResource(R.string.days_until_tomorrow)
        else -> pluralStringResource(R.plurals.days_until_n, daysLeft, daysLeft)
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
                    BirthdayAvatar(photoUri = birthday.photoUri, modifier = Modifier.fillMaxSize())
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
