package com.xiaomi.mimoclaw.core.update

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.updateDataStore by preferencesDataStore(name = "update_prefs")

@Singleton
class UpdatePrefs @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val keyDismissedVersionCode = intPreferencesKey("dismissed_version_code")

    suspend fun getDismissedVersionCode(): Int {
        return context.updateDataStore.data.map { prefs ->
            prefs[keyDismissedVersionCode] ?: -1
        }.first()
    }

    suspend fun setDismissedVersionCode(code: Int) {
        context.updateDataStore.edit { prefs ->
            prefs[keyDismissedVersionCode] = code
        }
    }
}
