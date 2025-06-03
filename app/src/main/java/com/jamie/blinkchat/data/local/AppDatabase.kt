package com.jamie.blinkchat.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import com.jamie.blinkchat.data.local.dato.ChatSummaryDao
import com.jamie.blinkchat.data.local.dato.MessageDao
import com.jamie.blinkchat.data.local.dato.UserDao
import com.jamie.blinkchat.data.model.local.ChatSummaryEntity
import com.jamie.blinkchat.data.model.local.MessageEntity
import com.jamie.blinkchat.data.model.local.UserEntity

@Database(
    entities = [UserEntity::class, ChatSummaryEntity::class, MessageEntity::class],
    version = 2, // Ensure this was incremented when MessageEntity was added
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun userDao(): UserDao
    abstract fun chatSummaryDao(): ChatSummaryDao
    abstract fun messageDao(): MessageDao

    companion object {
        const val DATABASE_NAME = "blink_chat_db"
    }
}