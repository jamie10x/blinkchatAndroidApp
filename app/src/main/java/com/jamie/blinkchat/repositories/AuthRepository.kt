package com.jamie.blinkchat.repositories

import com.jamie.blinkchat.core.common.Resource
import com.jamie.blinkchat.data.model.remote.CreateUserRequestDto
import com.jamie.blinkchat.data.model.remote.LoginUserRequestDto
import com.jamie.blinkchat.domain.model.AuthResult
import com.jamie.blinkchat.domain.model.User
import kotlinx.coroutines.flow.Flow

interface AuthRepository {

    /**
     * Registers a new user.
     * @return A [Resource] wrapping an [AuthResult] which contains the user and token on success.
     */
    suspend fun register(request: CreateUserRequestDto): Resource<AuthResult>

    /**
     * Logs in an existing user.
     * @return A [Resource] wrapping an [AuthResult] which contains the user and token on success.
     */
    suspend fun login(request: LoginUserRequestDto): Resource<AuthResult>

    /**
     * Fetches the details of the currently authenticated user.
     * This also serves to validate the current token.
     * @return A [Resource] wrapping a [User] on success.
     */
    suspend fun getCurrentUser(): Resource<User>

    /**
     * Retrieves the current authentication token as a Flow.
     */
    fun observeToken(): Flow<String?>

    /**
     * Clears the stored authentication token (logs out the user).
     */
    suspend fun logout()
}