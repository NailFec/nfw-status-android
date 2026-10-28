package com.nailfec.nfwstatus.presentation.status

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nailfec.nfwstatus.R
import com.nailfec.nfwstatus.data.remote.NetworkResult
import com.nailfec.nfwstatus.data.remote.NfwRepository
import com.nailfec.nfwstatus.model.Level
import com.nailfec.nfwstatus.model.StatusSegment
import com.nailfec.nfwstatus.presentation.common.messageRes
import com.nailfec.nfwstatus.util.TimeFormat
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@HiltViewModel
class StatusViewModel @Inject constructor(
    private val repository: NfwRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(StatusUiState())
    val state: StateFlow<StatusUiState> = _state.asStateFlow()

    private val _effects = Channel<StatusUiEffect>(Channel.BUFFERED)
    val effects = _effects.receiveAsFlow()

    init {
        refresh()
    }

    fun onEvent(event: StatusUiEvent) {
        when (event) {
            is StatusUiEvent.LevelSelected -> _state.update { it.copy(level = event.level) }
            is StatusUiEvent.ContentChanged -> _state.update { it.copy(content = event.value) }
            is StatusUiEvent.PhraseClicked -> _state.update { it.copy(content = event.text) }
            is StatusUiEvent.PhraseRemoveRequested ->
                _state.update { it.copy(pendingDeletePhraseId = event.id) }
            StatusUiEvent.PhraseRemoveDismissed -> _state.update { it.copy(pendingDeletePhraseId = null) }
            StatusUiEvent.PhraseRemoveConfirmed -> confirmPhraseDelete()
            StatusUiEvent.Save -> save()
            StatusUiEvent.Refresh -> refresh()
            is StatusUiEvent.EditRequested -> openEditor(event.id)
            StatusUiEvent.EditDismissed -> _state.update { it.copy(editor = null, activePicker = null) }
            is StatusUiEvent.EditorLevelSelected -> updateEditor { it.copy(level = event.level) }
            is StatusUiEvent.EditorContentChanged -> updateEditor { it.copy(content = event.value) }
            is StatusUiEvent.OpenPicker -> _state.update { it.copy(activePicker = event.picker) }
            StatusUiEvent.DismissPicker -> _state.update { it.copy(activePicker = null) }
            is StatusUiEvent.DatePicked -> applyDate(event.utcMillis)
            is StatusUiEvent.TimePicked -> applyTime(event.hour, event.minute)
            StatusUiEvent.SaveEdit -> saveEdit()
            is StatusUiEvent.DeleteRequested -> _state.update { it.copy(pendingDeleteSegmentId = event.id) }
            StatusUiEvent.DeleteDismissed -> _state.update { it.copy(pendingDeleteSegmentId = null) }
            StatusUiEvent.DeleteConfirmed -> confirmDelete()
        }
    }

    private fun updateEditor(transform: (SegmentEditor) -> SegmentEditor) {
        _state.update { state ->
            val editor = state.editor ?: return@update state
            state.copy(editor = transform(editor))
        }
    }

    private fun refresh() {
        viewModelScope.launch { load() }
    }

    private suspend fun load() {
        _state.update { it.copy(isLoading = true) }
        val segments = repository.getSegments()
        val phrases = repository.getPhrases()
        _state.update { current ->
            current.copy(
                isLoading = false,
                segments = when (segments) {
                    is NetworkResult.Success -> segments.value.map { it.toUi() }
                    is NetworkResult.Failure -> current.segments
                },
                phrases = when (phrases) {
                    is NetworkResult.Success -> phrases.value.map { PhraseUi(it.id, it.text) }
                    is NetworkResult.Failure -> current.phrases
                },
            )
        }
        val failure = when {
            segments is NetworkResult.Failure -> segments.kind
            phrases is NetworkResult.Failure -> phrases.kind
            else -> null
        }
        if (failure != null) {
            _effects.send(StatusUiEffect.ShowMessage(failure.messageRes()))
        }
    }

    private fun save() {
        val snapshot = _state.value
        if (snapshot.isSaving) return
        viewModelScope.launch {
            _state.update { it.copy(isSaving = true) }
            when (val result = repository.createSegment(snapshot.level.wire, snapshot.content.trim())) {
                is NetworkResult.Success -> {
                    _state.update { it.copy(isSaving = false, content = "") }
                    _effects.send(StatusUiEffect.ShowMessage(R.string.message_saved))
                    load()
                }
                is NetworkResult.Failure -> {
                    _state.update { it.copy(isSaving = false) }
                    _effects.send(StatusUiEffect.ShowMessage(result.kind.messageRes()))
                }
            }
        }
    }

    private fun openEditor(id: String) {
        val segment = _state.value.segments.find { it.id == id } ?: return
        val start = TimeFormat.parse(segment.startedAt) ?: run {
            viewModelScope.launch { _effects.send(StatusUiEffect.ShowMessage(R.string.error_invalid)) }
            return
        }
        _state.update {
            it.copy(
                editor = SegmentEditor(
                    id = segment.id,
                    level = segment.level ?: Level.FREE,
                    content = segment.content,
                    startDate = start.date.toString(),
                    startHour = start.time.hour,
                    startMinute = start.time.minute,
                ),
                activePicker = null,
            )
        }
    }

    private fun applyDate(utcMillis: Long) {
        val date = TimeFormat.dateFromUtcMillis(utcMillis).toString()
        updateEditor { editor -> editor.copy(startDate = date) }
        _state.update { it.copy(activePicker = null) }
    }

    private fun applyTime(hour: Int, minute: Int) {
        updateEditor { editor -> editor.copy(startHour = hour, startMinute = minute) }
        _state.update { it.copy(activePicker = null) }
    }

    private fun saveEdit() {
        val editor = _state.value.editor ?: return
        if (_state.value.isSaving) return
        val startedAt = TimeFormat.toIso(editor.startDate, editor.startHour, editor.startMinute)
        if (startedAt == null) {
            viewModelScope.launch { _effects.send(StatusUiEffect.ShowMessage(R.string.error_invalid)) }
            return
        }
        viewModelScope.launch {
            _state.update { it.copy(isSaving = true) }
            when (
                val result = repository.updateSegment(
                    id = editor.id,
                    level = editor.level.wire,
                    content = editor.content.trim(),
                    startedAt = startedAt,
                )
            ) {
                is NetworkResult.Success -> {
                    _state.update { it.copy(isSaving = false, editor = null, activePicker = null) }
                    _effects.send(StatusUiEffect.ShowMessage(R.string.message_saved))
                    load()
                }
                is NetworkResult.Failure -> {
                    _state.update { it.copy(isSaving = false) }
                    _effects.send(StatusUiEffect.ShowMessage(result.kind.messageRes()))
                }
            }
        }
    }

    private fun confirmDelete() {
        val id = _state.value.pendingDeleteSegmentId ?: return
        viewModelScope.launch {
            _state.update { it.copy(pendingDeleteSegmentId = null, isSaving = true) }
            when (val result = repository.deleteSegment(id)) {
                is NetworkResult.Success -> {
                    _state.update { it.copy(isSaving = false) }
                    _effects.send(StatusUiEffect.ShowMessage(R.string.message_saved))
                    load()
                }
                is NetworkResult.Failure -> {
                    _state.update { it.copy(isSaving = false) }
                    _effects.send(StatusUiEffect.ShowMessage(result.kind.messageRes()))
                }
            }
        }
    }

    private fun confirmPhraseDelete() {
        val id = _state.value.pendingDeletePhraseId ?: return
        viewModelScope.launch {
            _state.update { it.copy(pendingDeletePhraseId = null) }
            when (val result = repository.deletePhrase(id)) {
                is NetworkResult.Success -> {
                    _effects.send(StatusUiEffect.ShowMessage(R.string.message_saved))
                    load()
                }
                is NetworkResult.Failure ->
                    _effects.send(StatusUiEffect.ShowMessage(result.kind.messageRes()))
            }
        }
    }

    private fun StatusSegment.toUi(): SegmentUi = SegmentUi(
        id = id,
        level = Level.fromWire(level),
        content = content,
        startLabel = TimeFormat.formatIso(startedAt),
        endLabel = endedAt?.let(TimeFormat::formatIso),
        startedAt = startedAt,
    )
}
