package com.nailfec.nfwstatus.presentation.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nailfec.nfwstatus.BuildConfig
import com.nailfec.nfwstatus.R
import com.nailfec.nfwstatus.data.settings.SettingsRepository
import com.nailfec.nfwstatus.data.update.ApkInstaller
import com.nailfec.nfwstatus.data.update.GithubUpdateClient
import com.nailfec.nfwstatus.data.update.UpdateCheck
import dagger.hilt.android.lifecycle.HiltViewModel
import java.io.IOException
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
    private val updates: GithubUpdateClient,
    private val installer: ApkInstaller,
) : ViewModel() {
    private var pendingDownloadUrl: String? = null
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
            SettingsUiEvent.CheckUpdate -> checkUpdate()
            SettingsUiEvent.InstallUpdate -> installUpdate()
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
            _effects.send(SettingsUiEffect.Saved)
        }
    }

    private fun checkUpdate() {
        val snapshot = _state.value
        if (snapshot.checkingUpdate || snapshot.downloadingUpdate) return
        viewModelScope.launch {
            _state.update { it.copy(checkingUpdate = true) }
            when (val result = updates.check(BuildConfig.VERSION_CODE)) {
                is UpdateCheck.Available -> {
                    pendingDownloadUrl = result.downloadUrl
                    _state.update {
                        it.copy(
                            checkingUpdate = false,
                            updateVersionName = result.versionName,
                            updateVersionCode = result.versionCode,
                        )
                    }
                }
                UpdateCheck.UpToDate -> clearUpdate(R.string.message_up_to_date)
                UpdateCheck.NoRelease -> clearUpdate(R.string.error_update_none)
                UpdateCheck.NoPackage -> clearUpdate(R.string.error_update_asset)
                UpdateCheck.Failed -> clearUpdate(R.string.error_update_check)
            }
        }
    }

    private suspend fun clearUpdate(messageRes: Int) {
        pendingDownloadUrl = null
        _state.update {
            it.copy(
                checkingUpdate = false,
                updateVersionName = null,
                updateVersionCode = null,
            )
        }
        _effects.send(SettingsUiEffect.ShowMessage(messageRes))
    }

    private fun installUpdate() {
        val url = pendingDownloadUrl ?: return
        val snapshot = _state.value
        if (snapshot.downloadingUpdate || snapshot.checkingUpdate) return
        if (!installer.canRequestInstalls()) {
            viewModelScope.launch {
                _effects.send(SettingsUiEffect.ShowMessage(R.string.message_allow_installs))
                _effects.send(SettingsUiEffect.RequestInstallPermission)
            }
            return
        }
        viewModelScope.launch {
            _state.update { it.copy(downloadingUpdate = true, downloadProgress = 0f) }
            val file = updates.download(url) { fraction ->
                _state.update { it.copy(downloadProgress = fraction) }
            }
            if (file == null) {
                _state.update { it.copy(downloadingUpdate = false, downloadProgress = null) }
                _effects.send(SettingsUiEffect.ShowMessage(R.string.error_update_download))
                return@launch
            }
            try {
                installer.install(file)
            } catch (_: IOException) {
                _effects.send(SettingsUiEffect.ShowMessage(R.string.error_update_install))
            } finally {
                _state.update { it.copy(downloadingUpdate = false, downloadProgress = null) }
            }
        }
    }
}
