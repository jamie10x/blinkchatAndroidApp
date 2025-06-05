package com.jamie.blinkchat.di

import com.jamie.blinkchat.data.local.TokenStorageServiceImpl
import com.jamie.blinkchat.data.repository.AuthRepositoryImpl
import com.jamie.blinkchat.data.repository.ChatListRepositoryImpl
import com.jamie.blinkchat.data.repository.MessageRepositoryImpl
import com.jamie.blinkchat.data.repository.UserRepositoryImpl
import com.jamie.blinkchat.repositories.AuthRepository
import com.jamie.blinkchat.repositories.ChatListRepository
import com.jamie.blinkchat.repositories.MessageRepository
import com.jamie.blinkchat.repositories.TokenStorageService
import com.jamie.blinkchat.repositories.UserRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds
    @Singleton
    abstract fun bindTokenStorageService(
        tokenStorageServiceImpl: TokenStorageServiceImpl
    ): TokenStorageService

    @Binds
    @Singleton
    abstract fun bindAuthRepository(
        authRepositoryImpl: AuthRepositoryImpl
    ): AuthRepository

    @Binds
    @Singleton
    abstract fun bindChatListRepository(
        chatListRepositoryImpl: ChatListRepositoryImpl
    ): ChatListRepository

    @Binds
    @Singleton
    abstract fun bindMessageRepository(
        messageRepositoryImpl: MessageRepositoryImpl
    ): MessageRepository

    @Binds
    @Singleton
    abstract fun bindUserRepository(
        userRepositoryImpl: UserRepositoryImpl
    ): UserRepository
}