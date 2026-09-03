package com.glimmer.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import kotlin.math.abs

/**
 * A person's photo, cropped to a circle, or — FEAT-09 — a colored monogram derived from their
 * name when there isn't one, replacing the plain [MaterialTheme.colorScheme.primaryContainer]
 * circle every card used to show unconditionally. Shared by every place an avatar renders (Home's
 * cards, the Detail screen) so they can't drift.
 */
@Composable
fun BirthdayAvatar(photoUri: String?, name: String = "", modifier: Modifier = Modifier) {
    if (photoUri != null) {
        AsyncImage(
            model = photoUri,
            contentDescription = null,
            modifier = modifier.clip(CircleShape),
            contentScale = ContentScale.Crop
        )
    } else {
        // BoxWithConstraints (not a fixed typography style) so the monogram reads correctly at
        // every size this composable is used at — a 48dp list-row avatar and a 128dp Detail
        // screen avatar have wildly different needs, and callers only ever pass a Modifier, not a
        // size value this composable could otherwise read directly.
        BoxWithConstraints(
            modifier = modifier.clip(CircleShape).background(monogramColor(name)),
            contentAlignment = Alignment.Center
        ) {
            Text(
                monogramText(name),
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = (maxWidth.value * 0.4f).sp
            )
        }
    }
}

// A fixed, hand-picked palette rather than deriving RGB straight from the hash — that tends to
// land on muddy or overly-dark colors against white text more often than not.
private val MonogramPalette = listOf(
    Color(0xFFE57373), Color(0xFFBA68C8), Color(0xFF7986CB), Color(0xFF4FC3F7),
    Color(0xFF4DB6AC), Color(0xFF81C784), Color(0xFFFFB74D), Color(0xFFFF8A65),
    Color(0xFF9575CD), Color(0xFF4DD0E1)
)

private fun monogramColor(name: String): Color {
    if (name.isBlank()) return MonogramPalette.first()
    return MonogramPalette[abs(name.hashCode()) % MonogramPalette.size]
}

private fun monogramText(name: String): String {
    val parts = name.trim().split(Regex("\\s+")).filter { it.isNotBlank() }
    return when {
        parts.isEmpty() -> "?"
        parts.size == 1 -> parts[0].take(1).uppercase()
        else -> (parts.first().take(1) + parts.last().take(1)).uppercase()
    }
}
