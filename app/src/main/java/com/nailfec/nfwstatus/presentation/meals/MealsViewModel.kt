package com.nailfec.nfwstatus.presentation.meals

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nailfec.nfwstatus.R
import com.nailfec.nfwstatus.data.remote.NetworkResult
import com.nailfec.nfwstatus.data.remote.NfwRepository
import com.nailfec.nfwstatus.model.Meal
import com.nailfec.nfwstatus.model.MealSlot
import com.nailfec.nfwstatus.presentation.common.messageRes
import com.nailfec.nfwstatus.util.TimeFormat
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@HiltViewModel
class MealsViewModel @Inject constructor(
    private val repository: NfwRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(initialState())
    val state = _state.asStateFlow()

    private val _effects = Channel<MealsUiEffect>(Channel.BUFFERED)
    val effects = _effects.receiveAsFlow()
    private var composerClockCustom = false

    init {
        refresh()
    }

    fun onEvent(event: MealsUiEvent) {
        when (event) {
            is MealsUiEvent.SlotSelected -> _state.update { it.copy(slot = event.slot) }
            is MealsUiEvent.EditorSlotSelected -> updateEditor { it.copy(slot = event.slot) }
            is MealsUiEvent.OpenPicker -> _state.update { it.copy(activePicker = event.picker) }
            MealsUiEvent.DismissPicker -> _state.update { it.copy(activePicker = null) }
            is MealsUiEvent.DatePicked -> applyDate(event.utcMillis)
            is MealsUiEvent.TimePicked -> applyTime(event.hour, event.minute)
            is MealsUiEvent.PhotosPicked ->
                _state.update { it.copy(photoUris = it.photoUris + event.uris) }
            is MealsUiEvent.EditPhotosPicked ->
                updateEditor { it.copy(newPhotoUris = it.newPhotoUris + event.uris) }
            is MealsUiEvent.RemovePendingPhoto ->
                _state.update { it.copy(photoUris = it.photoUris.filterNot { uri -> uri == event.uri }) }
            is MealsUiEvent.RemoveEditorPhoto ->
                updateEditor { it.copy(newPhotoUris = it.newPhotoUris.filterNot { uri -> uri == event.uri }) }
            is MealsUiEvent.RemoveExistingPhoto ->
                updateEditor { editor ->
                    editor.copy(removedPhotoIds = editor.removedPhotoIds + event.id)
                }
            MealsUiEvent.Save -> save()
            MealsUiEvent.SaveEdit -> saveEdit()
            MealsUiEvent.Refresh -> refresh()
            MealsUiEvent.ScreenShown -> {
                if (!composerClockCustom) applyComposerNow()
            }
            is MealsUiEvent.EditRequested -> openEditor(event.id)
            MealsUiEvent.EditDismissed -> _state.update { it.copy(editor = null, activePicker = null) }
            is MealsUiEvent.DeleteRequested -> _state.update { it.copy(pendingDeleteId = event.id) }
            MealsUiEvent.DeleteDismissed -> _state.update { it.copy(pendingDeleteId = null) }
            MealsUiEvent.DeleteConfirmed -> confirmDelete()
        }
    }

    private fun updateEditor(transform: (MealEditor) -> MealEditor) {
        _state.update { state ->
            val editor = state.editor ?: return@update state
            state.copy(editor = transform(editor))
        }
    }

    private fun refresh() {
        viewModelScope.launch { load() }
    }

    private suspend fun load() {
        _state.update { it.copy(isLoading = true) }
        when (val result = repository.getMeals()) {
            is NetworkResult.Success -> _state.update {
                it.copy(isLoading = false, meals = result.value.map { meal -> meal.toUi() })
            }
            is NetworkResult.Failure -> {
                _state.update { it.copy(isLoading = false) }
                _effects.send(MealsUiEffect.ShowMessage(result.kind.messageRes()))
            }
        }
    }

    private fun save() {
        val snapshot = _state.value
        if (snapshot.isSaving) return
        val eatenAt = TimeFormat.toIso(snapshot.date, snapshot.hour, snapshot.minute) ?: run {
            viewModelScope.launch { _effects.send(MealsUiEffect.ShowMessage(R.string.error_invalid)) }
            return
        }
        viewModelScope.launch {
            _state.update { it.copy(isSaving = true) }
            when (val result = repository.createMeal(snapshot.slot.wire, eatenAt, snapshot.photoUris)) {
                is NetworkResult.Success -> {
                    composerClockCustom = false
                    val now = TimeFormat.stampNow()
                    _state.update {
                        it.copy(
                            isSaving = false,
                            photoUris = emptyList(),
                            date = now.date.toString(),
                            hour = now.time.hour,
                            minute = now.time.minute,
                        )
                    }
                    _effects.send(MealsUiEffect.ShowMessage(R.string.message_saved))
                    load()
                }
                is NetworkResult.Failure -> {
                    _state.update { it.copy(isSaving = false) }
                    _effects.send(MealsUiEffect.ShowMessage(result.kind.messageRes()))
                    if (result.kind == com.nailfec.nfwstatus.data.remote.FailureKind.Exists) {
                        load()
                    }
                }
            }
        }
    }

    private fun saveEdit() {
        val editor = _state.value.editor ?: return
        if (_state.value.isSaving) return
        val eatenAt = TimeFormat.toIso(editor.date, editor.hour, editor.minute) ?: run {
            viewModelScope.launch { _effects.send(MealsUiEffect.ShowMessage(R.string.error_invalid)) }
            return
        }
        viewModelScope.launch {
            _state.update { it.copy(isSaving = true) }
            when (
                val result = repository.updateMeal(
                    id = editor.id,
                    slot = editor.slot.wire,
                    eatenAt = eatenAt,
                    removePhotoIds = editor.removedPhotoIds,
                    photoUris = editor.newPhotoUris,
                )
            ) {
                is NetworkResult.Success -> {
                    _state.update { it.copy(isSaving = false, editor = null, activePicker = null) }
                    _effects.send(MealsUiEffect.ShowMessage(R.string.message_saved))
                    load()
                }
                is NetworkResult.Failure -> {
                    _state.update { it.copy(isSaving = false) }
                    _effects.send(MealsUiEffect.ShowMessage(result.kind.messageRes()))
                }
            }
        }
    }

    private fun confirmDelete() {
        val id = _state.value.pendingDeleteId ?: return
        viewModelScope.launch {
            _state.update { it.copy(pendingDeleteId = null, isSaving = true) }
            when (val result = repository.deleteMeal(id)) {
                is NetworkResult.Success -> {
                    _state.update { it.copy(isSaving = false) }
                    _effects.send(MealsUiEffect.ShowMessage(R.string.message_saved))
                    load()
                }
                is NetworkResult.Failure -> {
                    _state.update { it.copy(isSaving = false) }
                    _effects.send(MealsUiEffect.ShowMessage(result.kind.messageRes()))
                }
            }
        }
    }

    private fun openEditor(id: String) {
        val meal = _state.value.meals.find { it.id == id } ?: return
        val stamp = TimeFormat.parse(meal.eatenAt) ?: run {
            viewModelScope.launch { _effects.send(MealsUiEffect.ShowMessage(R.string.error_invalid)) }
            return
        }
        _state.update {
            it.copy(
                editor = MealEditor(
                    id = meal.id,
                    slot = meal.slot ?: MealSlot.BREAKFAST,
                    date = stamp.date.toString(),
                    hour = stamp.time.hour,
                    minute = stamp.time.minute,
                    photos = meal.photos,
                    removedPhotoIds = emptyList(),
                    newPhotoUris = emptyList(),
                ),
                activePicker = null,
            )
        }
    }

    private fun applyComposerNow() {
        val now = TimeFormat.stampNow()
        _state.update {
            it.copy(
                date = now.date.toString(),
                hour = now.time.hour,
                minute = now.time.minute,
            )
        }
    }

    private fun applyDate(utcMillis: Long) {
        val date = TimeFormat.dateFromUtcMillis(utcMillis).toString()
        when (_state.value.activePicker) {
            MealPicker.ComposerDate -> {
                composerClockCustom = true
                _state.update { it.copy(date = date, activePicker = null) }
            }
            MealPicker.EditorDate -> {
                updateEditor { it.copy(date = date) }
                _state.update { it.copy(activePicker = null) }
            }
            else -> _state.update { it.copy(activePicker = null) }
        }
    }

    private fun applyTime(hour: Int, minute: Int) {
        when (_state.value.activePicker) {
            MealPicker.ComposerTime -> {
                composerClockCustom = true
                _state.update { it.copy(hour = hour, minute = minute, activePicker = null) }
            }
            MealPicker.EditorTime -> {
                updateEditor { it.copy(hour = hour, minute = minute) }
                _state.update { it.copy(activePicker = null) }
            }
            else -> _state.update { it.copy(activePicker = null) }
        }
    }

    private fun Meal.toUi(): MealUi = MealUi(
        id = id,
        day = day,
        dayLabel = TimeFormat.formatDate(day),
        slot = MealSlot.fromWire(slot),
        eatenLabel = TimeFormat.formatIso(eatenAt),
        eatenAt = eatenAt,
        photos = photos.map { MealPhotoUi(it.id, it.url) },
    )

    private companion object {
        fun initialState(): MealsUiState {
            val now = TimeFormat.stampNow()
            return MealsUiState(
                date = now.date.toString(),
                hour = now.time.hour,
                minute = now.time.minute,
                ready = true,
            )
        }
    }
}
