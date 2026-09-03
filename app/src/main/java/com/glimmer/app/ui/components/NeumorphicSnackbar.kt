package com.glimmer.app.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarData
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * A drop-in replacement for `SnackbarHost(hostState)` — same `SnackbarHostState`/`showSnackbar`
 * API (including the action-label + `SnackbarResult` flow BUG-33's undo and the import-success
 * confirmation rely on), just rendered as a raised neumorphic card instead of Material3's default
 * flat, dark pill, which was the one place left that didn't match the rest of the app's look.
 * `SnackbarHost` still owns the show/dismiss timing and the enter/exit animation; only the
 * per-message content composable is swapped out.
 */
@Composable
fun NeumorphicSnackbarHost(hostState: SnackbarHostState) {
    SnackbarHost(hostState = hostState) { data -> NeumorphicSnackbar(data) }
}

@Composable
private fun NeumorphicSnackbar(data: SnackbarData) {
    Box(
        modifier = Modifier
            .padding(16.dp)
            .fillMaxWidth()
            .neumorphic(cornerRadius = 16.dp, shapeBackgroundColor = MaterialTheme.colorScheme.surface)
            .padding(horizontal = 20.dp, vertical = 16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                data.visuals.message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f)
            )
            data.visuals.actionLabel?.let { actionLabel ->
                Spacer(modifier = Modifier.width(12.dp))
                TextButton(onClick = { data.performAction() }) {
                    Text(actionLabel, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelLarge)
                }
            }
        }
    }
}
