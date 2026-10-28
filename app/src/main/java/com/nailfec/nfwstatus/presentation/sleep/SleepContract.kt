package com.nailfec.nfwstatus.presentation.sleep

import androidx.annotation.StringRes
import androidx.compose.runtime.Immutable

@Immutable
data class SleepDayUi(
    val date: String,
    val dateLabel: String,
    val bedtime: String?,
    val wakeTime: String?,
    val bedtimeLabel: String?,
    val wakeLabel: String?,
    val durationMinutes: Int?,
    val stages: List<String>,
)

enum class SleepPicker { ImportDate }

enum class PendingImport { Recent, Date, All }

@Immutable
data class ImportProgress(
    val reading: Boolean,
    val completed: Int,
    val total: Int,
)

@Immutable
data class SleepUiState(
    val days: List<SleepDayUi> = emptyList(),
    val viewedDate: String = "",
    val isLoading: Boolean = false,
    val isSaving: Boolean = false,
    val isImporting: Boolean = false,
    val importProgress: ImportProgress? = null,
    val activePicker: SleepPicker? = null,
    val pendingImport: PendingImport? = null,
    val pendingDeleteDate: String? = null,
)

sealed interface SleepUiEvent {
    data object Refresh : SleepUiEvent
    data object ImportRecent : SleepUiEvent
    data object ImportAll : SleepUiEvent
    data object ImportDateRequested : SleepUiEvent
    data class PermissionResult(val granted: Boolean) : SleepUiEvent
    data object DismissPicker : SleepUiEvent
    data class DatePicked(val utcMillis: Long) : SleepUiEvent
    data class SelectDate(val date: String) : SleepUiEvent
    data class DeleteHealthRequested(val date: String) : SleepUiEvent
    data object DeleteHealthDismissed : SleepUiEvent
    data object DeleteHealthConfirmed : SleepUiEvent
}

sealed interface SleepUiEffect {
    data class ShowMessage(@StringRes val messageRes: Int) : SleepUiEffect
    data object RequestPermission : SleepUiEffect
}
