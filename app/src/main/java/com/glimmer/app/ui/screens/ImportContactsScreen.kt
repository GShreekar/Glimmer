package com.glimmer.app.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Cake
import androidx.compose.material.icons.filled.Contacts
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.glimmer.app.R
import com.glimmer.app.data.Birthday
import com.glimmer.app.data.ContactBirthdayCandidate
import com.glimmer.app.data.ContactsBirthdayImporter
import com.glimmer.app.ui.components.NeumorphicButton
import com.glimmer.app.ui.components.NeumorphicIconButton
import com.glimmer.app.ui.components.neumorphic
import com.glimmer.app.viewmodel.GlimmerViewModel
import kotlinx.coroutines.launch
import java.time.format.TextStyle
import java.util.Locale

/**
 * FEAT-02: a reviewable bulk import — every contact with a saved birthday is listed with a
 * checkbox (already-added people pre-deselected and marked), the user can deselect any of them,
 * and only then does anything get written. Never a silent "imported 60 contacts" surprise.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImportContactsScreen(
    viewModel: GlimmerViewModel,
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    var permissionGranted by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED
        )
    }
    var permissionDenied by remember { mutableStateOf(false) }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        permissionGranted = granted
        permissionDenied = !granted
    }

    var loading by remember { mutableStateOf(false) }
    var loaded by remember { mutableStateOf(false) }
    var candidates by remember { mutableStateOf<List<ContactBirthdayCandidate>>(emptyList()) }
    var alreadyAdded by remember { mutableStateOf<List<Boolean>>(emptyList()) }
    var selected by remember { mutableStateOf<Set<Int>>(emptySet()) }
    var importing by remember { mutableStateOf(false) }

    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()

    LaunchedEffect(permissionGranted) {
        if (permissionGranted && !loaded) {
            loading = true
            val found = ContactsBirthdayImporter.findBirthdays(context)
                .distinctBy { it.name to it.dateOfBirth }
            val exists = found.map { viewModel.isDuplicateBirthday(it.name, it.dateOfBirth) }
            candidates = found
            alreadyAdded = exists
            selected = found.indices.filterNot { exists[it] }.toSet()
            loading = false
            loaded = true
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.import_title), style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.primary) },
                navigationIcon = {
                    NeumorphicIconButton(
                        onClick = onNavigateBack,
                        modifier = Modifier.padding(start = 12.dp).size(40.dp),
                        cornerRadius = 20.dp
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.import_cd_back), tint = MaterialTheme.colorScheme.onSurface)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = MaterialTheme.colorScheme.surface
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            when {
                !permissionGranted -> {
                    Column(
                        modifier = Modifier.fillMaxSize().padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Box(
                            modifier = Modifier.size(96.dp).neumorphic(cornerRadius = 48.dp, shapeBackgroundColor = MaterialTheme.colorScheme.surface),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.Contacts, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(40.dp))
                        }
                        Spacer(modifier = Modifier.height(20.dp))
                        Text(
                            stringResource(R.string.import_permission_heading),
                            style = MaterialTheme.typography.headlineMedium,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            stringResource(R.string.import_permission_rationale),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(24.dp))
                        NeumorphicButton(
                            onClick = { permissionLauncher.launch(Manifest.permission.READ_CONTACTS) },
                            modifier = Modifier.fillMaxWidth().height(52.dp),
                            cornerRadius = 12.dp
                        ) {
                            Text(stringResource(R.string.import_permission_button), color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.headlineMedium)
                        }
                        if (permissionDenied) {
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                stringResource(R.string.import_permission_denied),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.error,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }

                loading -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(stringResource(R.string.import_loading), color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }

                candidates.isEmpty() -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(stringResource(R.string.import_empty), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }

                else -> {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            pluralStringResource(R.plurals.import_selected_count, selected.size, selected.size),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        val selectableIndices = candidates.indices.filterNot { alreadyAdded.getOrElse(it) { false } }
                        val allSelected = selectableIndices.isNotEmpty() && selectableIndices.all { it in selected }
                        TextButton(onClick = {
                            selected = if (allSelected) emptySet() else selectableIndices.toSet()
                        }) {
                            Text(stringResource(if (allSelected) R.string.import_deselect_all else R.string.import_select_all))
                        }
                    }

                    LazyColumn(
                        modifier = Modifier.weight(1f).padding(horizontal = 20.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(candidates.indices.toList()) { index ->
                            val candidate = candidates[index]
                            val exists = alreadyAdded.getOrElse(index) { false }
                            val isSelected = index in selected
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .neumorphic(
                                        isSunken = isSelected,
                                        cornerRadius = 14.dp,
                                        shapeBackgroundColor = MaterialTheme.colorScheme.surface
                                    )
                                    .clickable(enabled = !exists) {
                                        selected = if (isSelected) selected - index else selected + index
                                    }
                                    .padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Checkbox(checked = isSelected, onCheckedChange = null, enabled = !exists)
                                Spacer(modifier = Modifier.width(8.dp))
                                Icon(Icons.Default.Cake, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                                Spacer(modifier = Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(candidate.name, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                                    val monthDay = java.time.Instant.ofEpochMilli(candidate.dateOfBirth)
                                        .atZone(java.time.ZoneOffset.UTC).toLocalDate()
                                    val dateLabel = monthDay.month.getDisplayName(TextStyle.SHORT, Locale.getDefault()) + " " + monthDay.dayOfMonth +
                                        (candidate.birthYear?.let { ", $it" } ?: "")
                                    Text(dateLabel, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                                }
                                if (exists) {
                                    Text(
                                        stringResource(R.string.import_already_added),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.outline
                                    )
                                }
                            }
                        }
                        item { Spacer(modifier = Modifier.height(8.dp)) }
                    }

                    Column(modifier = Modifier.fillMaxWidth().padding(20.dp)) {
                        NeumorphicButton(
                            onClick = {
                                if (selected.isEmpty() || importing) return@NeumorphicButton
                                importing = true
                                val toImport = selected.map { index ->
                                    val c = candidates[index]
                                    Birthday(
                                        name = c.name,
                                        dateOfBirth = c.dateOfBirth,
                                        birthYear = c.birthYear,
                                        relationship = "Friend",
                                        reminderEnabled = true,
                                        contactLookupKey = c.contactLookupKey
                                    )
                                }
                                coroutineScope.launch {
                                    // This screen (and its SnackbarHost) is torn down by the
                                    // navigate-back below, so the "N imported" confirmation goes
                                    // out on the same shared events channel BUG-33's delete-undo
                                    // snackbar uses — HomeScreen is still around to show it.
                                    viewModel.importBirthdays(toImport)
                                    importing = false
                                    onNavigateBack()
                                }
                            },
                            modifier = Modifier.fillMaxWidth().height(56.dp),
                            cornerRadius = 12.dp
                        ) {
                            if (importing) {
                                CircularProgressIndicator(
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(24.dp),
                                    strokeWidth = 2.dp
                                )
                            } else {
                                Text(
                                    pluralStringResource(R.plurals.import_button, selected.size, selected.size),
                                    color = MaterialTheme.colorScheme.primary,
                                    style = MaterialTheme.typography.headlineMedium
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
