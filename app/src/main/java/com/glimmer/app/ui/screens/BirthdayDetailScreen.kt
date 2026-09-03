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
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.glimmer.app.R
import com.glimmer.app.data.birthLocalDate
import com.glimmer.app.data.birthMonthDay
import com.glimmer.app.data.renderWishTemplate
import com.glimmer.app.ui.components.BirthdayAvatar
import com.glimmer.app.ui.components.NeumorphicButton
import com.glimmer.app.ui.components.NeumorphicIconButton
import com.glimmer.app.ui.components.NeumorphicSnackbarHost
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
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()

    if (birthday == null) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
        }
        return
    }

    val monthDay = birthday.birthMonthDay()
    val fullDateFormat = DateTimeFormatter.ofPattern("MMMM d, yyyy", Locale.getDefault())
    val daysLeft = daysUntilBirthday(birthday)
    val age = ageOnNextBirthday(birthday) // FEAT-05: null when the birth year isn't known

    val daysLabel = when (daysLeft) {
        0 -> stringResource(R.string.detail_days_today)
        1 -> stringResource(R.string.days_until_tomorrow)
        else -> pluralStringResource(R.plurals.detail_days_away, daysLeft, daysLeft)
    }
    val birthdateStr = "${monthDay.month.getDisplayName(TextStyle.FULL, Locale.getDefault())} ${monthDay.dayOfMonth}"

    // FEAT-04: a person can have several reminders now; summarized here as "3 days before, 1 day
    // before" rather than the single string the old Birthday.reminderTime column held.
    val remindersState by remember(birthday.id) { viewModel.getRemindersForBirthday(birthday.id) }.collectAsState()
    val offsetLabels = mapOf(
        0 to stringResource(R.string.reminder_offset_on_day),
        1 to stringResource(R.string.reminder_offset_1_day),
        3 to stringResource(R.string.reminder_offset_3_days),
        7 to stringResource(R.string.reminder_offset_1_week)
    )
    val remindersSummary = when {
        !birthday.reminderEnabled -> stringResource(R.string.detail_reminder_off)
        remindersState == null -> null // still loading; the row below is skipped for this frame
        remindersState.isNullOrEmpty() -> stringResource(R.string.detail_reminders_none)
        else -> remindersState.orEmpty().sortedBy { it.daysBefore }
            .mapNotNull { offsetLabels[it.daysBefore] }
            .joinToString(", ")
    }

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
                    // BUG: see NeumorphicIconButton's doc — its default shadow gets clipped by
                    // the TopAppBar's own Surface at this size unless it's reduced.
                    NeumorphicIconButton(
                        onClick = onNavigateBack,
                        modifier = Modifier.padding(start = 8.dp).size(40.dp),
                        cornerRadius = 20.dp,
                        elevation = 3.dp,
                        blur = 6.dp
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.detail_cd_back), tint = MaterialTheme.colorScheme.primary)
                    }
                },
                actions = {
                    // FEAT-12
                    NeumorphicIconButton(
                        onClick = { viewModel.toggleFavorite(id) },
                        modifier = Modifier.size(40.dp),
                        cornerRadius = 20.dp,
                        elevation = 3.dp,
                        blur = 6.dp
                    ) {
                        Icon(
                            if (birthday.isFavorite) Icons.Default.Star else Icons.Default.StarBorder,
                            contentDescription = stringResource(if (birthday.isFavorite) R.string.home_cd_unfavorite else R.string.home_cd_favorite),
                            tint = if (birthday.isFavorite) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    NeumorphicIconButton(
                        onClick = { onNavigateToEdit(id) },
                        modifier = Modifier.size(40.dp),
                        cornerRadius = 20.dp,
                        elevation = 3.dp,
                        blur = 6.dp
                    ) {
                        Icon(Icons.Default.Edit, contentDescription = stringResource(R.string.detail_cd_edit), tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    // FEAT-10: was a confirmation dialog gating an irreversible delete — now an
                    // optimistic delete (deletes immediately) with an Undo snackbar (rendered on
                    // Home, which this navigates back to — see GlimmerViewModel.deleteEvents /
                    // HomeScreen's collector). Fewer taps, and strictly safer: a dialog only
                    // guards against a mistake you catch in the two seconds before you tap
                    // confirm; undo guards against noticing five seconds later too.
                    NeumorphicIconButton(
                        onClick = {
                            viewModel.deleteBirthday(id)
                            onNavigateBack()
                        },
                        modifier = Modifier.padding(end = 12.dp).size(40.dp),
                        cornerRadius = 20.dp,
                        elevation = 3.dp,
                        blur = 6.dp
                    ) {
                        Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.detail_cd_delete), tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(18.dp))
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
                BirthdayAvatar(photoUri = birthday.photoUri, name = birthday.name, modifier = Modifier.fillMaxSize())
            }

            Spacer(modifier = Modifier.height(16.dp))
            Text(birthday.name, style = MaterialTheme.typography.headlineLarge, color = MaterialTheme.colorScheme.onSurface)
            Text(
                if (age != null) stringResource(R.string.detail_turning_on, age, birthdateStr) else stringResource(R.string.detail_born_on, birthdateStr),
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
                // FEAT-08: was the single fixed string "Happy Birthday {name}! 🎂🎉" everywhere —
                // now resolved from the user's own template (global default, or a per-relationship
                // override) and rendered with this person's actual name/age/relationship.
                val wishTemplates by viewModel.wishTemplates.collectAsState()
                val smsBody = renderWishTemplate(wishTemplates.resolve(birthday.relationship), birthday.name, age, birthday.relationship)
                val noSmsAppMessage = stringResource(R.string.detail_snackbar_no_sms_app)
                val noDialerAppMessage = stringResource(R.string.detail_snackbar_no_dialer_app)
                val noBrowserMessage = stringResource(R.string.detail_snackbar_no_browser)
                val noWhatsAppMessage = stringResource(R.string.detail_snackbar_no_whatsapp)
                val noPhoneForWhatsAppMessage = stringResource(R.string.detail_snackbar_no_phone_whatsapp)

                ActionButton(
                    icon = rememberVectorPainter(Icons.Default.ChatBubble),
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
                    icon = rememberVectorPainter(Icons.Default.Call),
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
                    icon = painterResource(R.drawable.ic_whatsapp),
                    label = stringResource(R.string.detail_action_whatsapp),
                    modifier = Modifier.weight(1f),
                    color = MaterialTheme.colorScheme.secondary,
                    bgColor = MaterialTheme.colorScheme.surface,
                    onClick = {
                        val phone = birthday.phoneNumber
                        if (phone.isNullOrBlank()) {
                            // Unlike Message/Call, there's no "open WhatsApp with nobody picked"
                            // fallback — wa.me needs a number in the URL itself — so this is the
                            // one action button that has to bail out early rather than open
                            // something half-empty.
                            coroutineScope.launch { snackbarHostState.showSnackbar(noPhoneForWhatsAppMessage) }
                        } else {
                            // wa.me wants the full international number as bare digits — no `+`,
                            // spaces, or punctuation. If the number wasn't entered with a country
                            // code this simply won't resolve to the right contact; there's no way
                            // to know that from a phone number string alone.
                            val digitsOnly = phone.filter { it.isDigit() }
                            val waIntent = Intent(
                                Intent.ACTION_VIEW,
                                Uri.parse("https://wa.me/$digitsOnly?text=${Uri.encode(smsBody)}")
                            )
                            context.safeStartActivity(waIntent) {
                                coroutineScope.launch { snackbarHostState.showSnackbar(noWhatsAppMessage) }
                            }
                        }
                    }
                )
                ActionButton(
                    icon = rememberVectorPainter(Icons.Default.Redeem),
                    label = stringResource(R.string.detail_action_gift),
                    modifier = Modifier.weight(1f),
                    color = MaterialTheme.colorScheme.onPrimary,
                    bgColor = MaterialTheme.colorScheme.primary,
                    onClick = {
                        // SEC-05: relationship is Uri.encode-wrapped before going into the query
                        // string (good) and, since it currently only ever comes from a fixed
                        // dropdown, this is already safe — the length cap is cheap insurance
                        // against a future free-text relationship field (FEAT-08) turning this
                        // into a way to build an oversized or malformed search URL.
                        val safeRelationship = Uri.encode(birthday.relationship.lowercase().take(60))
                        val searchIntent = Intent(Intent.ACTION_VIEW,
                            Uri.parse("https://www.google.com/search?q=birthday+gift+ideas+for+$safeRelationship"))
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
                remindersSummary?.let { DetailRow(label = stringResource(R.string.detail_label_reminder), value = it) }
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
    icon: androidx.compose.ui.graphics.painter.Painter,
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
