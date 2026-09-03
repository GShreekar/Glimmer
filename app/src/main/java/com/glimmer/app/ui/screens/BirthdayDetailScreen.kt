package com.glimmer.app.ui.screens

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.ChatBubble
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Redeem
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.glimmer.app.R
import com.glimmer.app.data.birthLocalDate
import com.glimmer.app.data.birthMonthDay
import com.glimmer.app.ui.components.BirthdayAvatar
import com.glimmer.app.ui.components.NeumorphicButton
import com.glimmer.app.ui.components.NeumorphicIconButton
import com.glimmer.app.ui.components.neumorphic
import com.glimmer.app.viewmodel.GlimmerViewModel
import com.glimmer.app.viewmodel.ageOnNextBirthday
import com.glimmer.app.viewmodel.daysUntilBirthday
import kotlinx.coroutines.launch
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BirthdayDetailScreen(
    id: Int,
    viewModel: GlimmerViewModel,
    onNavigateBack: () -> Unit,
    onNavigateToEdit: (Int) -> Unit
) {
    val birthdayState by remember(id) { viewModel.getBirthdayById(id) }.collectAsState()
    val birthday = birthdayState
    var showDeleteDialog by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()

    // Delete confirmation dialog
    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            containerColor = MaterialTheme.colorScheme.surface,
            title = {
                Text(stringResource(R.string.detail_delete_dialog_title), style = MaterialTheme.typography.headlineMedium)
            },
            text = {
                Text(
                    stringResource(
                        R.string.detail_delete_dialog_message,
                        birthday?.name ?: stringResource(R.string.detail_delete_dialog_fallback_name)
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            },
            confirmButton = {
                NeumorphicButton(
                    onClick = {
                        showDeleteDialog = false
                        viewModel.deleteBirthday(id)
                        onNavigateBack()
                    },
                    modifier = Modifier.height(44.dp).padding(end = 4.dp),
                    cornerRadius = 10.dp,
                    shapeBackgroundColor = MaterialTheme.colorScheme.errorContainer
                ) {
                    Text(stringResource(R.string.detail_delete_confirm), color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(horizontal = 16.dp))
                }
            },
            dismissButton = {
                NeumorphicButton(
                    onClick = { showDeleteDialog = false },
                    modifier = Modifier.height(44.dp),
                    cornerRadius = 10.dp
                ) {
                    Text(stringResource(R.string.detail_delete_cancel), color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.padding(horizontal = 16.dp))
                }
            }
        )
    }

    if (birthday == null) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
        }
        return
    }

    val monthDay = birthday.birthMonthDay()
    val fullDateFormat = DateTimeFormatter.ofPattern("MMMM d, yyyy", Locale.getDefault())
    val daysLeft = daysUntilBirthday(birthday)
    val age = ageOnNextBirthday(birthday)

    val daysLabel = when (daysLeft) {
        0 -> stringResource(R.string.detail_days_today)
        1 -> stringResource(R.string.days_until_tomorrow)
        else -> pluralStringResource(R.plurals.detail_days_away, daysLeft, daysLeft)
    }
    val birthdateStr = "${monthDay.month.getDisplayName(TextStyle.FULL, Locale.getDefault())} ${monthDay.dayOfMonth}"

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        stringResource(R.string.detail_title),
                        style = MaterialTheme.typography.headlineMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                },
                navigationIcon = {
                    NeumorphicIconButton(
                        onClick = onNavigateBack,
                        modifier = Modifier.padding(start = 8.dp).size(40.dp),
                        cornerRadius = 20.dp
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.detail_cd_back), tint = MaterialTheme.colorScheme.primary)
                    }
                },
                actions = {
                    NeumorphicIconButton(
                        onClick = { onNavigateToEdit(id) },
                        modifier = Modifier.size(40.dp),
                        cornerRadius = 20.dp
                    ) {
                        Icon(Icons.Default.Edit, contentDescription = stringResource(R.string.detail_cd_edit), tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    NeumorphicIconButton(
                        onClick = { showDeleteDialog = true },
                        modifier = Modifier.padding(end = 12.dp).size(40.dp),
                        cornerRadius = 20.dp
                    ) {
                        Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.detail_cd_delete), tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(18.dp))
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
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(20.dp))

            // Avatar
            Box(
                modifier = Modifier
                    .size(128.dp)
                    .neumorphic(cornerRadius = 64.dp, shapeBackgroundColor = MaterialTheme.colorScheme.surface)
                    .padding(4.dp),
                contentAlignment = Alignment.Center
            ) {
                BirthdayAvatar(photoUri = birthday.photoUri, modifier = Modifier.fillMaxSize())
            }

            Spacer(modifier = Modifier.height(16.dp))
            Text(birthday.name, style = MaterialTheme.typography.headlineLarge, color = MaterialTheme.colorScheme.onSurface)
            Text(
                stringResource(R.string.detail_turning_on, age, birthdateStr),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.primary
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Days away badge
            Box(
                modifier = Modifier
                    .neumorphic(cornerRadius = 20.dp, shapeBackgroundColor = MaterialTheme.colorScheme.primaryContainer)
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(daysLabel, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            }

            // Relationship badge
            Spacer(modifier = Modifier.height(8.dp))
            Box(
                modifier = Modifier
                    .neumorphic(cornerRadius = 16.dp, shapeBackgroundColor = MaterialTheme.colorScheme.surface)
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(birthday.relationship, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            Spacer(modifier = Modifier.height(32.dp))

            // ── Action Buttons ────────────────────────────────────────────
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                val smsBody = stringResource(R.string.detail_sms_body, birthday.name)
                val noSmsAppMessage = stringResource(R.string.detail_snackbar_no_sms_app)
                val noDialerAppMessage = stringResource(R.string.detail_snackbar_no_dialer_app)
                val noBrowserMessage = stringResource(R.string.detail_snackbar_no_browser)

                ActionButton(
                    icon = Icons.Default.ChatBubble,
                    label = stringResource(R.string.detail_action_message),
                    modifier = Modifier.weight(1f),
                    color = MaterialTheme.colorScheme.primary,
                    bgColor = MaterialTheme.colorScheme.surface,
                    onClick = {
                        // ACTION_SENDTO with a smsto: URI prefills the recipient; a bare "sms:"
                        // URI (the old fallback) opens an empty composer the user has to address
                        // by hand, which defeats the point of a "one tap" quick action.
                        val phone = birthday.phoneNumber
                        val smsIntent = if (!phone.isNullOrBlank()) {
                            Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:$phone"))
                        } else {
                            Intent(Intent.ACTION_VIEW, Uri.parse("sms:"))
                        }.apply {
                            putExtra("sms_body", smsBody)
                        }
                        context.safeStartActivity(smsIntent) {
                            coroutineScope.launch { snackbarHostState.showSnackbar(noSmsAppMessage) }
                        }
                    }
                )
                ActionButton(
                    icon = Icons.Default.Call,
                    label = stringResource(R.string.detail_action_call),
                    modifier = Modifier.weight(1f),
                    color = MaterialTheme.colorScheme.tertiary,
                    bgColor = MaterialTheme.colorScheme.surface,
                    onClick = {
                        val phone = birthday.phoneNumber
                        val dialIntent = if (!phone.isNullOrBlank()) {
                            Intent(Intent.ACTION_DIAL, Uri.parse("tel:$phone"))
                        } else {
                            Intent(Intent.ACTION_DIAL)
                        }
                        context.safeStartActivity(dialIntent) {
                            coroutineScope.launch { snackbarHostState.showSnackbar(noDialerAppMessage) }
                        }
                    }
                )
                ActionButton(
                    icon = Icons.Default.Redeem,
                    label = stringResource(R.string.detail_action_gift),
                    modifier = Modifier.weight(1f),
                    color = MaterialTheme.colorScheme.onPrimary,
                    bgColor = MaterialTheme.colorScheme.primary,
                    onClick = {
                        val searchIntent = Intent(Intent.ACTION_VIEW,
                            Uri.parse("https://www.google.com/search?q=birthday+gift+ideas+for+${Uri.encode(birthday.relationship.lowercase())}"))
                        context.safeStartActivity(searchIntent) {
                            coroutineScope.launch { snackbarHostState.showSnackbar(noBrowserMessage) }
                        }
                    }
                )
            }

            Spacer(modifier = Modifier.height(32.dp))

            // ── Details Card ─────────────────────────────────────────────
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp)
                    .neumorphic(cornerRadius = 20.dp, shapeBackgroundColor = MaterialTheme.colorScheme.surface)
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(stringResource(R.string.detail_details_heading), style = MaterialTheme.typography.headlineMedium)

                DetailRow(label = stringResource(R.string.detail_label_full_name), value = birthday.name)
                DetailRow(label = stringResource(R.string.detail_label_dob), value = fullDateFormat.format(birthday.birthLocalDate()))
                DetailRow(label = stringResource(R.string.detail_label_relationship), value = birthday.relationship)
                if (!birthday.phoneNumber.isNullOrBlank()) {
                    DetailRow(label = stringResource(R.string.detail_label_phone), value = birthday.phoneNumber)
                }
                DetailRow(label = stringResource(R.string.detail_label_reminder), value = if (birthday.reminderEnabled) birthday.reminderTime else stringResource(R.string.detail_reminder_off))
                if (!birthday.notes.isNullOrBlank()) {
                    DetailRow(label = stringResource(R.string.detail_label_notes), value = birthday.notes)
                }
            }

            Spacer(modifier = Modifier.height(48.dp))
        }
    }
}

/**
 * Launches [intent], calling [onMissing] instead of crashing when no app can handle it — a
 * device with no SMS app, no dialer, or no browser (some tablets, Android Go, enterprise builds)
 * would otherwise throw ActivityNotFoundException straight out of these quick actions.
 */
private fun Context.safeStartActivity(intent: Intent, onMissing: () -> Unit) {
    try {
        startActivity(intent)
    } catch (e: ActivityNotFoundException) {
        onMissing()
    }
}

@Composable
private fun DetailRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top
    ) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.outline)
        Text(
            value,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.End,
            modifier = Modifier.weight(1f).padding(start = 16.dp)
        )
    }
}

@Composable
fun ActionButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    modifier: Modifier,
    color: Color,
    bgColor: Color,
    onClick: () -> Unit = {}
) {
    NeumorphicButton(
        onClick = onClick,
        modifier = modifier.height(96.dp),
        cornerRadius = 16.dp,
        shapeBackgroundColor = bgColor
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(28.dp))
            Spacer(modifier = Modifier.height(8.dp))
            Text(label, style = MaterialTheme.typography.labelSmall, color = color)
        }
    }
}

@Composable
fun GiftIdeaCard(title: String, price: String, icon: androidx.compose.ui.graphics.vector.ImageVector? = null, modifier: Modifier) {
    Column(
        modifier = modifier
            .neumorphic(cornerRadius = 16.dp, shapeBackgroundColor = MaterialTheme.colorScheme.surface)
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(
            modifier = Modifier.fillMaxWidth().height(96.dp)
                .neumorphic(isSunken = true, cornerRadius = 12.dp, shapeBackgroundColor = MaterialTheme.colorScheme.surface),
            contentAlignment = Alignment.Center
        ) {
            if (icon != null) {
                Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.outline, modifier = Modifier.size(32.dp))
            }
        }
        Column {
            Text(title, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurface, maxLines = 1)
            Text(price, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
        }
    }
}

@Composable
fun PastCelebrationItem(day: String, desc: String, date: String) {
    Row(
        modifier = Modifier.fillMaxWidth()
            .neumorphic(cornerRadius = 16.dp, shapeBackgroundColor = MaterialTheme.colorScheme.surface)
            .padding(16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier.size(48.dp)
                    .neumorphic(isSunken = true, cornerRadius = 24.dp, shapeBackgroundColor = MaterialTheme.colorScheme.surface),
                contentAlignment = Alignment.Center
            ) {
                Text(day, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text(desc, style = MaterialTheme.typography.bodyMedium)
                Text(date, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
