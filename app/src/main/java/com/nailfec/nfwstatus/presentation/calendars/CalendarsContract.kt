package com.nailfec.nfwstatus.presentation.calendars

import androidx.annotation.StringRes
import androidx.compose.runtime.Immutable

@Immutable
data class CalendarUi(
    val id: String,
    val name: String,
    val url: String,
    val lastError: String?,
)

@Immutable
data class CalendarsUiState(
    val subscriptions: List<CalendarUi> = emptyList(),
    val isLoading: Boolean = false,
    val isSaving: Boolean = false,
    val isRefreshingFeed: Boolean = false,
    val showAdd: Boolean = false,
    val draftName: String = "",
    val draftUrl: String = "",
    val pendingDeleteId: String? = null,
)

sealed interface CalendarsUiEvent {
    data object Refresh : CalendarsUiEvent
    data object RefreshSchedule : CalendarsUiEvent
    data object AddOpened : CalendarsUiEvent
    data object AddDismissed : CalendarsUiEvent
    data class NameChanged(val value: String) : CalendarsUiEvent
    data class UrlChanged(val value: String) : CalendarsUiEvent
    data object AddConfirmed : CalendarsUiEvent
    data class DeleteRequested(val id: String) : CalendarsUiEvent
    data object DeleteDismissed : CalendarsUiEvent
    data object DeleteConfirmed : CalendarsUiEvent
}

sealed interface CalendarsUiEffect {
    data class ShowMessage(@StringRes val messageRes: Int) : CalendarsUiEffect
}
