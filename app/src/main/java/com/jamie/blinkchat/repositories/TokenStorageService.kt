package com.jamie.blinkchat.repositories

import kotlinx.coroutines.flow.Flow

interface TokenStorageService {
    suspend fun saveAuthToken(token: String)
    fun getAuthToken(): Flow<String?>
    suspend fun clearAuthToken()
}