package com.glimmer.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import coil.compose.AsyncImage

/**
 * A person's photo, cropped to a circle, or the plain [MaterialTheme.colorScheme.primaryContainer]
 * circle every card used to show unconditionally — [Birthday.photoUri] was declared on the entity
 * but nothing ever read or wrote it (BUG-32) until Add/Edit's avatar became a working photo picker.
 * Shared by every place an avatar renders (Home's cards, the Detail screen) so they can't drift.
 */
@Composable
fun BirthdayAvatar(photoUri: String?, modifier: Modifier = Modifier) {
    if (photoUri != null) {
        AsyncImage(
            model = photoUri,
            contentDescription = null,
            modifier = modifier.clip(CircleShape),
            contentScale = ContentScale.Crop
        )
    } else {
        Box(modifier = modifier.clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer))
    }
}
