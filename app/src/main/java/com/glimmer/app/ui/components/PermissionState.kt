package com.glimmer.app.ui.components

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.glimmer.app.data.NotificationScheduler

/**
 * Re-checks a permission/settings value every time the screen resumes, so returning from the
 * system Settings screen (where these are actually granted) is reflected without restarting the
 * app. Neither permission has a Compose-observable state of its own — both live outside the
 * process — so polling on resume is the standard way to pick up the change.
 */
@Composable
private fun rememberOnResumeState(initial: () -> Boolean, refresh: () -> Boolean): State<Boolean> {
    val lifecycleOwner = LocalLifecycleOwner.current
    val state = remember { mutableStateOf(initial()) }
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                state.value = refresh()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    return state
}

/** Whether the user can currently see notifications from this app at all (system-level toggle). */
@Composable
fun rememberNotificationsPermissionState(context: Context): State<Boolean> =
    rememberOnResumeState(
        initial = { NotificationManagerCompat.from(context).areNotificationsEnabled() },
        refresh = { NotificationManagerCompat.from(context).areNotificationsEnabled() }
    )

/** Whether exact-time alarms are currently available (see NotificationScheduler.canScheduleExactAlarms). */
@Composable
fun rememberExactAlarmPermissionState(context: Context): State<Boolean> =
    rememberOnResumeState(
        initial = { NotificationScheduler.canScheduleExactAlarms(context) },
        refresh = { NotificationScheduler.canScheduleExactAlarms(context) }
    )
