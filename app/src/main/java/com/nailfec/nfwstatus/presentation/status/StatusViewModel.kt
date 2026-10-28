package com.nailfec.nfwstatus.presentation.status

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nailfec.nfwstatus.R
import com.nailfec.nfwstatus.data.remote.NetworkResult
import com.nailfec.nfwstatus.data.remote.NfwRepository
import com.nailfec.nfwstatus.model.HistoryPage
import com.nailfec.nfwstatus.model.Level
import com.nailfec.nfwstatus.model.StatusSegment
import com.nailfec.nfwstatus.presentation.common.messageRes
import com.nailfec.nfwstatus.util.TimeFormat
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
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
            StatusUiEvent.LoadOlder -> loadOlder()
            StatusUiEvent.ComposerOpened -> _state.update { it.copy(composerOpen = true) }
            StatusUiEvent.ComposerDismissed -> _state.update { it.copy(composerOpen = false, activePicker = null) }
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
        viewModelScope.launch { load(reset = true) }
    }

    private fun loadOlder() {
        viewModelScope.launch { load(reset = false) }
    }

    private suspend fun load(reset: Boolean) {
        val snapshot = _state.value
        if (!reset && (!snapshot.hasMore || snapshot.isLoadingMore || snapshot.isLoading)) return
        val end = if (reset) {
            null
        } else {
            val oldest = snapshot.days.lastOrNull()?.day ?: return
            LocalDate.parse(oldest).minusDays(1).toString()
        }
        _state.update {
            if (reset) it.copy(isLoading = true) else it.copy(isLoadingMore = true)
        }
        val history = repository.getHistory(end)
        val phrases = if (reset) repository.getPhrases() else null
        _state.update { current ->
            when (history) {
                is NetworkResult.Success -> {
                    val built = builtPage(history.value)
                    current.copy(
                        isLoading = false,
                        isLoadingMore = false,
                        hasMore = history.value.hasMore,
                        current = if (reset) built.current else current.current,
                        days = if (reset) built.days else current.days + built.days,
                        segments = if (reset) built.segments else mergeSegments(current.segments, built.segments),
                        phrases = when (phrases) {
                            is NetworkResult.Success -> phrases.value.map { PhraseUi(it.id, it.text) }
                            else -> current.phrases
                        },
                    )
                }
                is NetworkResult.Failure -> current.copy(isLoading = false, isLoadingMore = false)
            }
        }
        val failure = when {
            history is NetworkResult.Failure -> history.kind
            phrases is NetworkResult.Failure -> phrases.kind
            else -> null
        }
        if (failure != null) {
            _effects.send(StatusUiEffect.ShowMessage(failure.messageRes()))
        }
    }

    private fun builtPage(page: HistoryPage): BuiltPage {
        val zone = runCatching { ZoneId.of(page.timezone) }.getOrDefault(ZoneId.systemDefault())
        val now = TimeFormat.parseInstant(page.now) ?: Instant.now()
        val days = page.days.asReversed().map { day ->
            StatusDayUi(
                day = day.day,
                label = TimeFormat.formatDate(day.day),
                slices = slicesFor(day.day, zone, day.segments, now),
            )
        }
        val segments = page.days
            .flatMap { it.segments }
            .distinctBy { it.id }
            .map { it.toUi() }
        val current = page.current?.let {
            CurrentStatusUi(
                id = it.id,
                level = Level.fromWire(it.level),
                content = it.content,
                startedAt = it.startedAt,
                startLabel = TimeFormat.formatIso(it.startedAt),
            )
        }
        return BuiltPage(days, segments, current)
    }

    private fun slicesFor(
        day: String,
        zone: ZoneId,
        segments: List<StatusSegment>,
        now: Instant,
    ): List<StatusSliceUi> {
        val date = runCatching { LocalDate.parse(day) }.getOrNull() ?: return emptyList()
        val start = date.atStartOfDay(zone).toInstant()
        val end = date.plusDays(1).atStartOfDay(zone).toInstant()
        val span = Duration.between(start, end).toMillis().toFloat().coerceAtLeast(1f)
        return segments.mapNotNull { segment ->
            val segmentStart = TimeFormat.parseInstant(segment.startedAt) ?: return@mapNotNull null
            val segmentEnd = segment.endedAt?.let(TimeFormat::parseInstant) ?: now
            val visibleStart = if (segmentStart.isAfter(start)) segmentStart else start
            val visibleEnd = if (segmentEnd.isBefore(end)) segmentEnd else end
            if (!visibleEnd.isAfter(visibleStart)) return@mapNotNull null
            StatusSliceUi(
                id = segment.id,
                level = Level.fromWire(segment.level),
                start = (Duration.between(start, visibleStart).toMillis() / span).coerceIn(0f, 1f),
                end = (Duration.between(start, visibleEnd).toMillis() / span).coerceIn(0f, 1f),
            )
        }
    }

    private fun mergeSegments(existing: List<SegmentUi>, extra: List<SegmentUi>): List<SegmentUi> {
        val merged = LinkedHashMap<String, SegmentUi>()
        for (segment in existing) merged[segment.id] = segment
        for (segment in extra) merged[segment.id] = segment
        return merged.values.toList()
    }

    private fun save() {
        val snapshot = _state.value
        if (snapshot.isSaving) return
        viewModelScope.launch {
            _state.update { it.copy(isSaving = true) }
            when (val result = repository.createSegment(snapshot.level.wire, snapshot.content.trim())) {
                is NetworkResult.Success -> {
                    _state.update { it.copy(isSaving = false, content = "", composerOpen = false) }
                    _effects.send(StatusUiEffect.ShowMessage(R.string.message_saved))
                    load(reset = true)
                }
                is NetworkResult.Failure -> {
                    _state.update { it.copy(isSaving = false) }
                    _effects.send(StatusUiEffect.ShowMessage(result.kind.messageRes()))
                }
            }
        }
    }

    private fun openEditor(id: String) {
        val segment = _state.value.segments.find { it.id == id }
        val current = _state.value.current?.takeIf { it.id == id }
        val startedAt = segment?.startedAt ?: current?.startedAt ?: return
        val level = segment?.level ?: current?.level
        val content = segment?.content ?: current?.content ?: ""
        val start = TimeFormat.parse(startedAt) ?: run {
            viewModelScope.launch { _effects.send(StatusUiEffect.ShowMessage(R.string.error_invalid)) }
            return
        }
        _state.update {
            it.copy(
                editor = SegmentEditor(
                    id = id,
                    level = level ?: Level.FREE,
                    content = content,
                    startDate = start.date.toString(),
                    startHour = start.time.hour,
                    startMinute = start.time.minute,
                    startSecond = start.time.second,
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
        val startedAt = TimeFormat.toIso(
            editor.startDate,
            editor.startHour,
            editor.startMinute,
            editor.startSecond,
        )
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
                    load(reset = true)
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
            _state.update { it.copy(pendingDeleteSegmentId = null, isSaving = true, editor = null, activePicker = null) }
            when (val result = repository.deleteSegment(id)) {
                is NetworkResult.Success -> {
                    _state.update { it.copy(isSaving = false) }
                    _effects.send(StatusUiEffect.ShowMessage(R.string.message_saved))
                    load(reset = true)
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
                    load(reset = true)
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

private data class BuiltPage(
    val days: List<StatusDayUi>,
    val segments: List<SegmentUi>,
    val current: CurrentStatusUi?,
)
}
