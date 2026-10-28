package com.nailfec.nfwstatus.presentation.sleep

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nailfec.nfwstatus.R
import com.nailfec.nfwstatus.data.health.SleepHealthReader
import com.nailfec.nfwstatus.data.health.SleepRead
import com.nailfec.nfwstatus.data.remote.NetworkResult
import com.nailfec.nfwstatus.data.remote.NfwRepository
import com.nailfec.nfwstatus.model.SleepDay
import com.nailfec.nfwstatus.model.SleepUpload
import com.nailfec.nfwstatus.presentation.common.messageRes
import com.nailfec.nfwstatus.util.TimeFormat
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.LocalDate
import javax.inject.Inject
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@HiltViewModel
class SleepViewModel @Inject constructor(
    private val repository: NfwRepository,
    private val health: SleepHealthReader,
) : ViewModel() {
    private val _state = MutableStateFlow(SleepUiState(viewedDate = TimeFormat.today().toString()))
    val state = _state.asStateFlow()

    private val _effects = Channel<SleepUiEffect>(Channel.BUFFERED)
    val effects = _effects.receiveAsFlow()

    init {
        refresh()
    }

    fun onEvent(event: SleepUiEvent) {
        when (event) {
            SleepUiEvent.Refresh -> refresh()
            SleepUiEvent.ImportRecent -> beginImport(PendingImport.Recent)
            SleepUiEvent.ImportAll -> beginImport(PendingImport.All)
            SleepUiEvent.ImportDateRequested -> _state.update { it.copy(activePicker = SleepPicker.ImportDate) }
            is SleepUiEvent.PermissionResult -> onPermission(event.granted)
            SleepUiEvent.DismissPicker -> _state.update { it.copy(activePicker = null) }
            is SleepUiEvent.DatePicked -> applyDate(event.utcMillis)
            is SleepUiEvent.SelectDate -> _state.update { it.copy(viewedDate = event.date) }
            is SleepUiEvent.DeleteHealthRequested -> _state.update { it.copy(pendingDeleteDate = event.date) }
            SleepUiEvent.DeleteHealthDismissed -> _state.update { it.copy(pendingDeleteDate = null) }
            SleepUiEvent.DeleteHealthConfirmed -> confirmDeleteHealth()
        }
    }

    private fun refresh() {
        viewModelScope.launch { load() }
    }

    private suspend fun load() {
        _state.update { it.copy(isLoading = true) }
        when (val result = repository.getSleep()) {
            is NetworkResult.Success -> _state.update {
                it.copy(isLoading = false, days = result.value.map { day -> day.toUi() })
            }
            is NetworkResult.Failure -> {
                _state.update { it.copy(isLoading = false) }
                _effects.send(SleepUiEffect.ShowMessage(result.kind.messageRes()))
            }
        }
    }

    private fun beginImport(mode: PendingImport) {
        if (_state.value.isImporting) return
        viewModelScope.launch {
            if (!health.isAvailable()) {
                _effects.send(SleepUiEffect.ShowMessage(R.string.error_health_unavailable))
                return@launch
            }
            if (!health.hasPermission()) {
                _state.update { it.copy(pendingImport = mode) }
                _effects.send(SleepUiEffect.RequestPermission)
                return@launch
            }
            runImport(mode)
        }
    }

    private fun onPermission(granted: Boolean) {
        val mode = _state.value.pendingImport
        _state.update { it.copy(pendingImport = null) }
        if (!granted) {
            viewModelScope.launch {
                _effects.send(SleepUiEffect.ShowMessage(R.string.error_permission_denied))
            }
            return
        }
        if (mode == null) return
        viewModelScope.launch { runImport(mode) }
    }

    private suspend fun runImport(mode: PendingImport) {
        _state.update {
            it.copy(
                isImporting = true,
                importProgress = if (mode == PendingImport.All) {
                    ImportProgress(reading = true, completed = 0, total = 0)
                } else {
                    null
                },
            )
        }
        val read = when (mode) {
            PendingImport.Recent -> health.readRecent()
            PendingImport.All -> health.readAll()
            PendingImport.Date -> {
                val date = runCatching { LocalDate.parse(_state.value.viewedDate) }.getOrNull()
                if (date == null) {
                    _state.update { it.copy(isImporting = false, importProgress = null) }
                    _effects.send(SleepUiEffect.ShowMessage(R.string.error_invalid))
                    return
                }
                health.readWakeDate(date)
            }
        }
        when (read) {
            is SleepRead.Found -> {
                if (mode == PendingImport.All) uploadMissing(read.uploads) else uploadAll(read.uploads)
            }
            SleepRead.Empty -> finishImport(R.string.error_no_sleep)
            SleepRead.Unavailable -> finishImport(R.string.error_health_unavailable)
            SleepRead.Denied -> finishImport(R.string.error_permission_denied)
        }
    }

    private suspend fun uploadMissing(uploads: List<SleepUpload>) {
        val existing = when (val dates = repository.getSleepDates()) {
            is NetworkResult.Success -> dates.value
            is NetworkResult.Failure -> {
                _state.update { it.copy(isImporting = false, importProgress = null) }
                _effects.send(SleepUiEffect.ShowMessage(dates.kind.messageRes()))
                return
            }
        }
        val pending = uploads.filter { it.date !in existing }
        if (pending.isEmpty()) {
            finishImport(R.string.message_no_new_sleep)
            return
        }
        _state.update {
            it.copy(importProgress = ImportProgress(reading = false, completed = 0, total = pending.size))
        }
        pending.forEachIndexed { index, upload ->
            when (val result = repository.upsertSleep(upload)) {
                is NetworkResult.Success -> _state.update { state ->
                    state.copy(
                        importProgress = ImportProgress(
                            reading = false,
                            completed = index + 1,
                            total = pending.size,
                        ),
                    )
                }
                is NetworkResult.Failure -> {
                    _state.update { it.copy(isImporting = false, importProgress = null) }
                    _effects.send(SleepUiEffect.ShowMessage(result.kind.messageRes()))
                    load()
                    return
                }
            }
        }
        _state.update { it.copy(isImporting = false, importProgress = null) }
        _effects.send(SleepUiEffect.ShowMessage(R.string.message_saved))
        load()
    }

    private suspend fun uploadAll(uploads: List<SleepUpload>) {
        for (upload in uploads) {
            when (val result = repository.upsertSleep(upload)) {
                is NetworkResult.Success -> Unit
                is NetworkResult.Failure -> {
                    _state.update { it.copy(isImporting = false, importProgress = null) }
                    _effects.send(SleepUiEffect.ShowMessage(result.kind.messageRes()))
                    load()
                    return
                }
            }
        }
        _state.update { it.copy(isImporting = false, importProgress = null) }
        _effects.send(SleepUiEffect.ShowMessage(R.string.message_saved))
        load()
    }

    private suspend fun finishImport(messageRes: Int) {
        _state.update { it.copy(isImporting = false, importProgress = null) }
        _effects.send(SleepUiEffect.ShowMessage(messageRes))
    }

    private fun applyDate(utcMillis: Long) {
        val date = TimeFormat.dateFromUtcMillis(utcMillis).toString()
        if (_state.value.activePicker != SleepPicker.ImportDate) {
            _state.update { it.copy(activePicker = null) }
            return
        }
        _state.update { it.copy(viewedDate = date, activePicker = null) }
        beginImport(PendingImport.Date)
    }

    private fun confirmDeleteHealth() {
        val date = _state.value.pendingDeleteDate ?: return
        viewModelScope.launch {
            _state.update { it.copy(pendingDeleteDate = null, isSaving = true) }
            when (val result = repository.deleteSleep(date)) {
                is NetworkResult.Success -> {
                    _state.update { it.copy(isSaving = false) }
                    _effects.send(SleepUiEffect.ShowMessage(R.string.message_saved))
                    load()
                }
                is NetworkResult.Failure -> {
                    _state.update { it.copy(isSaving = false) }
                    _effects.send(SleepUiEffect.ShowMessage(result.kind.messageRes()))
                }
            }
        }
    }

    private fun SleepDay.toUi(): SleepDayUi = SleepDayUi(
        date = date,
        dateLabel = TimeFormat.formatDate(date),
        bedtime = bedtime,
        wakeTime = wakeTime,
        bedtimeLabel = bedtime?.let(TimeFormat::formatIso),
        wakeLabel = wakeTime?.let(TimeFormat::formatIso),
        durationMinutes = durationMinutes,
        stages = stages.map { it.stage },
    )
}
