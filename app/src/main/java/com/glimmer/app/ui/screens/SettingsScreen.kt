package com.glimmer.app.ui.screens

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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.glimmer.app.R
import com.glimmer.app.ui.components.NeumorphicSwitch
import com.glimmer.app.ui.components.NeumorphicIconButton
import com.glimmer.app.ui.components.neumorphic
import com.glimmer.app.viewmodel.GlimmerViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: GlimmerViewModel,
    onNavigateToNotifications: () -> Unit = {},
    onNavigateToProfile: () -> Unit = {},
    onNavigateToSync: () -> Unit = {}
) {
    // Both driven by the same ViewModel/DataStore-backed StateFlow that ProfileSettingsScreen
    // writes to, so this screen updates the moment a name/email is saved there — no restart
    // needed, and no separate "not set yet" default to keep in sync with that screen's own.
    val displayName by viewModel.profileName.collectAsState()
    val email by viewModel.profileEmail.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        stringResource(R.string.settings_title),
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
                        contentDescription = stringResource(R.string.settings_cd_edit_profile),
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
            Text(displayName.ifBlank { stringResource(R.string.settings_default_name) }, style = MaterialTheme.typography.headlineMedium)
            Text(email.ifBlank { stringResource(R.string.settings_default_email) }, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)

            Spacer(modifier = Modifier.height(32.dp))

            // ── Settings Categories ──────────────────────────────────────
            SettingsCategory(stringResource(R.string.settings_category_account)) {
                SettingsItem(
                    title = stringResource(R.string.settings_item_user_profile),
                    icon = Icons.Default.Person,
                    iconColor = MaterialTheme.colorScheme.primary,
                    onClick = onNavigateToProfile
                )
                SettingsItem(
                    title = stringResource(R.string.settings_item_sync_backup),
                    icon = Icons.Default.CloudSync,
                    iconColor = MaterialTheme.colorScheme.primary,
                    onClick = onNavigateToSync
                )
            }

            SettingsCategory(stringResource(R.string.settings_category_preferences)) {
                SettingsItem(
                    title = stringResource(R.string.settings_item_notifications),
                    icon = Icons.Default.Notifications,
                    iconColor = MaterialTheme.colorScheme.secondary,
                    subtitle = stringResource(R.string.settings_item_notifications_subtitle),
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
