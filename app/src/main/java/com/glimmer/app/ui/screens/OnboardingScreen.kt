package com.glimmer.app.ui.screens

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cake
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Contacts
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.glimmer.app.R
import com.glimmer.app.data.NotificationScheduler
import com.glimmer.app.ui.components.NeumorphicButton
import com.glimmer.app.ui.components.neumorphic
import com.glimmer.app.ui.components.rememberExactAlarmPermissionState
import com.glimmer.app.ui.components.rememberNotificationsPermissionState
import com.glimmer.app.viewmodel.GlimmerViewModel
import kotlinx.coroutines.launch

/**
 * FEAT-11: first launch used to drop the user straight on an empty Home screen. Three pages —
 * Value (what this is, the privacy promise), Import (the single biggest lever on whether the app
 * ever gets used — nobody types in 60 people by hand), Reliability (prime the permissions this
 * app's whole value proposition depends on, at a moment that actually explains why, instead of an
 * unexplained system dialog at a random point later). Shown once — see
 * SettingsRepository.hasCompletedOnboarding / MainActivity for how that's gated.
 */
@Composable
fun OnboardingScreen(
    viewModel: GlimmerViewModel,
    onNavigateToImport: () -> Unit,
    onNavigateToAdd: () -> Unit,
    onFinished: () -> Unit
) {
    val pagerState = rememberPagerState(pageCount = { 3 })
    val coroutineScope = rememberCoroutineScope()

    fun complete(andThen: () -> Unit) {
        viewModel.setHasCompletedOnboarding(true)
        andThen()
    }

    Scaffold(containerColor = MaterialTheme.colorScheme.surface) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            HorizontalPager(state = pagerState, modifier = Modifier.weight(1f)) { page ->
                when (page) {
                    0 -> OnboardingValuePage(
                        onNext = { coroutineScope.launch { pagerState.animateScrollToPage(1) } }
                    )
                    1 -> OnboardingImportPage(
                        onImportContacts = { complete(onNavigateToImport) },
                        onAddManually = { complete(onNavigateToAdd) },
                        onSkip = { coroutineScope.launch { pagerState.animateScrollToPage(2) } }
                    )
                    else -> OnboardingReliabilityPage(
                        onFinish = { complete(onFinished) }
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 20.dp),
                horizontalArrangement = Arrangement.Center
            ) {
                repeat(3) { index ->
                    val isCurrent = pagerState.currentPage == index
                    Box(
                        modifier = Modifier
                            .padding(horizontal = 4.dp)
                            .size(if (isCurrent) 10.dp else 8.dp)
                            .clip(CircleShape)
                            .background(if (isCurrent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant)
                    )
                }
            }
        }
    }
}

@Composable
private fun OnboardingValuePage(onNext: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier.size(120.dp).neumorphic(cornerRadius = 60.dp, shapeBackgroundColor = MaterialTheme.colorScheme.surface),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Default.Cake, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(56.dp))
        }
        Spacer(modifier = Modifier.height(24.dp))
        Text(stringResource(R.string.onboarding_value_title), style = MaterialTheme.typography.headlineLarge, textAlign = TextAlign.Center)
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            stringResource(R.string.onboarding_value_subtitle),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(32.dp))
        OnboardingBullet(Icons.Default.Lock, stringResource(R.string.onboarding_value_bullet_privacy))
        Spacer(modifier = Modifier.height(16.dp))
        OnboardingBullet(Icons.Default.NotificationsActive, stringResource(R.string.onboarding_value_bullet_reminders))
        Spacer(modifier = Modifier.height(16.dp))
        OnboardingBullet(Icons.Default.Schedule, stringResource(R.string.onboarding_value_bullet_offline))
        Spacer(modifier = Modifier.height(40.dp))
        NeumorphicButton(onClick = onNext, modifier = Modifier.fillMaxWidth().height(56.dp), cornerRadius = 12.dp) {
            Text(stringResource(R.string.onboarding_next), color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.headlineMedium)
        }
    }
}

@Composable
private fun OnboardingBullet(icon: androidx.compose.ui.graphics.vector.ImageVector, text: String) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier.size(36.dp).neumorphic(cornerRadius = 18.dp, shapeBackgroundColor = MaterialTheme.colorScheme.surface),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
        }
        Spacer(modifier = Modifier.width(12.dp))
        Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
    }
}

@Composable
private fun OnboardingImportPage(
    onImportContacts: () -> Unit,
    onAddManually: () -> Unit,
    onSkip: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier.size(120.dp).neumorphic(cornerRadius = 60.dp, shapeBackgroundColor = MaterialTheme.colorScheme.surface),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Default.Contacts, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(56.dp))
        }
        Spacer(modifier = Modifier.height(24.dp))
        Text(stringResource(R.string.onboarding_import_title), style = MaterialTheme.typography.headlineLarge, textAlign = TextAlign.Center)
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            stringResource(R.string.onboarding_import_subtitle),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(32.dp))
        NeumorphicButton(onClick = onImportContacts, modifier = Modifier.fillMaxWidth().height(56.dp), cornerRadius = 12.dp) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Contacts, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(modifier = Modifier.width(8.dp))
                Text(stringResource(R.string.onboarding_import_contacts_button), color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.headlineMedium)
            }
        }
        Spacer(modifier = Modifier.height(12.dp))
        NeumorphicButton(onClick = onAddManually, modifier = Modifier.fillMaxWidth().height(56.dp), cornerRadius = 12.dp) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.PersonAdd, contentDescription = null, tint = MaterialTheme.colorScheme.onSurface)
                Spacer(modifier = Modifier.width(8.dp))
                Text(stringResource(R.string.onboarding_add_manually_button), color = MaterialTheme.colorScheme.onSurface, style = MaterialTheme.typography.headlineMedium)
            }
        }
        Spacer(modifier = Modifier.height(16.dp))
        TextButton(onClick = onSkip) {
            Text(stringResource(R.string.onboarding_skip_for_now), color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun OnboardingReliabilityPage(onFinish: () -> Unit) {
    val context = LocalContext.current
    val notificationsGranted by rememberNotificationsPermissionState(context)
    val exactAlarmsGranted by rememberExactAlarmPermissionState(context)
    var batteryExempted by remember {
        mutableStateOf(
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                (context.getSystemService(android.content.Context.POWER_SERVICE) as PowerManager).isIgnoringBatteryOptimizations(context.packageName)
            } else true
        )
    }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* Home's own banner covers a denial — this page just offers the prompt once, up front. */ }

    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier.size(100.dp).neumorphic(cornerRadius = 50.dp, shapeBackgroundColor = MaterialTheme.colorScheme.surface),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Default.NotificationsActive, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(44.dp))
        }
        Spacer(modifier = Modifier.height(20.dp))
        Text(stringResource(R.string.onboarding_reliability_title), style = MaterialTheme.typography.headlineLarge, textAlign = TextAlign.Center)
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            stringResource(R.string.onboarding_reliability_subtitle),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(28.dp))

        OnboardingPermissionRow(
            granted = notificationsGranted,
            title = stringResource(R.string.onboarding_perm_notifications_title),
            buttonLabel = stringResource(R.string.onboarding_perm_allow_button),
            onRequest = {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
            }
        )
        Spacer(modifier = Modifier.height(12.dp))
        OnboardingPermissionRow(
            granted = exactAlarmsGranted,
            title = stringResource(R.string.onboarding_perm_exact_alarm_title),
            buttonLabel = stringResource(R.string.onboarding_perm_allow_button),
            onRequest = { NotificationScheduler.requestExactAlarmPermission(context) }
        )
        Spacer(modifier = Modifier.height(12.dp))
        OnboardingPermissionRow(
            granted = batteryExempted,
            title = stringResource(R.string.onboarding_perm_battery_title),
            buttonLabel = stringResource(R.string.onboarding_perm_allow_button),
            onRequest = {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    try {
                        context.startActivity(
                            Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:${context.packageName}"))
                        )
                        batteryExempted = true
                    } catch (e: Exception) {
                        // A handful of OEM builds omit this screen; there's nothing more to do.
                    }
                }
            }
        )

        Spacer(modifier = Modifier.height(32.dp))
        NeumorphicButton(onClick = onFinish, modifier = Modifier.fillMaxWidth().height(56.dp), cornerRadius = 12.dp) {
            Text(stringResource(R.string.onboarding_get_started), color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.headlineMedium)
        }
    }
}

@Composable
private fun OnboardingPermissionRow(granted: Boolean, title: String, buttonLabel: String, onRequest: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .neumorphic(cornerRadius = 14.dp, shapeBackgroundColor = MaterialTheme.colorScheme.surface)
            .padding(16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(title, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        if (granted) {
            Icon(Icons.Default.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        } else {
            TextButton(onClick = onRequest) {
                Text(buttonLabel, color = MaterialTheme.colorScheme.primary)
            }
        }
    }
}
