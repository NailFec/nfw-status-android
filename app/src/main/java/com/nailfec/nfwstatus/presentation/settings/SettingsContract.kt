package com.nailfec.nfwstatus.presentation.settings

import androidx.annotation.StringRes
import androidx.compose.runtime.Immutable

@Immutable
data class SettingsUiState(
    val baseUrl: String = "",
    val token: String = "",
    val loaded: Boolean = false,
    val isSaving: Boolean = false,
    val checkingUpdate: Boolean = false,
    val downloadingUpdate: Boolean = false,
    val downloadProgress: Float? = null,
    val updateVersionName: String? = null,
    val updateVersionCode: Int? = null,
)

sealed interface SettingsUiEvent {
    data class BaseUrlChanged(val value: String) : SettingsUiEvent
    data class TokenChanged(val value: String) : SettingsUiEvent
    data object Save : SettingsUiEvent
    data object CheckUpdate : SettingsUiEvent
    data object InstallUpdate : SettingsUiEvent
}

sealed interface SettingsUiEffect {
    data class ShowMessage(@StringRes val messageRes: Int) : SettingsUiEffect
    data object Saved : SettingsUiEffect
    data object RequestInstallPermission : SettingsUiEffect
}
