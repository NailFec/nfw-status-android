package com.nailfec.nfwstatus.presentation.sleep

import androidx.annotation.StringRes
import androidx.compose.runtime.Immutable
import com.nailfec.nfwstatus.util.TimeFormat

@Immutable
data class SleepShotUi(
    val id: String,
    val url: String,
)

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
    val screenshots: List<SleepShotUi>,
)

enum class SleepPicker { ViewedDate, ImportDate, BedDate, BedTime, WakeDate, WakeTime }

enum class PendingImport { Recent, Date }

@Immutable
data class SleepEditor(
    val date: String,
    val bedDate: String,
    val bedHour: Int,
    val bedMinute: Int,
    val wakeDate: String,
    val wakeHour: Int,
    val wakeMinute: Int,
    val durationText: String,
) {
    fun recomputed(): SleepEditor {
        val minutes = TimeFormat.minutesBetween(
            bedDate,
            bedHour,
            bedMinute,
            wakeDate,
            wakeHour,
            wakeMinute,
        )
        return if (minutes == null) this else copy(durationText = minutes.toString())
    }
}

@Immutable
data class SleepUiState(
    val days: List<SleepDayUi> = emptyList(),
    val viewedDate: String = "",
    val isLoading: Boolean = false,
    val isSaving: Boolean = false,
    val isImporting: Boolean = false,
    val editor: SleepEditor? = null,
    val activePicker: SleepPicker? = null,
    val pendingImport: PendingImport? = null,
    val pendingDeleteDate: String? = null,
    val pendingDeleteShotId: String? = null,
)

sealed interface SleepUiEvent {
    data object Refresh : SleepUiEvent
    data object ImportRecent : SleepUiEvent
    data object ImportDateRequested : SleepUiEvent
    data class PermissionResult(val granted: Boolean) : SleepUiEvent
    data class OpenPicker(val picker: SleepPicker) : SleepUiEvent
    data object DismissPicker : SleepUiEvent
    data class DatePicked(val utcMillis: Long) : SleepUiEvent
    data class TimePicked(val hour: Int, val minute: Int) : SleepUiEvent
    data class DurationChanged(val value: String) : SleepUiEvent
    data class ScreenshotPicked(val uri: String) : SleepUiEvent
    data class SelectDate(val date: String) : SleepUiEvent
    data class EditRequested(val date: String) : SleepUiEvent
    data object EditDismissed : SleepUiEvent
    data object SaveEdit : SleepUiEvent
    data class DeleteHealthRequested(val date: String) : SleepUiEvent
    data object DeleteHealthDismissed : SleepUiEvent
    data object DeleteHealthConfirmed : SleepUiEvent
    data class DeleteShotRequested(val id: String) : SleepUiEvent
    data object DeleteShotDismissed : SleepUiEvent
    data object DeleteShotConfirmed : SleepUiEvent
}

sealed interface SleepUiEffect {
    data class ShowMessage(@StringRes val messageRes: Int) : SleepUiEffect
    data object RequestPermission : SleepUiEffect
}
