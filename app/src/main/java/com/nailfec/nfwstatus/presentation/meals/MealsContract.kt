package com.nailfec.nfwstatus.presentation.meals

import androidx.annotation.StringRes
import androidx.compose.runtime.Immutable
import com.nailfec.nfwstatus.model.MealSlot

@Immutable
data class MealPhotoUi(
    val id: String,
    val url: String,
)

@Immutable
data class MealUi(
    val id: String,
    val day: String,
    val dayLabel: String,
    val slot: MealSlot?,
    val eatenAt: String,
    val eatenLabel: String,
    val photos: List<MealPhotoUi>,
)

enum class MealPicker { ComposerDate, ComposerTime, EditorDate, EditorTime }

@Immutable
data class MealEditor(
    val id: String,
    val slot: MealSlot,
    val date: String,
    val hour: Int,
    val minute: Int,
    val photos: List<MealPhotoUi>,
    val removedPhotoIds: List<String>,
    val newPhotoUris: List<String>,
)

@Immutable
data class MealsUiState(
    val slot: MealSlot = MealSlot.BREAKFAST,
    val date: String = "",
    val hour: Int = 0,
    val minute: Int = 0,
    val photoUris: List<String> = emptyList(),
    val meals: List<MealUi> = emptyList(),
    val isLoading: Boolean = false,
    val isSaving: Boolean = false,
    val composerOpen: Boolean = false,
    val editor: MealEditor? = null,
    val activePicker: MealPicker? = null,
    val pendingDeleteId: String? = null,
    val ready: Boolean = false,
)

sealed interface MealsUiEvent {
    data class SlotSelected(val slot: MealSlot) : MealsUiEvent
    data class EditorSlotSelected(val slot: MealSlot) : MealsUiEvent
    data class OpenPicker(val picker: MealPicker) : MealsUiEvent
    data object DismissPicker : MealsUiEvent
    data class DatePicked(val utcMillis: Long) : MealsUiEvent
    data class TimePicked(val hour: Int, val minute: Int) : MealsUiEvent
    data class PhotosPicked(val uris: List<String>) : MealsUiEvent
    data class EditPhotosPicked(val uris: List<String>) : MealsUiEvent
    data class RemovePendingPhoto(val uri: String) : MealsUiEvent
    data class RemoveEditorPhoto(val uri: String) : MealsUiEvent
    data class RemoveExistingPhoto(val id: String) : MealsUiEvent
    data object Save : MealsUiEvent
    data object ComposerOpened : MealsUiEvent
    data object ComposerDismissed : MealsUiEvent
    data object SaveEdit : MealsUiEvent
    data object Refresh : MealsUiEvent
    data object ScreenShown : MealsUiEvent
    data class EditRequested(val id: String) : MealsUiEvent
    data object EditDismissed : MealsUiEvent
    data class DeleteRequested(val id: String) : MealsUiEvent
    data object DeleteDismissed : MealsUiEvent
    data object DeleteConfirmed : MealsUiEvent
}

sealed interface MealsUiEffect {
    data class ShowMessage(@StringRes val messageRes: Int) : MealsUiEffect
}
