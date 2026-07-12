package com.xiaomi.mimoclaw.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import com.xiaomi.mimoclaw.data.local.dao.ChatDao
import com.xiaomi.mimoclaw.data.local.entity.ConversationEntity
import com.xiaomi.mimoclaw.data.local.entity.MessageEntity

@Database(
    entities = [ConversationEntity::class, MessageEntity::class],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun chatDao(): ChatDao
}
