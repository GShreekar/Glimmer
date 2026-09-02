package com.example.ui.screens

import android.Manifest
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.data.NotificationScheduler
import com.example.ui.components.NeumorphicButton
import com.example.ui.components.NeumorphicIconButton
import com.example.ui.components.NeumorphicSwitch
import com.example.ui.components.neumorphic
import com.example.ui.components.rememberExactAlarmPermissionState
import com.example.ui.components.rememberNotificationsPermissionState
import com.example.viewmodel.GlimmerViewModel

/**
 * All three settings here are now driven by [GlimmerViewModel] (backed by SettingsRepository /
 * DataStore) instead of a raw SharedPreferences file. Previously `save()` wrote to
 * "glimmer_notifications" and nothing else in the app ever read it back — the global toggle
 * didn't stop reminders, the sound switch had no channel to route to, and AddBirthdayScreen
 * never consulted the "default reminder" — so every control on this screen was decorative.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotificationsSettingsScreen(
    viewModel: GlimmerViewModel,
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current

    val notificationsEnabled by viewModel.notificationsEnabled.collectAsState()
    val soundEnabled by viewModel.soundEnabled.collectAsState()
    val defaultReminderTime by viewModel.defaultReminderTime.collectAsState()

    var showReminderDropdown by remember { mutableStateOf(false) }
    val reminderOptions = listOf("On the day", "1 day before", "3 days before", "1 week before")

    val systemNotificationsGranted by rememberNotificationsPermissionState(context)
    val exactAlarmsGranted by rememberExactAlarmPermissionState(context)

    // Safe to request from here (unlike the Add/Edit save buttons this screen doesn't navigate
    // away in the same click), so the launcher survives long enough for the system dialog's
    // result to actually come back.
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> viewModel.setNotificationsEnabled(granted) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text("Notifications", style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.primary)
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
                .verticalScroll(rememberScrollState())
        ) {
            Spacer(modifier = Modifier.height(24.dp))

            // ── System permission health ───────────────────────────────
            if (!systemNotificationsGranted || !exactAlarmsGranted) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .neumorphic(cornerRadius = 16.dp, shapeBackgroundColor = MaterialTheme.colorScheme.surface)
                        .padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Text("Reminder Health", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.error)

                    if (!systemNotificationsGranted) {
                        PermissionStatusRow(
                            title = "App Notifications",
                            subtitle = "Blocked at the system level — reminders can't show at all",
                            onClick = {
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                    permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                } else {
                                    context.startActivity(
                                        Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                                            .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                                    )
                                }
                            }
                        )
                    }
                    if (!exactAlarmsGranted) {
                        PermissionStatusRow(
                            title = "Exact Alarms",
                            subtitle = "Without this, reminders may fire late or be dropped by the system",
                            onClick = { NotificationScheduler.requestExactAlarmPermission(context) }
                        )
                    }
                }
                Spacer(modifier = Modifier.height(20.dp))
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .neumorphic(cornerRadius = 16.dp, shapeBackgroundColor = MaterialTheme.colorScheme.surface)
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(24.dp)
            ) {
                // ── Enable All Notifications ─────────────────────────────
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                        Box(
                            modifier = Modifier.size(40.dp)
                                .neumorphic(cornerRadius = 20.dp, shapeBackgroundColor = MaterialTheme.colorScheme.surface),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.NotificationsActive, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                        }
                        Spacer(modifier = Modifier.width(16.dp))
                        Column {
                            Text("Enable Notifications", style = MaterialTheme.typography.bodyLarge)
                            Text("Turn all birthday alerts on or off", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    NeumorphicSwitch(
                        checked = notificationsEnabled,
                        onCheckedChange = { enabled ->
                            if (enabled && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !systemNotificationsGranted) {
                                // Don't flip the app-level switch until the system permission is
                                // actually granted — the launcher's callback does that itself.
                                permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                            } else {
                                viewModel.setNotificationsEnabled(enabled)
                            }
                        }
                    )
                }

                AnimatedVisibility(visible = notificationsEnabled) {
                    Column(verticalArrangement = Arrangement.spacedBy(24.dp)) {

                        HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))

                        // ── Sounds ────────────────────────────────────────
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                Box(
                                    modifier = Modifier.size(40.dp)
                                        .neumorphic(cornerRadius = 20.dp, shapeBackgroundColor = MaterialTheme.colorScheme.surface),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(Icons.AutoMirrored.Filled.VolumeUp, contentDescription = null, tint = MaterialTheme.colorScheme.secondary, modifier = Modifier.size(20.dp))
                                }
                                Spacer(modifier = Modifier.width(16.dp))
                                Column {
                                    Text("Notification Sounds", style = MaterialTheme.typography.bodyLarge)
                                    Text("Play sound for alerts", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                            NeumorphicSwitch(
                                checked = soundEnabled,
                                onCheckedChange = { viewModel.setSoundEnabled(it) }
                            )
                        }

                        HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))

                        // ── Default Reminder Time ─────────────────────────
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                Box(
                                    modifier = Modifier.size(40.dp)
                                        .neumorphic(cornerRadius = 20.dp, shapeBackgroundColor = MaterialTheme.colorScheme.surface),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(Icons.Default.Schedule, contentDescription = null, tint = MaterialTheme.colorScheme.tertiary, modifier = Modifier.size(20.dp))
                                }
                                Spacer(modifier = Modifier.width(16.dp))
                                Column {
                                    Text("Default Reminder", style = MaterialTheme.typography.bodyLarge)
                                    Text("Used to pre-fill new birthdays", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }

                            ExposedDropdownMenuBox(
                                expanded = showReminderDropdown,
                                onExpandedChange = { showReminderDropdown = it }
                            ) {
                                TextButton(
                                    onClick = { showReminderDropdown = true },
                                    modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable)
                                ) {
                                    Text(defaultReminderTime, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelMedium)
                                }
                                ExposedDropdownMenu(
                                    expanded = showReminderDropdown,
                                    onDismissRequest = { showReminderDropdown = false }
                                ) {
                                    reminderOptions.forEach { option ->
                                        DropdownMenuItem(
                                            text = { Text(option) },
                                            onClick = {
                                                viewModel.setDefaultReminderTime(option)
                                                showReminderDropdown = false
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
private fun PermissionStatusRow(title: String, subtitle: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
            Icon(
                Icons.Default.WarningAmber,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text(title, style = MaterialTheme.typography.bodyLarge)
                Text(subtitle, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Spacer(modifier = Modifier.width(12.dp))
        NeumorphicButton(
            onClick = onClick,
            modifier = Modifier.height(40.dp),
            cornerRadius = 10.dp,
            shapeBackgroundColor = MaterialTheme.colorScheme.primary
        ) {
            Text(
                "Enable",
                color = MaterialTheme.colorScheme.onPrimary,
                style = MaterialTheme.typography.labelMedium,
                modifier = Modifier.padding(horizontal = 16.dp)
            )
        }
    }
}
