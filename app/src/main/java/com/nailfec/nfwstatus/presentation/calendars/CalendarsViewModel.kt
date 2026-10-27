package com.nailfec.nfwstatus.presentation.calendars

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nailfec.nfwstatus.R
import com.nailfec.nfwstatus.data.remote.NetworkResult
import com.nailfec.nfwstatus.data.remote.NfwRepository
import com.nailfec.nfwstatus.presentation.common.messageRes
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@HiltViewModel
class CalendarsViewModel @Inject constructor(
    private val repository: NfwRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(CalendarsUiState())
    val state = _state.asStateFlow()

    private val _effects = Channel<CalendarsUiEffect>(Channel.BUFFERED)
    val effects = _effects.receiveAsFlow()

    init {
        refresh()
    }

    fun onEvent(event: CalendarsUiEvent) {
        when (event) {
            CalendarsUiEvent.Refresh -> refresh()
            CalendarsUiEvent.AddOpened ->
                _state.update { it.copy(showAdd = true, draftName = "", draftUrl = "") }
            CalendarsUiEvent.AddDismissed -> _state.update { it.copy(showAdd = false) }
            is CalendarsUiEvent.NameChanged -> _state.update { it.copy(draftName = event.value) }
            is CalendarsUiEvent.UrlChanged -> _state.update { it.copy(draftUrl = event.value) }
            CalendarsUiEvent.AddConfirmed -> add()
            is CalendarsUiEvent.DeleteRequested -> _state.update { it.copy(pendingDeleteId = event.id) }
            CalendarsUiEvent.DeleteDismissed -> _state.update { it.copy(pendingDeleteId = null) }
            CalendarsUiEvent.DeleteConfirmed -> confirmDelete()
        }
    }

    private fun refresh() {
        viewModelScope.launch { load() }
    }

    private suspend fun load() {
        _state.update { it.copy(isLoading = true) }
        when (val result = repository.getCalendars()) {
            is NetworkResult.Success -> _state.update {
                it.copy(
                    isLoading = false,
                    subscriptions = result.value.map { item ->
                        CalendarUi(item.id, item.name, item.url, item.lastError)
                    },
                )
            }
            is NetworkResult.Failure -> {
                _state.update { it.copy(isLoading = false) }
                _effects.send(CalendarsUiEffect.ShowMessage(result.kind.messageRes()))
            }
        }
    }

    private fun add() {
        val name = _state.value.draftName.trim()
        val url = _state.value.draftUrl.trim()
        if (name.isEmpty()) {
            viewModelScope.launch { _effects.send(CalendarsUiEffect.ShowMessage(R.string.error_invalid)) }
            return
        }
        if (!url.startsWith("http://") && !url.startsWith("https://")) {
            viewModelScope.launch { _effects.send(CalendarsUiEffect.ShowMessage(R.string.error_bad_url)) }
            return
        }
        viewModelScope.launch {
            _state.update { it.copy(isSaving = true) }
            when (val result = repository.addCalendar(name, url)) {
                is NetworkResult.Success -> {
                    _state.update { it.copy(isSaving = false, showAdd = false, draftName = "", draftUrl = "") }
                    _effects.send(CalendarsUiEffect.ShowMessage(R.string.message_saved))
                    load()
                }
                is NetworkResult.Failure -> {
                    _state.update { it.copy(isSaving = false) }
                    _effects.send(CalendarsUiEffect.ShowMessage(result.kind.messageRes()))
                }
            }
        }
    }

    private fun confirmDelete() {
        val id = _state.value.pendingDeleteId ?: return
        viewModelScope.launch {
            _state.update { it.copy(pendingDeleteId = null, isSaving = true) }
            when (val result = repository.deleteCalendar(id)) {
                is NetworkResult.Success -> {
                    _state.update { it.copy(isSaving = false) }
                    _effects.send(CalendarsUiEffect.ShowMessage(R.string.message_saved))
                    load()
                }
                is NetworkResult.Failure -> {
                    _state.update { it.copy(isSaving = false) }
                    _effects.send(CalendarsUiEffect.ShowMessage(result.kind.messageRes()))
                }
            }
        }
    }
}
