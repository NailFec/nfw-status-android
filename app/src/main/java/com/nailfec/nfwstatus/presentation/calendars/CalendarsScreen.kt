package com.nailfec.nfwstatus.presentation.calendars

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nailfec.nfwstatus.R
import com.nailfec.nfwstatus.presentation.common.ConfirmDialog

@Composable
fun CalendarsScreen(
    onMessage: (String) -> Unit,
    onBindRefresh: (() -> Unit) -> Unit,
    onBindAdd: (() -> Unit) -> Unit,
    viewModel: CalendarsViewModel = hiltViewModel(),
    modifier: Modifier = Modifier,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    LaunchedEffect(viewModel) {
        viewModel.effects.collect { effect ->
            when (effect) {
                is CalendarsUiEffect.ShowMessage -> onMessage(context.getString(effect.messageRes))
            }
        }
    }
    DisposableEffect(onBindRefresh, onBindAdd, viewModel) {
        onBindRefresh { viewModel.onEvent(CalendarsUiEvent.Refresh) }
        onBindAdd { viewModel.onEvent(CalendarsUiEvent.AddOpened) }
        onDispose { }
    }
    CalendarsContent(state = state, onEvent = viewModel::onEvent, modifier = modifier)
}

@Composable
private fun CalendarsContent(
    state: CalendarsUiState,
    onEvent: (CalendarsUiEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    PullToRefreshBox(
        isRefreshing = state.isLoading,
        onRefresh = { onEvent(CalendarsUiEvent.Refresh) },
        modifier = modifier.fillMaxSize(),
    ) {
        Column(Modifier.fillMaxSize()) {
            if (state.isSaving) {
                LinearProgressIndicator(Modifier.fillMaxWidth())
            }
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                if (state.subscriptions.isEmpty()) {
                    item(key = "empty", contentType = "empty") {
                        Text(
                            text = stringResource(R.string.empty_list),
                            modifier = Modifier.padding(16.dp),
                            style = MaterialTheme.typography.bodyLarge,
                        )
                    }
                }
                items(
                    items = state.subscriptions,
                    key = { it.id },
                    contentType = { "calendar" },
                ) { item ->
                    val onDelete = remember(item.id) {
                        { onEvent(CalendarsUiEvent.DeleteRequested(item.id)) }
                    }
                    CalendarRow(item = item, onDelete = onDelete)
                }
            }
        }
    }
    if (state.showAdd) {
        AlertDialog(
            onDismissRequest = { onEvent(CalendarsUiEvent.AddDismissed) },
            title = { Text(stringResource(R.string.dialog_add_calendar)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = state.draftName,
                        onValueChange = { onEvent(CalendarsUiEvent.NameChanged(it)) },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text(stringResource(R.string.field_name)) },
                        singleLine = true,
                    )
                    OutlinedTextField(
                        value = state.draftUrl,
                        onValueChange = { onEvent(CalendarsUiEvent.UrlChanged(it)) },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text(stringResource(R.string.field_url)) },
                        singleLine = true,
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = { onEvent(CalendarsUiEvent.AddConfirmed) },
                    enabled = !state.isSaving,
                ) {
                    Text(stringResource(R.string.action_add))
                }
            },
            dismissButton = {
                TextButton(onClick = { onEvent(CalendarsUiEvent.AddDismissed) }) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
        )
    }
    state.pendingDeleteId?.let {
        ConfirmDialog(
            title = stringResource(R.string.dialog_delete_calendar),
            confirmLabel = stringResource(R.string.action_delete),
            onConfirm = { onEvent(CalendarsUiEvent.DeleteConfirmed) },
            onDismiss = { onEvent(CalendarsUiEvent.DeleteDismissed) },
        )
    }
}

@Composable
private fun CalendarRow(
    item: CalendarUi,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    ListItem(
        modifier = modifier,
        headlineContent = { Text(item.name) },
        supportingContent = {
            Column {
                Text(item.url)
                val error = item.lastError
                if (!error.isNullOrBlank()) {
                    Text(error, color = MaterialTheme.colorScheme.error)
                }
            }
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
}

@Composable
fun CalendarAddIcon(modifier: Modifier = Modifier) {
    Icon(
        imageVector = Icons.Filled.Add,
        contentDescription = stringResource(R.string.action_add),
        modifier = modifier,
    )
}
