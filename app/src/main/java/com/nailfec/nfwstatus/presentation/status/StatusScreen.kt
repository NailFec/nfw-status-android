package com.nailfec.nfwstatus.presentation.status

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nailfec.nfwstatus.R
import com.nailfec.nfwstatus.presentation.common.AppDatePickerDialog
import com.nailfec.nfwstatus.presentation.common.AppTimePickerDialog
import com.nailfec.nfwstatus.presentation.common.ConfirmDialog
import com.nailfec.nfwstatus.presentation.common.LevelDot
import com.nailfec.nfwstatus.presentation.common.LevelSelector
import com.nailfec.nfwstatus.util.TimeFormat

@Composable
fun StatusScreen(
    onMessage: (String) -> Unit,
    onBindRefresh: (() -> Unit) -> Unit,
    viewModel: StatusViewModel = hiltViewModel(),
    modifier: Modifier = Modifier,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    LaunchedEffect(viewModel) {
        viewModel.effects.collect { effect ->
            when (effect) {
                is StatusUiEffect.ShowMessage -> onMessage(context.getString(effect.messageRes))
            }
        }
    }
    DisposableEffect(onBindRefresh, viewModel) {
        onBindRefresh { viewModel.onEvent(StatusUiEvent.Refresh) }
        onDispose { }
    }
    StatusContent(
        state = state,
        onEvent = viewModel::onEvent,
        modifier = modifier,
    )
}

@Composable
private fun StatusContent(
    state: StatusUiState,
    onEvent: (StatusUiEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    PullToRefreshBox(
        isRefreshing = state.isLoading,
        onRefresh = { onEvent(StatusUiEvent.Refresh) },
        modifier = modifier.fillMaxSize(),
    ) {
        Column(Modifier.fillMaxSize()) {
            if (state.isSaving) {
                LinearProgressIndicator(Modifier.fillMaxWidth())
            }
            StatusComposer(state = state, onEvent = onEvent)
            LazyColumn(modifier = Modifier.weight(1f)) {
                if (state.segments.isEmpty()) {
                    item(key = "empty", contentType = "empty") {
                        Text(
                            text = stringResource(R.string.empty_list),
                            modifier = Modifier.padding(horizontal = 16.dp),
                            style = MaterialTheme.typography.bodyLarge,
                        )
                    }
                }
                items(
                    items = state.segments,
                    key = { it.id },
                    contentType = { "segment" },
                ) { segment ->
                    val onEdit = remember(segment.id) {
                        { onEvent(StatusUiEvent.EditRequested(segment.id)) }
                    }
                    val onDelete = remember(segment.id) {
                        { onEvent(StatusUiEvent.DeleteRequested(segment.id)) }
                    }
                    SegmentRow(segment = segment, onEdit = onEdit, onDelete = onDelete)
                }
            }
        }
    }
    state.pendingDeletePhraseId?.let {
        ConfirmDialog(
            title = stringResource(R.string.dialog_remove_phrase),
            confirmLabel = stringResource(R.string.action_remove),
            onConfirm = { onEvent(StatusUiEvent.PhraseRemoveConfirmed) },
            onDismiss = { onEvent(StatusUiEvent.PhraseRemoveDismissed) },
        )
    }
    state.pendingDeleteSegmentId?.let {
        ConfirmDialog(
            title = stringResource(R.string.dialog_delete_status),
            confirmLabel = stringResource(R.string.action_delete),
            onConfirm = { onEvent(StatusUiEvent.DeleteConfirmed) },
            onDismiss = { onEvent(StatusUiEvent.DeleteDismissed) },
        )
    }
    state.editor?.let { editor ->
        EditorSheet(editor = editor, saving = state.isSaving, onEvent = onEvent)
    }
    PickerHost(state = state, onEvent = onEvent)
}

@Composable
private fun StatusComposer(
    state: StatusUiState,
    onEvent: (StatusUiEvent) -> Unit,
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
            LevelSelector(
                selected = state.level,
                onSelect = { onEvent(StatusUiEvent.LevelSelected(it)) },
            )
            OutlinedTextField(
                value = state.content,
                onValueChange = { onEvent(StatusUiEvent.ContentChanged(it)) },
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.field_content)) },
                minLines = 2,
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Sentences,
                ),
            )
            if (state.phrases.isNotEmpty()) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    state.phrases.forEach { phrase ->
                        PhraseChip(
                            phrase = phrase,
                            onUse = { onEvent(StatusUiEvent.PhraseClicked(phrase.text)) },
                            onRemove = { onEvent(StatusUiEvent.PhraseRemoveRequested(phrase.id)) },
                        )
                    }
                }
            }
            Button(
                onClick = { onEvent(StatusUiEvent.Save) },
                enabled = !state.isSaving,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.action_save))
            }
        }
    }
}

@Composable
private fun PhraseChip(
    phrase: PhraseUi,
    onUse: () -> Unit,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SuggestionChip(
            onClick = onUse,
            label = { Text(phrase.text, maxLines = 1) },
        )
        IconButton(onClick = onRemove) {
            Icon(
                imageVector = Icons.Filled.Close,
                contentDescription = stringResource(R.string.action_remove_phrase),
            )
        }
    }
}

@Composable
private fun SegmentRow(
    segment: SegmentUi,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val end = segment.endLabel
    val range = if (end == null) {
        stringResource(R.string.time_range_open, segment.startLabel)
    } else {
        stringResource(R.string.time_range, segment.startLabel, end)
    }
    val headline = segment.content.ifBlank {
        stringResource(segment.level?.labelRes ?: R.string.level_unknown)
    }
    val editLabel = stringResource(R.string.action_edit)
    ListItem(
        modifier = modifier
            .clickable(onClickLabel = editLabel, onClick = onEdit)
            .semantics(mergeDescendants = true) {},
        headlineContent = { Text(headline) },
        supportingContent = { Text(range) },
        leadingContent = { LevelDot(level = segment.level, decorative = false) },
        trailingContent = {
            IconButton(onClick = onDelete) {
                Icon(
                    imageVector = Icons.Filled.Delete,
                    contentDescription = stringResource(R.string.action_delete),
                )
            }
        },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EditorSheet(
    editor: SegmentEditor,
    saving: Boolean,
    onEvent: (StatusUiEvent) -> Unit,
) {
    ModalBottomSheet(onDismissRequest = { onEvent(StatusUiEvent.EditDismissed) }) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            LevelSelector(
                selected = editor.level,
                onSelect = { onEvent(StatusUiEvent.EditorLevelSelected(it)) },
            )
            OutlinedTextField(
                value = editor.content,
                onValueChange = { onEvent(StatusUiEvent.EditorContentChanged(it)) },
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.field_content)) },
                minLines = 2,
            )
            Text(stringResource(R.string.action_pick_start))
            DateTimeButtons(
                dateLabel = TimeFormat.formatDate(editor.startDate),
                timeLabel = TimeFormat.formatTime(editor.startHour, editor.startMinute),
                onDate = { onEvent(StatusUiEvent.OpenPicker(StatusPicker.StartDate)) },
                onTime = { onEvent(StatusUiEvent.OpenPicker(StatusPicker.StartTime)) },
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.switch_still_current))
                Switch(
                    checked = editor.stillCurrent,
                    onCheckedChange = { onEvent(StatusUiEvent.StillCurrentChanged(it)) },
                )
            }
            if (!editor.stillCurrent) {
                Text(stringResource(R.string.action_pick_end))
                DateTimeButtons(
                    dateLabel = TimeFormat.formatDate(editor.endDate),
                    timeLabel = TimeFormat.formatTime(editor.endHour, editor.endMinute),
                    onDate = { onEvent(StatusUiEvent.OpenPicker(StatusPicker.EndDate)) },
                    onTime = { onEvent(StatusUiEvent.OpenPicker(StatusPicker.EndTime)) },
                )
            }
            Button(
                onClick = { onEvent(StatusUiEvent.SaveEdit) },
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
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        OutlinedButton(onClick = onDate, modifier = Modifier.weight(1f)) {
            Text(dateLabel, maxLines = 1)
        }
        OutlinedButton(onClick = onTime, modifier = Modifier.weight(1f)) {
            Text(timeLabel, maxLines = 1)
        }
    }
}

@Composable
private fun PickerHost(
    state: StatusUiState,
    onEvent: (StatusUiEvent) -> Unit,
) {
    val editor = state.editor
    val picker = state.activePicker
    if (editor == null || picker == null) return
    when (picker) {
        StatusPicker.StartDate -> AppDatePickerDialog(
            initialUtcMillis = TimeFormat.dateToUtcMillis(editor.startDate),
            onConfirm = { onEvent(StatusUiEvent.DatePicked(it)) },
            onDismiss = { onEvent(StatusUiEvent.DismissPicker) },
        )
        StatusPicker.EndDate -> AppDatePickerDialog(
            initialUtcMillis = TimeFormat.dateToUtcMillis(editor.endDate),
            onConfirm = { onEvent(StatusUiEvent.DatePicked(it)) },
            onDismiss = { onEvent(StatusUiEvent.DismissPicker) },
        )
        StatusPicker.StartTime -> AppTimePickerDialog(
            hour = editor.startHour,
            minute = editor.startMinute,
            onConfirm = { hour, minute -> onEvent(StatusUiEvent.TimePicked(hour, minute)) },
            onDismiss = { onEvent(StatusUiEvent.DismissPicker) },
        )
        StatusPicker.EndTime -> AppTimePickerDialog(
            hour = editor.endHour,
            minute = editor.endMinute,
            onConfirm = { hour, minute -> onEvent(StatusUiEvent.TimePicked(hour, minute)) },
            onDismiss = { onEvent(StatusUiEvent.DismissPicker) },
        )
    }
}
