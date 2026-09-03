package com.glimmer.app.ui.screens

import java.time.format.TextStyle
import java.util.Locale
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.stickyHeader
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Cake
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.NotificationsNone
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.annotation.StringRes
import com.glimmer.app.R
import com.glimmer.app.data.birthMonthDay
import com.glimmer.app.ui.components.BirthdayAvatar
import com.glimmer.app.ui.components.NeumorphicButton
import com.glimmer.app.ui.components.NeumorphicIconButton
import com.glimmer.app.ui.components.NeumorphicSnackbarHost
import com.glimmer.app.ui.components.neumorphic
import com.glimmer.app.ui.components.rememberExactAlarmPermissionState
import com.glimmer.app.ui.components.rememberNotificationsPermissionState
import com.glimmer.app.viewmodel.BirthdayUi
import com.glimmer.app.viewmodel.GlimmerViewModel
import com.glimmer.app.viewmodel.HomeSortMode
import com.glimmer.app.viewmodel.UiEvent

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel: GlimmerViewModel,
    onNavigateToDetail: (Int) -> Unit,
    onNavigateToAdd: () -> Unit,
    onNavigateToNotifications: () -> Unit,
    onNavigateToProfile: () -> Unit,
    onNavigateToImport: () -> Unit
) {
    // PERF-02: today/upcoming partitioning and per-item daysUntil/age are now computed once in
    // the ViewModel (see GlimmerViewModel.homeUiState), not recomputed here on every
    // recomposition — a keystroke in the search box, the reminder banner being dismissed, the
    // delete-undo snackbar appearing, none of that used to be free.
    val uiState by viewModel.homeUiState.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    // FEAT-12: filter chips + sort toggle. Collected here (not inside a LazyColumn item {} block)
    // because LazyListScope's builder isn't itself a @Composable context — only item {}/items {}
    // bodies are, so collectAsState has to happen up here regardless of where it's rendered.
    val availableRelationships by viewModel.availableRelationships.collectAsState()
    val relationshipFilter by viewModel.relationshipFilter.collectAsState()
    val sortMode by viewModel.sortMode.collectAsState()

    // Reflects the REAL, current system permission state (unlike a stored preference, these can
    // change outside the app — e.g. the user revokes notifications from system Settings) so the
    // warning shows up whenever reminders genuinely can't fire, not just right after onboarding.
    val context = LocalContext.current
    val notificationsGranted by rememberNotificationsPermissionState(context)
    val exactAlarmsGranted by rememberExactAlarmPermissionState(context)
    var reminderWarningDismissed by remember { mutableStateOf(false) }
    val showReminderWarning = (!notificationsGranted || !exactAlarmsGranted) && !reminderWarningDismissed

    // BUG-33: deleting a birthday used to be instant and irreversible. deleteBirthday() still
    // deletes right away (so alarms/DB stay consistent immediately), but now emits an event this
    // screen turns into an Undo snackbar — undoDelete() re-inserts the row and re-arms its alarm.
    val snackbarHostState = remember { SnackbarHostState() }
    val undoDeleteMessageTemplate = stringResource(R.string.home_undo_delete_message)
    val undoActionLabel = stringResource(R.string.common_undo)
    LaunchedEffect(viewModel) {
        viewModel.deleteEvents.collect { name ->
            val result = snackbarHostState.showSnackbar(
                message = String.format(undoDeleteMessageTemplate, name),
                actionLabel = undoActionLabel,
                duration = SnackbarDuration.Long
            )
            if (result == SnackbarResult.ActionPerformed) {
                viewModel.undoDelete()
            }
        }
    }

    // Section 4.2: Add/Edit fire their save and navigate back in the same click without waiting
    // for the result (see AddBirthdayScreen/EditBirthdayScreen), so a failure has nowhere to
    // surface on the screen where it happened. GlimmerViewModel.events is the generic safety net
    // for exactly that — previously a failed save "looked identical to a successful one".
    // context.getString(...) (not stringResource(), which needs composable context) resolves the
    // event's resource id from inside the plain suspend collector below.
    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                is UiEvent.Error -> snackbarHostState.showSnackbar(context.getString(event.messageRes))
                is UiEvent.ImportSuccess -> snackbarHostState.showSnackbar(
                    context.resources.getQuantityString(R.plurals.import_success_snackbar, event.count, event.count)
                )
            }
        }
    }

    Scaffold(
        snackbarHost = { NeumorphicSnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        stringResource(R.string.app_name),
                        style = MaterialTheme.typography.headlineMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                },
                actions = {
                    // BUG: see NeumorphicIconButton's doc — its default shadow gets clipped by
                    // the TopAppBar's own Surface at this size unless it's reduced.
                    NeumorphicIconButton(
                        onClick = onNavigateToNotifications,
                        modifier = Modifier
                            .padding(end = 8.dp)
                            .size(36.dp),
                        cornerRadius = 18.dp,
                        elevation = 3.dp,
                        blur = 6.dp
                    ) {
                        Icon(
                            Icons.Default.NotificationsNone,
                            contentDescription = stringResource(R.string.home_cd_notifications),
                            tint = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    NeumorphicIconButton(
                        onClick = onNavigateToProfile,
                        modifier = Modifier
                            .padding(end = 16.dp)
                            .size(36.dp),
                        cornerRadius = 18.dp,
                        elevation = 3.dp,
                        blur = 6.dp,
                        shapeBackgroundColor = MaterialTheme.colorScheme.primary
                    ) {
                        Icon(
                            Icons.Default.Person,
                            contentDescription = stringResource(R.string.home_cd_profile),
                            tint = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        floatingActionButton = {
            NeumorphicIconButton(
                onClick = onNavigateToAdd,
                modifier = Modifier.size(64.dp),
                cornerRadius = 32.dp,
                shapeBackgroundColor = MaterialTheme.colorScheme.primary
            ) {
                Icon(
                    Icons.Default.Add,
                    contentDescription = stringResource(R.string.home_cd_add_birthday),
                    tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(28.dp)
                )
            }
        },
        containerColor = MaterialTheme.colorScheme.surface
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            item { Spacer(modifier = Modifier.height(8.dp)) }

            // ── Search Bar ──────────────────────────────────────────────────
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .neumorphic(isSunken = true, cornerRadius = 26.dp, shapeBackgroundColor = MaterialTheme.colorScheme.surface)
                        .padding(horizontal = 20.dp),
                    contentAlignment = Alignment.CenterStart
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Default.Search, contentDescription = null, tint = MaterialTheme.colorScheme.outline)
                        Spacer(modifier = Modifier.width(12.dp))
                        androidx.compose.foundation.text.BasicTextField(
                            value = searchQuery,
                            onValueChange = { viewModel.searchQuery.value = it },
                            textStyle = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.onSurface),
                            singleLine = true,
                            modifier = Modifier.weight(1f),
                            decorationBox = { inner ->
                                if (searchQuery.isEmpty()) {
                                    Text(
                                        stringResource(R.string.home_search_placeholder),
                                        color = MaterialTheme.colorScheme.outlineVariant,
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                }
                                inner()
                            }
                        )
                    }
                }
            }

            // ── Reminder health warning ────────────────────────────────────
            if (showReminderWarning) {
                item {
                    ReminderHealthBanner(
                        onFixClick = onNavigateToNotifications,
                        onDismiss = { reminderWarningDismissed = true }
                    )
                }
            }

            // FEAT-12: relationship filter chips + date/name sort toggle — only worth showing
            // once there's more than one relationship in play at all.
            if (availableRelationships.size > 1) {
                item {
                    FilterAndSortRow(
                        relationships = availableRelationships,
                        selectedFilter = relationshipFilter,
                        onFilterChange = { viewModel.relationshipFilter.value = it },
                        sortMode = sortMode,
                        onToggleSort = {
                            viewModel.sortMode.value = if (sortMode == HomeSortMode.DATE) HomeSortMode.NAME else HomeSortMode.DATE
                        }
                    )
                }
            }

            // ── Today's Birthdays ─────────────────────────────────────────
            if (uiState.today.isNotEmpty()) {
                item {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Cake, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            stringResource(R.string.home_section_today),
                            style = MaterialTheme.typography.headlineMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }

                items(uiState.today, key = { it.birthday.id }) { item ->
                    TodayBirthdayCard(
                        item = item,
                        onClick = { onNavigateToDetail(item.birthday.id) },
                        onToggleFavorite = { viewModel.toggleFavorite(item.birthday.id) }
                    )
                }
            }

            // ── Favorites ───────────────────────────────────────────────────
            if (uiState.favorites.isNotEmpty()) {
                item { HomeSectionHeader(stringResource(R.string.home_section_favorites)) }
                items(uiState.favorites, key = { it.birthday.id }) { item ->
                    UpcomingBirthdayCard(
                        item = item,
                        onClick = { onNavigateToDetail(item.birthday.id) },
                        onToggleFavorite = { viewModel.toggleFavorite(item.birthday.id) }
                    )
                }
            }

            // ── This Week / This Month / Later — FEAT-12's sticky-header grouping ──
            homeSection(
                titleRes = R.string.home_section_this_week,
                items = uiState.thisWeek,
                onNavigateToDetail = onNavigateToDetail,
                onToggleFavorite = { viewModel.toggleFavorite(it) }
            )
            homeSection(
                titleRes = R.string.home_section_this_month,
                items = uiState.thisMonth,
                onNavigateToDetail = onNavigateToDetail,
                onToggleFavorite = { viewModel.toggleFavorite(it) }
            )
            homeSection(
                titleRes = R.string.home_section_later,
                items = uiState.later,
                onNavigateToDetail = onNavigateToDetail,
                onToggleFavorite = { viewModel.toggleFavorite(it) }
            )

            if (uiState.isEmpty) {
                item { HomeSectionHeader(stringResource(R.string.home_section_none_yet)) }
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .neumorphic(cornerRadius = 20.dp, shapeBackgroundColor = MaterialTheme.colorScheme.surface)
                            .padding(32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Icon(Icons.Default.Cake, contentDescription = null, tint = MaterialTheme.colorScheme.outline, modifier = Modifier.size(48.dp))
                            Text(stringResource(R.string.home_empty_title), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.outline)
                            Text(stringResource(R.string.home_empty_subtitle), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.outlineVariant)
                            // FEAT-02: realistically nobody types in 60 people by hand — this is
                            // the single biggest lever on whether the app ever gets used at all.
                            TextButton(onClick = onNavigateToImport) {
                                Text(stringResource(R.string.home_import_contacts), color = MaterialTheme.colorScheme.primary)
                            }
                        }
                    }
                }
            }

            item { Spacer(modifier = Modifier.height(96.dp)) }
        }
    }
}

/**
 * Shown when the app can't reliably fire reminders — either POST_NOTIFICATIONS is blocked, or
 * exact alarms aren't available (see NotificationScheduler.canScheduleExactAlarms). This is the
 * one place the real, current permission state surfaces; it's not a one-time onboarding prompt,
 * since either permission can be revoked from system Settings at any time after being granted.
 */
@Composable
private fun ReminderHealthBanner(onFixClick: () -> Unit, onDismiss: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .neumorphic(cornerRadius = 16.dp, shapeBackgroundColor = MaterialTheme.colorScheme.errorContainer)
            .padding(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                Icons.Default.WarningAmber,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(stringResource(R.string.home_reminder_warning_title), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onErrorContainer)
                Text(
                    stringResource(R.string.home_reminder_warning_subtitle),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onErrorContainer
                )
            }
            TextButton(onClick = onFixClick) {
                Text(stringResource(R.string.home_reminder_warning_fix), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelLarge)
            }
            IconButton(onClick = onDismiss) {
                Icon(
                    Icons.Default.Close,
                    contentDescription = stringResource(R.string.home_cd_dismiss),
                    tint = MaterialTheme.colorScheme.onErrorContainer,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

/**
 * FEAT-12: This Week/This Month/Later, each as a sticky header (stays pinned while its own
 * people scroll past underneath — the same reason any app groups a long list this way) followed
 * by that bucket's cards. A no-op (renders nothing, not even the header) when the bucket is
 * empty, so an active relationship filter that empties out "Later" doesn't leave a floating
 * header with nothing under it.
 */
@OptIn(ExperimentalFoundationApi::class)
private fun LazyListScope.homeSection(
    @StringRes titleRes: Int,
    items: List<BirthdayUi>,
    onNavigateToDetail: (Int) -> Unit,
    onToggleFavorite: (Int) -> Unit
) {
    if (items.isEmpty()) return
    stickyHeader {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surface)
                .padding(vertical = 8.dp)
        ) {
            HomeSectionHeader(stringResource(titleRes))
        }
    }
    items(items, key = { it.birthday.id }) { item ->
        UpcomingBirthdayCard(
            item = item,
            onClick = { onNavigateToDetail(item.birthday.id) },
            onToggleFavorite = { onToggleFavorite(item.birthday.id) }
        )
    }
}

@Composable
private fun HomeSectionHeader(title: String) {
    Text(title, style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun FilterAndSortRow(
    relationships: List<String>,
    selectedFilter: String?,
    onFilterChange: (String?) -> Unit,
    sortMode: HomeSortMode,
    onToggleSort: () -> Unit
) {
    Column {
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FilterChip(selected = selectedFilter == null, onClick = { onFilterChange(null) }, label = { Text(stringResource(R.string.home_filter_all)) })
            relationships.forEach { rel ->
                FilterChip(selected = selectedFilter == rel, onClick = { onFilterChange(rel) }, label = { Text(rel) })
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            TextButton(onClick = onToggleSort) {
                Icon(Icons.AutoMirrored.Filled.Sort, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    if (sortMode == HomeSortMode.DATE) stringResource(R.string.home_sort_by_date) else stringResource(R.string.home_sort_by_name),
                    style = MaterialTheme.typography.labelMedium
                )
            }
        }
    }
}

@Composable
private fun FavoriteToggle(isFavorite: Boolean, onToggle: () -> Unit, modifier: Modifier = Modifier) {
    IconButton(onClick = onToggle, modifier = modifier.size(32.dp)) {
        Icon(
            if (isFavorite) Icons.Default.Star else Icons.Default.StarBorder,
            contentDescription = stringResource(if (isFavorite) R.string.home_cd_unfavorite else R.string.home_cd_favorite),
            tint = if (isFavorite) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.outline,
            modifier = Modifier.size(20.dp)
        )
    }
}

@Composable
private fun TodayBirthdayCard(item: BirthdayUi, onClick: () -> Unit, onToggleFavorite: () -> Unit) {
    val birthday = item.birthday
    val age = item.ageTurning
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .neumorphic(cornerRadius = 20.dp, shapeBackgroundColor = MaterialTheme.colorScheme.surface)
            .clickable(onClick = onClick)
            .padding(20.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .neumorphic(cornerRadius = 32.dp, shapeBackgroundColor = MaterialTheme.colorScheme.surface)
                        .padding(4.dp)
                ) {
                    BirthdayAvatar(photoUri = birthday.photoUri, name = birthday.name, modifier = Modifier.fillMaxSize())
                }
                Spacer(modifier = Modifier.width(16.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(birthday.name, style = MaterialTheme.typography.headlineMedium)
                    Text(
                        // FEAT-05: age is unknowable without a birth year — show a plain
                        // celebration line instead of "Turning null today!".
                        if (age != null) stringResource(R.string.home_turning_today, age) else stringResource(R.string.home_celebrating_today),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.secondary
                    )
                    Text(birthday.relationship, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.outline)
                }
                // FEAT-12
                FavoriteToggle(isFavorite = birthday.isFavorite, onToggle = onToggleFavorite)
            }

            NeumorphicButton(
                onClick = onClick,
                modifier = Modifier.fillMaxWidth().height(48.dp),
                cornerRadius = 12.dp
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.AutoMirrored.Filled.Send, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(stringResource(R.string.home_send_wish), color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelLarge)
                }
            }
        }
    }
}

@Composable
private fun UpcomingBirthdayCard(item: BirthdayUi, onClick: () -> Unit, onToggleFavorite: () -> Unit) {
    val birthday = item.birthday
    val monthDay = birthday.birthMonthDay()
    val dayStr = "${monthDay.month.getDisplayName(TextStyle.SHORT, Locale.getDefault())} ${monthDay.dayOfMonth}"
    val daysLeft = item.daysUntil
    val daysLabel = when (daysLeft) {
        0 -> stringResource(R.string.days_until_today)
        1 -> stringResource(R.string.days_until_tomorrow)
        else -> pluralStringResource(R.plurals.days_until_n, daysLeft, daysLeft)
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .neumorphic(cornerRadius = 16.dp, shapeBackgroundColor = MaterialTheme.colorScheme.surface)
            .clickable(onClick = onClick)
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .neumorphic(isSunken = true, cornerRadius = 24.dp, shapeBackgroundColor = MaterialTheme.colorScheme.surface)
                        .padding(4.dp),
                    contentAlignment = Alignment.Center
                ) {
                    BirthdayAvatar(photoUri = birthday.photoUri, name = birthday.name, modifier = Modifier.fillMaxSize())
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(birthday.name, style = MaterialTheme.typography.labelLarge)
                        // FEAT-12: "these are the ones worth planning for" — a small badge rather
                        // than anything louder, since it's a nice-to-notice, not a warning.
                        if (item.isMilestone) {
                            Spacer(modifier = Modifier.width(6.dp))
                            Box(
                                modifier = Modifier
                                    .neumorphic(cornerRadius = 8.dp, shapeBackgroundColor = MaterialTheme.colorScheme.tertiaryContainer)
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    stringResource(R.string.home_milestone_badge, item.ageTurning ?: 0),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.tertiary
                                )
                            }
                        }
                    }
                    Text("$dayStr · ${birthday.relationship}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                }
            }
            FavoriteToggle(isFavorite = birthday.isFavorite, onToggle = onToggleFavorite)
            Spacer(modifier = Modifier.width(4.dp))
            // Days-away badge
            Box(
                modifier = Modifier
                    .neumorphic(cornerRadius = 12.dp, shapeBackgroundColor = MaterialTheme.colorScheme.primaryContainer)
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(daysLabel, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
            }
        }
    }
}
