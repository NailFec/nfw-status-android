package com.nailfec.nfwstatus.presentation.meals

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.MediumFloatingActionButton
import androidx.compose.ui.Alignment
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.compose.LifecycleStartEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.SubcomposeAsyncImage
import com.nailfec.nfwstatus.R
import com.nailfec.nfwstatus.presentation.common.AppDatePickerDialog
import com.nailfec.nfwstatus.presentation.common.AppTimePickerDialog
import com.nailfec.nfwstatus.presentation.common.ConfirmDialog
import com.nailfec.nfwstatus.presentation.common.MealSlotSelector
import com.nailfec.nfwstatus.util.TimeFormat

@Composable
fun MealsScreen(
    onMessage: (String) -> Unit,
    viewModel: MealsViewModel = hiltViewModel(),
    modifier: Modifier = Modifier,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val addPhotos = rememberLauncherForActivityResult(
        ActivityResultContracts.PickMultipleVisualMedia(maxItems = 10),
    ) { uris ->
        if (uris.isNotEmpty()) {
            viewModel.onEvent(MealsUiEvent.PhotosPicked(uris.map(Uri::toString)))
        }
    }
    val editPhotos = rememberLauncherForActivityResult(
        ActivityResultContracts.PickMultipleVisualMedia(maxItems = 10),
    ) { uris ->
        if (uris.isNotEmpty()) {
            viewModel.onEvent(MealsUiEvent.EditPhotosPicked(uris.map(Uri::toString)))
        }
    }
    val pickRequest = PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
    LaunchedEffect(viewModel) {
        viewModel.effects.collect { effect ->
            when (effect) {
                is MealsUiEffect.ShowMessage -> onMessage(context.getString(effect.messageRes))
            }
        }
    }
    LifecycleStartEffect(viewModel) {
        viewModel.onEvent(MealsUiEvent.ScreenShown)
        onStopOrDispose { }
    }
    MealsContent(
        state = state,
        onEvent = viewModel::onEvent,
        onAddPhotos = { addPhotos.launch(pickRequest) },
        onEditPhotos = { editPhotos.launch(pickRequest) },
        modifier = modifier,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MealsContent(
    state: MealsUiState,
    onEvent: (MealsUiEvent) -> Unit,
    onAddPhotos: () -> Unit,
    onEditPhotos: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier.fillMaxSize()) {
        PullToRefreshBox(
            isRefreshing = state.isLoading,
            onRefresh = { onEvent(MealsUiEvent.Refresh) },
            modifier = Modifier.fillMaxSize(),
        ) {
            Column(Modifier.fillMaxSize()) {
                if (state.isSaving) {
                    LinearProgressIndicator(Modifier.fillMaxWidth())
                }
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(bottom = 96.dp),
                ) {
                    if (state.meals.isEmpty()) {
                        item(key = "empty", contentType = "empty") {
                            Text(
                                text = stringResource(R.string.empty_list),
                                modifier = Modifier.padding(horizontal = 16.dp),
                                style = MaterialTheme.typography.bodyLarge,
                            )
                        }
                    }
                    items(items = state.meals, key = { it.id }, contentType = { "meal" }) { meal ->
                        val onEdit = remember(meal.id) { { onEvent(MealsUiEvent.EditRequested(meal.id)) } }
                        val onDelete = remember(meal.id) { { onEvent(MealsUiEvent.DeleteRequested(meal.id)) } }
                        MealRow(meal = meal, onEdit = onEdit, onDelete = onDelete)
                    }
                }
            }
        }
        MediumFloatingActionButton(
            onClick = { onEvent(MealsUiEvent.ComposerOpened) },
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
    state.pendingDeleteId?.let {
        ConfirmDialog(
            title = stringResource(R.string.dialog_delete_meal),
            confirmLabel = stringResource(R.string.action_delete),
            onConfirm = { onEvent(MealsUiEvent.DeleteConfirmed) },
            onDismiss = { onEvent(MealsUiEvent.DeleteDismissed) },
        )
    }
    state.editor?.let { editor ->
        MealEditorSheet(
            editor = editor,
            saving = state.isSaving,
            onEvent = onEvent,
            onAddPhotos = onEditPhotos,
        )
    }
    if (state.composerOpen) {
        ModalBottomSheet(onDismissRequest = { onEvent(MealsUiEvent.ComposerDismissed) }) {
            MealComposer(
                state = state,
                onEvent = onEvent,
                onAddPhotos = onAddPhotos,
                modifier = Modifier.padding(bottom = 32.dp),
            )
        }
    }
    MealPickers(state = state, onEvent = onEvent)
}

@Composable
private fun MealComposer(
    state: MealsUiState,
    onEvent: (MealsUiEvent) -> Unit,
    onAddPhotos: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
            MealSlotSelector(
                selected = state.slot,
                onSelect = { onEvent(MealsUiEvent.SlotSelected(it)) },
            )
            DateTimeButtons(
                dateLabel = TimeFormat.formatDate(state.date),
                timeLabel = TimeFormat.formatTime(state.hour, state.minute),
                onDate = { onEvent(MealsUiEvent.OpenPicker(MealPicker.ComposerDate)) },
                onTime = { onEvent(MealsUiEvent.OpenPicker(MealPicker.ComposerTime)) },
            )
            if (state.photoUris.isNotEmpty()) {
                PendingPhotos(
                    uris = state.photoUris,
                    onRemove = { onEvent(MealsUiEvent.RemovePendingPhoto(it)) },
                )
            }
            OutlinedButton(onClick = onAddPhotos, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.action_add_photos))
            }
            Button(
                onClick = { onEvent(MealsUiEvent.Save) },
                enabled = !state.isSaving,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.action_save))
            }
    }
}

@Composable
private fun MealRow(
    meal: MealUi,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val slotLabel = stringResource(meal.slot?.labelRes ?: R.string.slot_unknown)
    val editLabel = stringResource(R.string.action_edit)
    Column(modifier = modifier.fillMaxWidth()) {
        ListItem(
            modifier = Modifier
                .clickable(onClickLabel = editLabel, onClick = onEdit)
                .semantics(mergeDescendants = true) {},
            headlineContent = { Text(slotLabel) },
            supportingContent = {
                val separator = stringResource(R.string.list_separator)
                Text(listOf(meal.dayLabel, meal.eatenLabel).joinToString(separator))
            },
            trailingContent = {
                IconButton(onClick = onDelete) {
                    Icon(
                        imageVector = Icons.Filled.Delete,
                        contentDescription = stringResource(R.string.action_delete),
                    )
                }
            },
        )
        if (meal.photos.isNotEmpty()) {
            LazyRow(
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(items = meal.photos, key = { it.id }, contentType = { "photo" }) { photo ->
                    MealImage(
                        model = photo.url,
                        contentDescription = stringResource(
                            R.string.meal_photo_description,
                            slotLabel,
                            meal.dayLabel,
                        ),
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MealEditorSheet(
    editor: MealEditor,
    saving: Boolean,
    onEvent: (MealsUiEvent) -> Unit,
    onAddPhotos: () -> Unit,
) {
    val slotLabel = stringResource(editor.slot.labelRes)
    val dayLabel = TimeFormat.formatDate(editor.date)
    ModalBottomSheet(onDismissRequest = { onEvent(MealsUiEvent.EditDismissed) }) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            MealSlotSelector(
                selected = editor.slot,
                onSelect = { onEvent(MealsUiEvent.EditorSlotSelected(it)) },
            )
            DateTimeButtons(
                dateLabel = dayLabel,
                timeLabel = TimeFormat.formatTime(editor.hour, editor.minute),
                onDate = { onEvent(MealsUiEvent.OpenPicker(MealPicker.EditorDate)) },
                onTime = { onEvent(MealsUiEvent.OpenPicker(MealPicker.EditorTime)) },
            )
            editor.photos.filterNot { it.id in editor.removedPhotoIds }.forEach { photo ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    MealImage(
                        model = photo.url,
                        contentDescription = stringResource(
                            R.string.meal_photo_description,
                            slotLabel,
                            dayLabel,
                        ),
                    )
                    IconButton(onClick = { onEvent(MealsUiEvent.RemoveExistingPhoto(photo.id)) }) {
                        Icon(
                            imageVector = Icons.Filled.Close,
                            contentDescription = stringResource(R.string.action_remove_photo),
                        )
                    }
                }
            }
            if (editor.newPhotoUris.isNotEmpty()) {
                PendingPhotos(
                    uris = editor.newPhotoUris,
                    onRemove = { onEvent(MealsUiEvent.RemoveEditorPhoto(it)) },
                )
            }
            OutlinedButton(onClick = onAddPhotos, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.action_add_photos))
            }
            Button(
                onClick = { onEvent(MealsUiEvent.SaveEdit) },
                enabled = !saving,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.action_save))
            }
        }
    }
}

@Composable
private fun PendingPhotos(
    uris: List<String>,
    onRemove: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        uris.forEach { uri ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                MealImage(
                    model = uri,
                    contentDescription = stringResource(R.string.selected_photo_description),
                )
                IconButton(onClick = { onRemove(uri) }) {
                    Icon(
                        imageVector = Icons.Filled.Close,
                        contentDescription = stringResource(R.string.action_remove_photo),
                    )
                }
            }
        }
    }
}

@Composable
private fun MealImage(
    model: String,
    contentDescription: String,
    modifier: Modifier = Modifier,
) {
    SubcomposeAsyncImage(
        model = model,
        contentDescription = contentDescription,
        modifier = modifier
            .size(96.dp)
            .clip(MaterialTheme.shapes.medium),
        contentScale = ContentScale.Crop,
        loading = {
            CircularProgressIndicator(modifier = Modifier.size(24.dp))
        },
    )
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
private fun MealPickers(
    state: MealsUiState,
    onEvent: (MealsUiEvent) -> Unit,
) {
    val picker = state.activePicker ?: return
    val editor = state.editor
    when (picker) {
        MealPicker.ComposerDate -> AppDatePickerDialog(
            initialUtcMillis = TimeFormat.dateToUtcMillis(state.date),
            onConfirm = { onEvent(MealsUiEvent.DatePicked(it)) },
            onDismiss = { onEvent(MealsUiEvent.DismissPicker) },
        )
        MealPicker.ComposerTime -> AppTimePickerDialog(
            hour = state.hour,
            minute = state.minute,
            onConfirm = { hour, minute -> onEvent(MealsUiEvent.TimePicked(hour, minute)) },
            onDismiss = { onEvent(MealsUiEvent.DismissPicker) },
        )
        MealPicker.EditorDate -> {
            if (editor != null) {
                AppDatePickerDialog(
                    initialUtcMillis = TimeFormat.dateToUtcMillis(editor.date),
                    onConfirm = { onEvent(MealsUiEvent.DatePicked(it)) },
                    onDismiss = { onEvent(MealsUiEvent.DismissPicker) },
                )
            }
        }
        MealPicker.EditorTime -> {
            if (editor != null) {
                AppTimePickerDialog(
                    hour = editor.hour,
                    minute = editor.minute,
                    onConfirm = { hour, minute -> onEvent(MealsUiEvent.TimePicked(hour, minute)) },
                    onDismiss = { onEvent(MealsUiEvent.DismissPicker) },
                )
            }
        }
    }
}
