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
        private val KEY_USER_ID = stringPreferencesKey("user_id")
        private val KEY_THEME = stringPreferencesKey("theme_mode")
        private val KEY_FONT_SIZE = stringPreferencesKey("font_size")
        private val KEY_MODEL = stringPreferencesKey("selected_model")
        private val KEY_ENABLE_VOICE = booleanPreferencesKey("enable_voice")
        private val KEY_ENABLE_NOTIF = booleanPreferencesKey("enable_notification")
    }

    val authToken: Flow<String?> = context.dataStore.data.map { it[KEY_TOKEN] }
    val userId: Flow<String?> = context.dataStore.data.map { it[KEY_USER_ID] }
    val themeMode: Flow<String> = context.dataStore.data.map { it[KEY_THEME] ?: "SYSTEM" }
    val fontSize: Flow<String> = context.dataStore.data.map { it[KEY_FONT_SIZE] ?: "MEDIUM" }
    val selectedModel: Flow<String> = context.dataStore.data.map { it[KEY_MODEL] ?: "MiMo-V2.5-Pro" }
    val enableVoice: Flow<Boolean> = context.dataStore.data.map { it[KEY_ENABLE_VOICE] ?: false }
    val enableNotification: Flow<Boolean> = context.dataStore.data.map { it[KEY_ENABLE_NOTIF] ?: true }

    suspend fun saveToken(token: String) {
        context.dataStore.edit { it[KEY_TOKEN] = token }
    }

    suspend fun saveUserId(userId: String) {
        context.dataStore.edit { it[KEY_USER_ID] = userId }
    }

    suspend fun saveThemeMode(mode: String) {
        context.dataStore.edit { it[KEY_THEME] = mode }
    }

    suspend fun saveFontSize(size: String) {
        context.dataStore.edit { it[KEY_FONT_SIZE] = size }
    }

    suspend fun saveSelectedModel(model: String) {
        context.dataStore.edit { it[KEY_MODEL] = model }
    }

    suspend fun saveEnableVoice(enable: Boolean) {
        context.dataStore.edit { it[KEY_ENABLE_VOICE] = enable }
    }

    suspend fun saveEnableNotification(enable: Boolean) {
        context.dataStore.edit { it[KEY_ENABLE_NOTIF] = enable }
    }

    suspend fun clearAll() {
        context.dataStore.edit { it.clear() }
    }
}
