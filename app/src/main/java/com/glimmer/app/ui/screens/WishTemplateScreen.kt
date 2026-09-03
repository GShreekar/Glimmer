package com.glimmer.app.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.glimmer.app.R
import com.glimmer.app.data.WishTemplates
import com.glimmer.app.data.renderWishTemplate
import com.glimmer.app.ui.components.NeumorphicButton
import com.glimmer.app.ui.components.NeumorphicIconButton
import com.glimmer.app.ui.components.NeumorphicSnackbarHost
import com.glimmer.app.ui.components.NeumorphicTextField
import com.glimmer.app.ui.components.neumorphic
import com.glimmer.app.viewmodel.GlimmerViewModel

/**
 * FEAT-08: lets the user customize the message Message/WhatsApp/the notification's "Message"
 * action send — was hard-coded to "Happy Birthday {name}! 🎂🎉" everywhere. A default template
 * plus optional overrides for each of the five relationship presets (a message to your manager
 * shouldn't read like one to your sibling); anything else — a blank override, or a free-text
 * relationship that isn't one of the five — falls back to the default.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WishTemplateScreen(
    viewModel: GlimmerViewModel,
    onNavigateBack: () -> Unit
) {
    val stored by viewModel.wishTemplates.collectAsState()
    var defaultTemplate by remember(stored) { mutableStateOf(stored.default) }
    var overrides by remember(stored) { mutableStateOf(stored.perRelationship) }
    var savedSnackbar by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }
    val savedMessage = stringResource(R.string.wish_template_saved_snackbar)

    LaunchedEffect(savedSnackbar) {
        if (savedSnackbar) {
            snackbarHostState.showSnackbar(savedMessage)
            savedSnackbar = false
        }
    }

    val previewName = stringResource(R.string.wish_template_preview_name)
    val preview = remember(defaultTemplate) {
        renderWishTemplate(defaultTemplate.ifBlank { WishTemplates.DEFAULT_TEMPLATE }, previewName, 30, "Friend")
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.wish_template_title), style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.primary) },
                navigationIcon = {
                    NeumorphicIconButton(
                        onClick = onNavigateBack,
                        modifier = Modifier.padding(start = 12.dp).size(40.dp),
                        cornerRadius = 20.dp,
                        elevation = 3.dp,
                        blur = 6.dp
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.wish_template_cd_back), tint = MaterialTheme.colorScheme.onSurface)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
            )
        },
        snackbarHost = { NeumorphicSnackbarHost(snackbarHostState) },
        containerColor = MaterialTheme.colorScheme.surface
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 24.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Spacer(modifier = Modifier.height(20.dp))
            Text(stringResource(R.string.wish_template_placeholders_hint), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(modifier = Modifier.height(20.dp))

            FormEntry(label = stringResource(R.string.wish_template_default_label)) {
                NeumorphicTextField(
                    value = defaultTemplate,
                    onValueChange = { defaultTemplate = it },
                    placeholder = WishTemplates.DEFAULT_TEMPLATE,
                    icon = Icons.AutoMirrored.Filled.Send
                )
            }

            Spacer(modifier = Modifier.height(12.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .neumorphic(isSunken = true, cornerRadius = 12.dp, shapeBackgroundColor = MaterialTheme.colorScheme.surface)
                    .padding(16.dp)
            ) {
                Column {
                    Text(stringResource(R.string.wish_template_preview_label), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(preview, style = MaterialTheme.typography.bodyMedium)
                }
            }

            Spacer(modifier = Modifier.height(28.dp))
            Text(stringResource(R.string.wish_template_overrides_heading), style = MaterialTheme.typography.headlineMedium)
            Spacer(modifier = Modifier.height(4.dp))
            Text(stringResource(R.string.wish_template_overrides_subheading), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(modifier = Modifier.height(16.dp))

            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                RelationshipPresets.forEach { preset ->
                    FormEntry(label = preset) {
                        NeumorphicTextField(
                            value = overrides[preset] ?: "",
                            onValueChange = { value ->
                                overrides = if (value.isBlank()) overrides - preset else overrides + (preset to value)
                            },
                            placeholder = stringResource(R.string.wish_template_override_placeholder),
                            icon = Icons.AutoMirrored.Filled.Send
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            NeumorphicButton(
                onClick = {
                    viewModel.setWishTemplates(
                        WishTemplates(default = defaultTemplate.ifBlank { WishTemplates.DEFAULT_TEMPLATE }, perRelationship = overrides)
                    )
                    savedSnackbar = true
                },
                modifier = Modifier.fillMaxWidth().height(56.dp),
                cornerRadius = 12.dp
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Save, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(stringResource(R.string.wish_template_save_button), color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.headlineMedium)
                }
            }

            Spacer(modifier = Modifier.height(80.dp))
        }
    }
}
