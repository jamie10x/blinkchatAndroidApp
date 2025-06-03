package com.jamie.blinkchat.di

import android.content.Context
import androidx.room.Room
import com.jamie.blinkchat.data.local.AppDatabase
import com.jamie.blinkchat.data.local.dato.ChatSummaryDao
import com.jamie.blinkchat.data.local.dato.MessageDao
import com.jamie.blinkchat.data.local.dato.UserDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Singleton
    @Provides
    fun provideAppDatabase(@ApplicationContext context: Context): AppDatabase {
        return Room.databaseBuilder(
            context,
            AppDatabase::class.java,
            AppDatabase.DATABASE_NAME
        )
            .fallbackToDestructiveMigration(false) // OK for dev. Implement proper migrations for prod.
            .build()
    }

    @Singleton
    @Provides
    fun provideUserDao(database: AppDatabase): UserDao {
        return database.userDao()
    }

    @Singleton
    @Provides
    fun provideChatSummaryDao(database: AppDatabase): ChatSummaryDao {
        return database.chatSummaryDao()
    }

    // Provide MessageDao
    @Singleton
    @Provides
    fun provideMessageDao(database: AppDatabase): MessageDao {
        return database.messageDao()
    }
}