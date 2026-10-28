package com.nailfec.nfwstatus.presentation.sleep

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.History
import androidx.compose.material3.FloatingActionButtonMenu
import androidx.compose.material3.FloatingActionButtonMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.ToggleFloatingActionButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.health.connect.client.PermissionController
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.SleepSessionRecord
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nailfec.nfwstatus.R
import com.nailfec.nfwstatus.model.stageLabelRes
import com.nailfec.nfwstatus.presentation.common.AppDatePickerDialog
import com.nailfec.nfwstatus.presentation.common.ConfirmDialog
import com.nailfec.nfwstatus.util.TimeFormat

@Composable
fun SleepScreen(
    onMessage: (String) -> Unit,
    viewModel: SleepViewModel = hiltViewModel(),
    modifier: Modifier = Modifier,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val readPermission = HealthPermission.getReadPermission(SleepSessionRecord::class)
    val permissionLauncher = rememberLauncherForActivityResult(
        PermissionController.createRequestPermissionResultContract(),
    ) { granted ->
        viewModel.onEvent(SleepUiEvent.PermissionResult(granted.contains(readPermission)))
    }
    LaunchedEffect(viewModel) {
        viewModel.effects.collect { effect ->
            when (effect) {
                is SleepUiEffect.ShowMessage -> onMessage(context.getString(effect.messageRes))
                SleepUiEffect.RequestPermission -> permissionLauncher.launch(setOf(readPermission))
            }
        }
    }
    SleepContent(
        state = state,
        onEvent = viewModel::onEvent,
        modifier = modifier,
    )
}

@Composable
private fun SleepContent(
    state: SleepUiState,
    onEvent: (SleepUiEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    var menuOpen by rememberSaveable { mutableStateOf(false) }
    Box(modifier.fillMaxSize()) {
        PullToRefreshBox(
            isRefreshing = state.isLoading,
            onRefresh = { onEvent(SleepUiEvent.Refresh) },
            modifier = Modifier.fillMaxSize(),
        ) {
            Column(Modifier.fillMaxSize()) {
                ImportProgressBar(state)
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(bottom = 96.dp),
                ) {
                    if (state.days.isEmpty()) {
                        item(key = "empty", contentType = "empty") {
                            Text(
                                text = stringResource(R.string.empty_list),
                                modifier = Modifier.padding(horizontal = 16.dp),
                                style = MaterialTheme.typography.bodyLarge,
                            )
                        }
                    }
                    items(items = state.days, key = { it.date }, contentType = { "sleep" }) { day ->
                        SleepDayRow(
                            day = day,
                            selected = day.date == state.viewedDate,
                            onSelect = { onEvent(SleepUiEvent.SelectDate(day.date)) },
                            onDeleteHealth = { onEvent(SleepUiEvent.DeleteHealthRequested(day.date)) },
                        )
                    }
                }
            }
        }
        FloatingActionButtonMenu(
            expanded = menuOpen,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp),
            button = {
                ToggleFloatingActionButton(
                    checked = menuOpen,
                    onCheckedChange = { menuOpen = it },
                ) {
                    Icon(
                        imageVector = Icons.Filled.Add,
                        contentDescription = stringResource(R.string.action_add),
                    )
                }
            },
        ) {
            FloatingActionButtonMenuItem(
                onClick = {
                    menuOpen = false
                    onEvent(SleepUiEvent.ImportRecent)
                },
                icon = { Icon(Icons.Filled.History, contentDescription = null) },
                text = { Text(stringResource(R.string.action_import_recent)) },
            )
            FloatingActionButtonMenuItem(
                onClick = {
                    menuOpen = false
                    onEvent(SleepUiEvent.ImportAll)
                },
                icon = { Icon(Icons.Filled.CloudDownload, contentDescription = null) },
                text = { Text(stringResource(R.string.action_import_all)) },
            )
            FloatingActionButtonMenuItem(
                onClick = {
                    menuOpen = false
                    onEvent(SleepUiEvent.ImportDateRequested)
                },
                icon = { Icon(Icons.Filled.CalendarMonth, contentDescription = null) },
                text = { Text(stringResource(R.string.action_import_date)) },
            )
        }
    }
    state.pendingDeleteDate?.let {
        ConfirmDialog(
            title = stringResource(R.string.dialog_delete_sleep),
            confirmLabel = stringResource(R.string.action_delete),
            onConfirm = { onEvent(SleepUiEvent.DeleteHealthConfirmed) },
            onDismiss = { onEvent(SleepUiEvent.DeleteHealthDismissed) },
        )
    }
    if (state.activePicker == SleepPicker.ImportDate) {
        AppDatePickerDialog(
            initialUtcMillis = TimeFormat.dateToUtcMillis(state.viewedDate),
            onConfirm = { onEvent(SleepUiEvent.DatePicked(it)) },
            onDismiss = { onEvent(SleepUiEvent.DismissPicker) },
        )
    }
}

@Composable
private fun ImportProgressBar(state: SleepUiState) {
    val progress = state.importProgress
    when {
        progress != null && !progress.reading && progress.total > 0 -> {
            Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
                LinearProgressIndicator(
                    progress = { progress.completed / progress.total.toFloat() },
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    text = stringResource(R.string.import_progress, progress.completed, progress.total),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        }
        state.isSaving || state.isImporting -> LinearProgressIndicator(Modifier.fillMaxWidth())
    }
}

@Composable
private fun SleepDayRow(
    day: SleepDayUi,
    selected: Boolean,
    onSelect: () -> Unit,
    onDeleteHealth: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val hasHealth = day.bedtime != null || day.durationMinutes != null
    val summary = sleepSummary(day)
    ListItem(
        modifier = modifier.clickable(onClick = onSelect),
        headlineContent = { Text(day.dateLabel) },
        supportingContent = {
            if (summary.isNotEmpty()) Text(summary)
        },
        colors = if (selected) {
            ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
        } else {
            ListItemDefaults.colors()
        },
        trailingContent = {
            if (hasHealth) {
                IconButton(onClick = onDeleteHealth) {
                    Icon(
                        imageVector = Icons.Filled.Delete,
                        contentDescription = stringResource(R.string.action_delete_sleep),
                    )
                }
            }
        },
    )
}

@Composable
private fun sleepSummary(day: SleepDayUi): String {
    val separator = stringResource(R.string.list_separator)
    val parts = buildList {
        day.durationMinutes?.let { add(durationLabel(it)) }
        val bed = day.bedtimeLabel
        val wake = day.wakeLabel
        if (bed != null && wake != null) {
            add(stringResource(R.string.time_range, bed, wake))
        }
        if (day.stages.isNotEmpty()) {
            val names = day.stages.distinct().map { stringResource(stageLabelRes(it)) }
            add(names.joinToString(separator))
        }
    }
    return parts.joinToString(separator)
}

@Composable
private fun durationLabel(minutes: Int): String {
    if (minutes < 0) return stringResource(R.string.duration_minutes, minutes)
    val hours = minutes / 60
    val rest = minutes % 60
    return if (hours > 0) {
        stringResource(R.string.duration_hours_minutes, hours, rest)
    } else {
        stringResource(R.string.duration_minutes, rest)
    }
}
