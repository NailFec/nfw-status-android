package com.nailfec.nfwstatus.presentation.settings

import androidx.annotation.StringRes
import androidx.compose.runtime.Immutable

@Immutable
data class SettingsUiState(
    val baseUrl: String = "",
    val token: String = "",
    val loaded: Boolean = false,
    val isSaving: Boolean = false,
)

sealed interface SettingsUiEvent {
    data class BaseUrlChanged(val value: String) : SettingsUiEvent
    data class TokenChanged(val value: String) : SettingsUiEvent
    data object Save : SettingsUiEvent
}

sealed interface SettingsUiEffect {
    data class ShowMessage(@StringRes val messageRes: Int) : SettingsUiEffect
}
