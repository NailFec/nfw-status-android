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
            SleepUiEvent.ImportDateRequested -> _state.update { it.copy(activePicker = SleepPicker.ImportDate) }
            is SleepUiEvent.PermissionResult -> onPermission(event.granted)
            is SleepUiEvent.OpenPicker -> _state.update { it.copy(activePicker = event.picker) }
            SleepUiEvent.DismissPicker -> _state.update { it.copy(activePicker = null) }
            is SleepUiEvent.DatePicked -> applyDate(event.utcMillis)
            is SleepUiEvent.TimePicked -> applyTime(event.hour, event.minute)
            is SleepUiEvent.DurationChanged -> updateEditor { it.copy(durationText = event.value) }
            is SleepUiEvent.SelectDate -> _state.update { it.copy(viewedDate = event.date) }
            is SleepUiEvent.EditRequested -> openEditor(event.date)
            SleepUiEvent.EditDismissed -> _state.update { it.copy(editor = null, activePicker = null) }
            SleepUiEvent.SaveEdit -> saveEdit()
            is SleepUiEvent.DeleteHealthRequested -> _state.update { it.copy(pendingDeleteDate = event.date) }
            SleepUiEvent.DeleteHealthDismissed -> _state.update { it.copy(pendingDeleteDate = null) }
            SleepUiEvent.DeleteHealthConfirmed -> confirmDeleteHealth()
        }
    }

    private fun updateEditor(transform: (SleepEditor) -> SleepEditor) {
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
        _state.update { it.copy(isImporting = true) }
        val read = when (mode) {
            PendingImport.Recent -> health.readRecent()
            PendingImport.Date -> {
                val date = runCatching { LocalDate.parse(_state.value.viewedDate) }.getOrNull()
                if (date == null) {
                    _state.update { it.copy(isImporting = false) }
                    _effects.send(SleepUiEffect.ShowMessage(R.string.error_invalid))
                    return
                }
                health.readWakeDate(date)
            }
        }
        when (read) {
            is SleepRead.Found -> uploadAll(read.uploads)
            SleepRead.Empty -> finishImport(R.string.error_no_sleep)
            SleepRead.Unavailable -> finishImport(R.string.error_health_unavailable)
            SleepRead.Denied -> finishImport(R.string.error_permission_denied)
        }
    }

    private suspend fun uploadAll(uploads: List<SleepUpload>) {
        for (upload in uploads) {
            when (val result = repository.upsertSleep(upload)) {
                is NetworkResult.Success -> Unit
                is NetworkResult.Failure -> {
                    _state.update { it.copy(isImporting = false) }
                    _effects.send(SleepUiEffect.ShowMessage(result.kind.messageRes()))
                    load()
                    return
                }
            }
        }
        _state.update { it.copy(isImporting = false) }
        _effects.send(SleepUiEffect.ShowMessage(R.string.message_saved))
        load()
    }

    private suspend fun finishImport(messageRes: Int) {
        _state.update { it.copy(isImporting = false) }
        _effects.send(SleepUiEffect.ShowMessage(messageRes))
    }

    private fun applyDate(utcMillis: Long) {
        val picker = _state.value.activePicker
        val date = TimeFormat.dateFromUtcMillis(utcMillis).toString()
        when (picker) {
            SleepPicker.ViewedDate -> _state.update { it.copy(viewedDate = date, activePicker = null) }
            SleepPicker.ImportDate -> {
                _state.update { it.copy(viewedDate = date, activePicker = null) }
                beginImport(PendingImport.Date)
            }
            SleepPicker.BedDate -> {
                updateEditor { it.copy(bedDate = date).recomputed() }
                _state.update { it.copy(activePicker = null) }
            }
            SleepPicker.WakeDate -> {
                updateEditor { it.copy(wakeDate = date).recomputed() }
                _state.update { it.copy(activePicker = null) }
            }
            else -> _state.update { it.copy(activePicker = null) }
        }
    }

    private fun applyTime(hour: Int, minute: Int) {
        when (_state.value.activePicker) {
            SleepPicker.BedTime -> {
                updateEditor { it.copy(bedHour = hour, bedMinute = minute).recomputed() }
                _state.update { it.copy(activePicker = null) }
            }
            SleepPicker.WakeTime -> {
                updateEditor { it.copy(wakeHour = hour, wakeMinute = minute).recomputed() }
                _state.update { it.copy(activePicker = null) }
            }
            else -> _state.update { it.copy(activePicker = null) }
        }
    }

    private fun openEditor(date: String) {
        val day = _state.value.days.find { it.date == date } ?: return
        val bed = day.bedtime?.let(TimeFormat::parse)
        val wake = day.wakeTime?.let(TimeFormat::parse)
        if (bed == null || wake == null) {
            viewModelScope.launch { _effects.send(SleepUiEffect.ShowMessage(R.string.error_invalid)) }
            return
        }
        _state.update {
            it.copy(
                viewedDate = date,
                editor = SleepEditor(
                    date = date,
                    bedDate = bed.date.toString(),
                    bedHour = bed.time.hour,
                    bedMinute = bed.time.minute,
                    wakeDate = wake.date.toString(),
                    wakeHour = wake.time.hour,
                    wakeMinute = wake.time.minute,
                    durationText = (day.durationMinutes ?: TimeFormat.minutesBetween(
                        bed.date.toString(),
                        bed.time.hour,
                        bed.time.minute,
                        wake.date.toString(),
                        wake.time.hour,
                        wake.time.minute,
                    ) ?: 0).toString(),
                ),
                activePicker = null,
            )
        }
    }

    private fun saveEdit() {
        val editor = _state.value.editor ?: return
        if (_state.value.isSaving) return
        val minutes = editor.durationText.trim().toIntOrNull()
        val bedtime = TimeFormat.toIso(editor.bedDate, editor.bedHour, editor.bedMinute)
        val wake = TimeFormat.toIso(editor.wakeDate, editor.wakeHour, editor.wakeMinute)
        if (minutes == null || bedtime == null || wake == null) {
            viewModelScope.launch { _effects.send(SleepUiEffect.ShowMessage(R.string.error_invalid)) }
            return
        }
        viewModelScope.launch {
            _state.update { it.copy(isSaving = true) }
            when (val result = repository.updateSleep(editor.date, bedtime, wake, minutes)) {
                is NetworkResult.Success -> {
                    _state.update { it.copy(isSaving = false, editor = null, activePicker = null) }
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
