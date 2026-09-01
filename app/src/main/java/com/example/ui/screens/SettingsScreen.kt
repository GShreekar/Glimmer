package com.example.ui.screens

import android.content.Context
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Face
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.ui.components.NeumorphicSwitch
import com.example.ui.components.NeumorphicIconButton
import com.example.ui.components.neumorphic
import com.example.viewmodel.GlimmerViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: GlimmerViewModel,
    onNavigateToNotifications: () -> Unit = {},
    onNavigateToProfile: () -> Unit = {},
    onNavigateToSync: () -> Unit = {}
) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("glimmer_profile", Context.MODE_PRIVATE) }
    val displayName by remember { derivedStateOf { prefs.getString("name", "Alex Mercer") ?: "Alex Mercer" } }
    val email by remember { derivedStateOf { prefs.getString("email", "alex.mercer@example.com") ?: "alex.mercer@example.com" } }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "Settings",
                        style = MaterialTheme.typography.headlineMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
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
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(20.dp))

            // ── Avatar ───────────────────────────────────────────────────
            Box(
                modifier = Modifier
                    .size(96.dp)
                    .neumorphic(cornerRadius = 48.dp, shapeBackgroundColor = MaterialTheme.colorScheme.surface),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.Face,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(48.dp)
                )

                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .size(32.dp)
                        .neumorphic(cornerRadius = 16.dp, shapeBackgroundColor = MaterialTheme.colorScheme.primary)
                        .clickable(onClick = onNavigateToProfile),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.Edit,
                        contentDescription = "Edit Profile",
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
            Text(displayName, style = MaterialTheme.typography.headlineMedium)
            Text(email, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)

            Spacer(modifier = Modifier.height(32.dp))

            // ── Settings Categories ──────────────────────────────────────
            SettingsCategory("ACCOUNT") {
                SettingsItem(
                    title = "User Profile",
                    icon = Icons.Default.Person,
                    iconColor = MaterialTheme.colorScheme.primary,
                    onClick = onNavigateToProfile
                )
                SettingsItem(
                    title = "Sync & Backup",
                    icon = Icons.Default.CloudSync,
                    iconColor = MaterialTheme.colorScheme.primary,
                    onClick = onNavigateToSync
                )
            }

            SettingsCategory("PREFERENCES") {
                SettingsItem(
                    title = "Notifications",
                    icon = Icons.Default.Notifications,
                    iconColor = MaterialTheme.colorScheme.secondary,
                    subtitle = "Time of day, sounds",
                    onClick = onNavigateToNotifications
                )
            }
        }
    }
}

@Composable
fun SettingsCategory(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .neumorphic(cornerRadius = 12.dp, shapeBackgroundColor = MaterialTheme.colorScheme.surface)
            .padding(12.dp)
    ) {
        Text(
            title,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(start = 12.dp, top = 4.dp, bottom = 4.dp)
        )
        content()
    }
    Spacer(modifier = Modifier.height(20.dp))
}

@Composable
fun SettingsItem(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    iconColor: Color,
    subtitle: String? = null,
    hasToggle: Boolean = false,
    isChecked: Boolean = false,
    onToggle: ((Boolean) -> Unit)? = null,
    onClick: (() -> Unit)? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier
            )
            .padding(8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .neumorphic(cornerRadius = 20.dp, shapeBackgroundColor = MaterialTheme.colorScheme.surface),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, contentDescription = null, tint = iconColor, modifier = Modifier.size(20.dp))
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text(title, style = MaterialTheme.typography.bodyLarge)
                if (subtitle != null) {
                    Text(subtitle, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        if (hasToggle && onToggle != null) {
            NeumorphicSwitch(
                checked = isChecked,
                onCheckedChange = onToggle
            )
        } else {
            Icon(Icons.Default.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.outline)
        }
    }
}
