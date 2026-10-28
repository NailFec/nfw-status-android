package com.nailfec.nfwstatus.presentation.status

import androidx.annotation.StringRes
import androidx.compose.runtime.Immutable
import com.nailfec.nfwstatus.model.Level

@Immutable
data class PhraseUi(
    val id: String,
    val text: String,
)

@Immutable
data class SegmentUi(
    val id: String,
    val level: Level?,
    val content: String,
    val startLabel: String,
    val endLabel: String?,
    val startedAt: String,
)

enum class StatusPicker { StartDate, StartTime }

@Immutable
data class SegmentEditor(
    val id: String,
    val level: Level,
    val content: String,
    val startDate: String,
    val startHour: Int,
    val startMinute: Int,
)

@Immutable
data class StatusUiState(
    val level: Level = Level.FREE,
    val content: String = "",
    val phrases: List<PhraseUi> = emptyList(),
    val segments: List<SegmentUi> = emptyList(),
    val isLoading: Boolean = false,
    val isSaving: Boolean = false,
    val pendingDeletePhraseId: String? = null,
    val pendingDeleteSegmentId: String? = null,
    val editor: SegmentEditor? = null,
    val activePicker: StatusPicker? = null,
)

sealed interface StatusUiEvent {
    data class LevelSelected(val level: Level) : StatusUiEvent
    data class ContentChanged(val value: String) : StatusUiEvent
    data class PhraseClicked(val text: String) : StatusUiEvent
    data class PhraseRemoveRequested(val id: String) : StatusUiEvent
    data object PhraseRemoveDismissed : StatusUiEvent
    data object PhraseRemoveConfirmed : StatusUiEvent
    data object Save : StatusUiEvent
    data object Refresh : StatusUiEvent
    data class EditRequested(val id: String) : StatusUiEvent
    data object EditDismissed : StatusUiEvent
    data class EditorLevelSelected(val level: Level) : StatusUiEvent
    data class EditorContentChanged(val value: String) : StatusUiEvent
    data class OpenPicker(val picker: StatusPicker) : StatusUiEvent
    data object DismissPicker : StatusUiEvent
    data class DatePicked(val utcMillis: Long) : StatusUiEvent
    data class TimePicked(val hour: Int, val minute: Int) : StatusUiEvent
    data object SaveEdit : StatusUiEvent
    data class DeleteRequested(val id: String) : StatusUiEvent
    data object DeleteDismissed : StatusUiEvent
    data object DeleteConfirmed : StatusUiEvent
}

sealed interface StatusUiEffect {
    data class ShowMessage(@StringRes val messageRes: Int) : StatusUiEffect
}
