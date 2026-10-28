package com.nailfec.nfwstatus.presentation.status

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MediumFloatingActionButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nailfec.nfwstatus.R
import com.nailfec.nfwstatus.presentation.common.AppDatePickerDialog
import com.nailfec.nfwstatus.presentation.common.AppTimePickerDialog
import com.nailfec.nfwstatus.presentation.common.ConfirmDialog
import com.nailfec.nfwstatus.presentation.common.LevelSelector
import com.nailfec.nfwstatus.util.TimeFormat

@Composable
fun StatusScreen(
    onMessage: (String) -> Unit,
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
    Box(modifier.fillMaxSize()) {
        PullToRefreshBox(
            isRefreshing = state.isLoading,
            onRefresh = { onEvent(StatusUiEvent.Refresh) },
            modifier = Modifier.fillMaxSize(),
        ) {
            Column(Modifier.fillMaxSize()) {
                if (state.isSaving) {
                    LinearProgressIndicator(Modifier.fillMaxWidth())
                }
                CurrentStatusCard(
                    current = state.current,
                    onEdit = {
                        val id = state.current?.id ?: return@CurrentStatusCard
                        onEvent(StatusUiEvent.EditRequested(id))
                    },
                )
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(bottom = 96.dp),
                ) {
                    itemsIndexed(
                        items = state.days,
                        key = { _, day -> day.day },
                        contentType = { _, _ -> "day" },
                    ) { index, day ->
                        if (index == state.days.lastIndex && state.hasMore) {
                            LaunchedEffect(day.day) {
                                onEvent(StatusUiEvent.LoadOlder)
                            }
                        }
                        DayRangeRow(
                            day = day,
                            onEdit = { onEvent(StatusUiEvent.EditRequested(it)) },
                        )
                    }
                    if (state.isLoadingMore) {
                        item(key = "more", contentType = "more") {
                            LinearProgressIndicator(Modifier.fillMaxWidth())
                        }
                    }
                }
            }
        }
        MediumFloatingActionButton(
            onClick = { onEvent(StatusUiEvent.ComposerOpened) },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp),
        ) {
            Icon(
                imageVector = Icons.Filled.Add,
                contentDescription = stringResource(R.string.action_add),
            )
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
    if (state.composerOpen) {
        ComposerSheet(state = state, onEvent = onEvent)
    }
    PickerHost(state = state, onEvent = onEvent)
}

@Composable
private fun CurrentStatusCard(
    current: CurrentStatusUi?,
    onEdit: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clickable(enabled = current != null, onClick = onEdit)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        if (current == null) {
            Text(
                text = stringResource(R.string.empty_list),
                style = MaterialTheme.typography.bodyLarge,
            )
        } else {
            Text(
                text = stringResource(current.level?.labelRes ?: R.string.level_unknown),
                style = MaterialTheme.typography.titleMedium,
            )
            if (current.content.isNotBlank()) {
                Text(current.content, style = MaterialTheme.typography.bodyLarge)
            }
            Text(
                text = stringResource(R.string.time_range_open, current.startLabel),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun DayRangeRow(
    day: StatusDayUi,
    onEdit: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = day.label,
            modifier = Modifier.width(96.dp),
            style = MaterialTheme.typography.labelMedium,
            maxLines = 2,
        )
        BoxWithConstraints(
            modifier = Modifier
                .weight(1f)
                .height(28.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
        ) {
            val track = maxWidth
            day.slices.forEach { slice ->
                val width = track * (slice.end - slice.start).coerceAtLeast(0.01f)
                Box(
                    modifier = Modifier
                        .offset(x = track * slice.start)
                        .width(width)
                        .fillMaxHeight()
                        .background(slice.level?.color ?: MaterialTheme.colorScheme.outline)
                        .clickable { onEdit(slice.id) },
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ComposerSheet(
    state: StatusUiState,
    onEvent: (StatusUiEvent) -> Unit,
) {
    ModalBottomSheet(onDismissRequest = { onEvent(StatusUiEvent.ComposerDismissed) }) {
        StatusComposer(
            state = state,
            onEvent = onEvent,
            modifier = Modifier.padding(bottom = 32.dp),
        )
    }
}

@Composable
private fun StatusComposer(
    state: StatusUiState,
    onEvent: (StatusUiEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
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
            Button(
                onClick = { onEvent(StatusUiEvent.SaveEdit) },
                enabled = !saving,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.action_save))
            }
            TextButton(
                onClick = { onEvent(StatusUiEvent.DeleteRequested(editor.id)) },
                enabled = !saving,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.action_delete))
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
        StatusPicker.StartTime -> AppTimePickerDialog(
            hour = editor.startHour,
            minute = editor.startMinute,
            onConfirm = { hour, minute -> onEvent(StatusUiEvent.TimePicked(hour, minute)) },
            onDismiss = { onEvent(StatusUiEvent.DismissPicker) },
        )
    }
}
