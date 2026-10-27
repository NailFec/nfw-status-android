package com.nailfec.nfwstatus.data.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

data class UserSettings(
    val baseUrl: String = "",
    val token: String = "",
)

private val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(name = "nfw_settings")

@Singleton
class SettingsRepository @Inject constructor(
    @ApplicationContext context: Context,
) {
    private val dataStore = context.settingsDataStore

    val settings: Flow<UserSettings> = dataStore.data.map { prefs ->
        UserSettings(
            baseUrl = prefs[BASE_URL].orEmpty(),
            token = prefs[TOKEN].orEmpty(),
        )
    }

    suspend fun current(): UserSettings = settings.first()

    suspend fun save(baseUrl: String, token: String) {
        dataStore.edit { prefs ->
            prefs[BASE_URL] = baseUrl.trim().trimEnd('/')
            prefs[TOKEN] = token.trim()
        }
    }

    private companion object {
        val BASE_URL = stringPreferencesKey("base_url")
        val TOKEN = stringPreferencesKey("token")
    }
}
