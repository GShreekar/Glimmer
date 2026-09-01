package com.example.ui.screens

import android.Manifest
import android.content.Context
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.ui.components.NeumorphicIconButton
import com.example.ui.components.NeumorphicSwitch
import com.example.ui.components.neumorphic
import com.example.viewmodel.GlimmerViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotificationsSettingsScreen(
    viewModel: GlimmerViewModel,
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("glimmer_notifications", Context.MODE_PRIVATE) }

    var notificationsEnabled by remember { mutableStateOf(prefs.getBoolean("enabled", true)) }
    var soundEnabled by remember { mutableStateOf(prefs.getBoolean("sound", true)) }
    var defaultReminderTime by remember { mutableStateOf(prefs.getString("reminder_time", "1 day before") ?: "1 day before") }

    var showReminderDropdown by remember { mutableStateOf(false) }
    val reminderOptions = listOf("On the day", "1 day before", "3 days before", "1 week before")
    val snackbarHostState = remember { SnackbarHostState() }

    // Permission launcher
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (!granted) notificationsEnabled = false
    }

    // Persist changes
    fun save() {
        prefs.edit()
            .putBoolean("enabled", notificationsEnabled)
            .putBoolean("sound", soundEnabled)
            .putString("reminder_time", defaultReminderTime)
            .apply()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text("Notifications", style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.primary)
                },
                navigationIcon = {
                    NeumorphicIconButton(
                        onClick = {
                            save()
                            onNavigateBack()
                        },
                        modifier = Modifier.padding(start = 12.dp).size(40.dp),
                        cornerRadius = 20.dp
                    ) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = MaterialTheme.colorScheme.onSurface)
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
        ) {
            Spacer(modifier = Modifier.height(24.dp))

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
                            if (enabled && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                            }
                            notificationsEnabled = enabled
                            save()
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
                                    Icon(Icons.Default.VolumeUp, contentDescription = null, tint = MaterialTheme.colorScheme.secondary, modifier = Modifier.size(20.dp))
                                }
                                Spacer(modifier = Modifier.width(16.dp))
                                Column {
                                    Text("Notification Sounds", style = MaterialTheme.typography.bodyLarge)
                                    Text("Play sound for alerts", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                            NeumorphicSwitch(
                                checked = soundEnabled,
                                onCheckedChange = { soundEnabled = it; save() }
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
                                    Text("Default advance notice", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
                                                defaultReminderTime = option
                                                showReminderDropdown = false
                                                save()
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
