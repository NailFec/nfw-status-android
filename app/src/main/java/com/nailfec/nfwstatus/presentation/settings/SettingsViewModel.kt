package com.nailfec.nfwstatus.presentation.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nailfec.nfwstatus.R
import com.nailfec.nfwstatus.data.settings.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settings: SettingsRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(SettingsUiState())
    val state = _state.asStateFlow()

    private val _effects = Channel<SettingsUiEffect>(Channel.BUFFERED)
    val effects = _effects.receiveAsFlow()

    init {
        viewModelScope.launch {
            val saved = settings.current()
            _state.update { current ->
                if (current.loaded) {
                    current
                } else {
                    current.copy(
                        baseUrl = saved.baseUrl,
                        token = saved.token,
                        loaded = true,
                    )
                }
            }
        }
    }

    fun onEvent(event: SettingsUiEvent) {
        when (event) {
            is SettingsUiEvent.BaseUrlChanged ->
                _state.update { it.copy(baseUrl = event.value, loaded = true) }
            is SettingsUiEvent.TokenChanged ->
                _state.update { it.copy(token = event.value, loaded = true) }
            SettingsUiEvent.Save -> save()
        }
    }

    private fun save() {
        val snapshot = _state.value
        if (snapshot.isSaving) return
        val baseUrl = snapshot.baseUrl.trim().trimEnd('/')
        val token = snapshot.token.trim()
        viewModelScope.launch {
            _state.update { it.copy(isSaving = true) }
            settings.save(baseUrl, token)
            _state.update { it.copy(baseUrl = baseUrl, token = token, isSaving = false, loaded = true) }
            _effects.send(SettingsUiEffect.ShowMessage(R.string.message_saved))
        }
    }
}
