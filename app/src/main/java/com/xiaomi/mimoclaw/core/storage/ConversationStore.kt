package com.xiaomi.mimoclaw.core.storage

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.xiaomi.mimoclaw.feature.chat.model.Conversation
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

private val Context.conversationDataStore: DataStore<Preferences> by preferencesDataStore(name = "conversations")

/**
 * 对话持久化存储 - 使用 DataStore + JSON
 *
 * 注意：DataStore Preferences 适合中小数据量。
 * 如果对话数量极大（>500条），应迁移到 Room。
 */
@Singleton
class ConversationStore @Inject constructor(
    @ApplicationContext private val context: Context,
    private val gson: Gson
) {
    private val key = stringPreferencesKey("conversations_json")

    suspend fun save(conversations: List<Conversation>) = withContext(Dispatchers.IO) {
        context.conversationDataStore.edit { prefs ->
            prefs[key] = gson.toJson(conversations)
        }
    }

    suspend fun load(): List<Conversation> = withContext(Dispatchers.IO) {
        val json = context.conversationDataStore.data.map { prefs ->
            prefs[key]
        }.first()
        if (json.isNullOrBlank()) return@withContext emptyList()
        runCatching {
            val type = object : TypeToken<List<Conversation>>() {}.type
            gson.fromJson<List<Conversation>>(json, type)
        }.getOrDefault(emptyList())
    }
}
