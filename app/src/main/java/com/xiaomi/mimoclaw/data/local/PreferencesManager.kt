package com.xiaomi.mimoclaw.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "mimo_settings")

@Singleton
class PreferencesManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        private val KEY_TOKEN = stringPreferencesKey("auth_token")
        private val KEY_THEME = stringPreferencesKey("theme_mode")
        private val KEY_MODEL = stringPreferencesKey("selected_model")
    }

    val authToken: Flow<String?> = context.dataStore.data.map { it[KEY_TOKEN] }
    val themeMode: Flow<String> = context.dataStore.data.map { it[KEY_THEME] ?: "SYSTEM" }
    val selectedModel: Flow<String> = context.dataStore.data.map { it[KEY_MODEL] ?: "MiMo-V2.5-Pro" }

    suspend fun saveToken(token: String) {
        context.dataStore.edit { it[KEY_TOKEN] = token }
    }

    suspend fun saveThemeMode(mode: String) {
        context.dataStore.edit { it[KEY_THEME] = mode }
    }

    suspend fun saveSelectedModel(model: String) {
        context.dataStore.edit { it[KEY_MODEL] = model }
    }

    suspend fun clearAll() {
        context.dataStore.edit { it.clear() }
    }
}
