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

@Immutable
data class StatusSliceUi(
    val id: String,
    val level: Level?,
    val start: Float,
    val end: Float,
)

@Immutable
data class StatusDayUi(
    val day: String,
    val label: String,
    val slices: List<StatusSliceUi>,
)

@Immutable
data class CurrentStatusUi(
    val id: String,
    val level: Level?,
    val content: String,
    val startedAt: String,
    val startLabel: String,
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
    val startSecond: Int,
)

@Immutable
data class StatusUiState(
    val level: Level = Level.FREE,
    val content: String = "",
    val phrases: List<PhraseUi> = emptyList(),
    val segments: List<SegmentUi> = emptyList(),
    val days: List<StatusDayUi> = emptyList(),
    val current: CurrentStatusUi? = null,
    val hasMore: Boolean = false,
    val isLoading: Boolean = false,
    val isLoadingMore: Boolean = false,
    val isSaving: Boolean = false,
    val composerOpen: Boolean = false,
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
    data object LoadOlder : StatusUiEvent
    data object ComposerOpened : StatusUiEvent
    data object ComposerDismissed : StatusUiEvent
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
