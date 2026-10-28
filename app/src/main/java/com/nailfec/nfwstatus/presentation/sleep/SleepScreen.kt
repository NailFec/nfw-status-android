package com.nailfec.nfwstatus.presentation.sleep

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.health.connect.client.PermissionController
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.SleepSessionRecord
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nailfec.nfwstatus.R
import com.nailfec.nfwstatus.model.stageLabelRes
import com.nailfec.nfwstatus.presentation.common.AppDatePickerDialog
import com.nailfec.nfwstatus.presentation.common.AppTimePickerDialog
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
    PullToRefreshBox(
        isRefreshing = state.isLoading,
        onRefresh = { onEvent(SleepUiEvent.Refresh) },
        modifier = modifier.fillMaxSize(),
    ) {
        Column(Modifier.fillMaxSize()) {
            if (state.isSaving || state.isImporting) {
                LinearProgressIndicator(Modifier.fillMaxWidth())
            }
            SleepActions(state = state, onEvent = onEvent)
            LazyColumn(modifier = Modifier.weight(1f)) {
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
                        onEdit = { onEvent(SleepUiEvent.EditRequested(day.date)) },
                        onDeleteHealth = { onEvent(SleepUiEvent.DeleteHealthRequested(day.date)) },
                    )
                }
            }
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
    state.editor?.let { editor ->
        SleepEditorSheet(editor = editor, saving = state.isSaving, onEvent = onEvent)
    }
    SleepPickers(state = state, onEvent = onEvent)
}

@Composable
private fun SleepActions(
    state: SleepUiState,
    onEvent: (SleepUiEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(16.dp),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Button(
                onClick = { onEvent(SleepUiEvent.ImportRecent) },
                enabled = !state.isImporting,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.action_import_recent))
            }
            OutlinedButton(
                onClick = { onEvent(SleepUiEvent.ImportDateRequested) },
                enabled = !state.isImporting,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.action_import_date))
            }
            OutlinedButton(
                onClick = { onEvent(SleepUiEvent.OpenPicker(SleepPicker.ViewedDate)) },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(TimeFormat.formatDate(state.viewedDate))
            }
        }
    }
}

@Composable
private fun SleepDayRow(
    day: SleepDayUi,
    selected: Boolean,
    onSelect: () -> Unit,
    onEdit: () -> Unit,
    onDeleteHealth: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val hasHealth = day.bedtime != null || day.durationMinutes != null
    val summary = sleepSummary(day)
    Column(modifier = modifier.fillMaxWidth()) {
        ListItem(
            modifier = Modifier.clickable(onClick = onSelect),
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
                Row {
                    if (hasHealth) {
                        IconButton(onClick = onEdit) {
                            Icon(
                                imageVector = Icons.Filled.Edit,
                                contentDescription = stringResource(R.string.action_edit),
                            )
                        }
                        IconButton(onClick = onDeleteHealth) {
                            Icon(
                                imageVector = Icons.Filled.Delete,
                                contentDescription = stringResource(R.string.action_delete_sleep),
                            )
                        }
                    }
                }
            },
        )
    }
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SleepEditorSheet(
    editor: SleepEditor,
    saving: Boolean,
    onEvent: (SleepUiEvent) -> Unit,
) {
    ModalBottomSheet(onDismissRequest = { onEvent(SleepUiEvent.EditDismissed) }) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = TimeFormat.formatDate(editor.date),
                style = MaterialTheme.typography.titleMedium,
            )
            Text(stringResource(R.string.field_bedtime))
            DateTimeButtons(
                dateLabel = TimeFormat.formatDate(editor.bedDate),
                timeLabel = TimeFormat.formatTime(editor.bedHour, editor.bedMinute),
                onDate = { onEvent(SleepUiEvent.OpenPicker(SleepPicker.BedDate)) },
                onTime = { onEvent(SleepUiEvent.OpenPicker(SleepPicker.BedTime)) },
            )
            Text(stringResource(R.string.field_wake))
            DateTimeButtons(
                dateLabel = TimeFormat.formatDate(editor.wakeDate),
                timeLabel = TimeFormat.formatTime(editor.wakeHour, editor.wakeMinute),
                onDate = { onEvent(SleepUiEvent.OpenPicker(SleepPicker.WakeDate)) },
                onTime = { onEvent(SleepUiEvent.OpenPicker(SleepPicker.WakeTime)) },
            )
            OutlinedTextField(
                value = editor.durationText,
                onValueChange = { onEvent(SleepUiEvent.DurationChanged(it)) },
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.field_duration)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            )
            Button(
                onClick = { onEvent(SleepUiEvent.SaveEdit) },
                enabled = !saving,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.action_save))
            }
        }
    }
}

@Composable
private fun DateTimeButtons(
    dateLabel: String,
    timeLabel: String,
    onDate: () -> Unit,
    onTime: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(modifier = modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton(onClick = onDate, modifier = Modifier.weight(1f)) {
            Text(dateLabel, maxLines = 1)
        }
        OutlinedButton(onClick = onTime, modifier = Modifier.weight(1f)) {
            Text(timeLabel, maxLines = 1)
        }
    }
}

@Composable
private fun SleepPickers(
    state: SleepUiState,
    onEvent: (SleepUiEvent) -> Unit,
) {
    val picker = state.activePicker ?: return
    val editor = state.editor
    when (picker) {
        SleepPicker.ViewedDate, SleepPicker.ImportDate -> AppDatePickerDialog(
            initialUtcMillis = TimeFormat.dateToUtcMillis(state.viewedDate),
            onConfirm = { onEvent(SleepUiEvent.DatePicked(it)) },
            onDismiss = { onEvent(SleepUiEvent.DismissPicker) },
        )
        SleepPicker.BedDate -> editor?.let {
            AppDatePickerDialog(
                initialUtcMillis = TimeFormat.dateToUtcMillis(it.bedDate),
                onConfirm = { millis -> onEvent(SleepUiEvent.DatePicked(millis)) },
                onDismiss = { onEvent(SleepUiEvent.DismissPicker) },
            )
        }
        SleepPicker.WakeDate -> editor?.let {
            AppDatePickerDialog(
                initialUtcMillis = TimeFormat.dateToUtcMillis(it.wakeDate),
                onConfirm = { millis -> onEvent(SleepUiEvent.DatePicked(millis)) },
                onDismiss = { onEvent(SleepUiEvent.DismissPicker) },
            )
        }
        SleepPicker.BedTime -> editor?.let {
            AppTimePickerDialog(
                hour = it.bedHour,
                minute = it.bedMinute,
                onConfirm = { hour, minute -> onEvent(SleepUiEvent.TimePicked(hour, minute)) },
                onDismiss = { onEvent(SleepUiEvent.DismissPicker) },
            )
        }
        SleepPicker.WakeTime -> editor?.let {
            AppTimePickerDialog(
                hour = it.wakeHour,
                minute = it.wakeMinute,
                onConfirm = { hour, minute -> onEvent(SleepUiEvent.TimePicked(hour, minute)) },
                onDismiss = { onEvent(SleepUiEvent.DismissPicker) },
            )
        }
    }
}
