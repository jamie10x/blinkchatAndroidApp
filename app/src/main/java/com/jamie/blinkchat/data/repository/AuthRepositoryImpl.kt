package com.jamie.blinkchat.data.repository

import com.jamie.blinkchat.core.common.Resource
import com.jamie.blinkchat.data.model.remote.ApiErrorDto
import com.jamie.blinkchat.data.model.remote.AuthSuccessResponseDto
import com.jamie.blinkchat.data.model.remote.CreateUserRequestDto
import com.jamie.blinkchat.data.model.remote.LoginUserRequestDto
import com.jamie.blinkchat.data.model.remote.PublicUserDto
import com.jamie.blinkchat.data.model.remote.websockets.WebSocketManager
import com.jamie.blinkchat.data.remote.AuthApiService
import com.jamie.blinkchat.domain.model.AuthResult
import com.jamie.blinkchat.domain.model.User
import com.jamie.blinkchat.repositories.AuthRepository
import com.jamie.blinkchat.repositories.TokenStorageService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import retrofit2.HttpException
import timber.log.Timber
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AuthRepositoryImpl @Inject constructor(
    private val authApiService: AuthApiService,
    private val tokenStorageService: TokenStorageService,
    private val webSocketManager: WebSocketManager, // Inject WebSocketManager
    private val json: Json
) : AuthRepository {

    override suspend fun register(request: CreateUserRequestDto): Resource<AuthResult> {
        return safeApiCall {
            val response = authApiService.registerUser(request)
            if (response.isSuccessful && response.body() != null) {
                val authResponse = response.body()!!
                // Token is saved, but user needs to log in to establish WebSocket session.
                // So, don't connect WebSocket here. Connect on explicit login.
                // tokenStorageService.saveAuthToken(authResponse.token) // Token saved on successful registration is usually for immediate login.
                // The API returns a token, so let's save it.
                // The WebSocket will connect when they log in.
                tokenStorageService.saveAuthToken(authResponse.token)
                Timber.i("User registered, token saved. User should log in to connect WebSocket.")
                Resource.Success(authResponse.toDomain())
            } else {
                parseError(response.code(), response.errorBody()?.string())
            }
        }
    }

    override suspend fun login(request: LoginUserRequestDto): Resource<AuthResult> {
        return safeApiCall {
            val response = authApiService.loginUser(request)
            if (response.isSuccessful && response.body() != null) {
                val authResponse = response.body()!!
                tokenStorageService.saveAuthToken(authResponse.token)
                // Connect WebSocket after successful login and token save
                webSocketManager.connect() // Non-blocking call to initiate connection
                Timber.i("User logged in, token saved, WebSocket connection initiated.")
                Resource.Success(authResponse.toDomain())
            } else {
                parseError(response.code(), response.errorBody()?.string())
            }
        }
    }

    override suspend fun getCurrentUser(): Resource<User> {
        Timber.d("AuthRepo: getCurrentUser called")
        return safeApiCall {
            Timber.d("AuthRepo: safeApiCall for getCurrentUser started")
            val tokenValue = tokenStorageService.getAuthToken().firstOrNull() // This is a suspend call if getAuthToken() itself collects
            Timber.d("AuthRepo: Token observed: ${if (tokenValue != null) "Exists" else "Null"}")

            if (tokenValue.isNullOrBlank()) { // Changed from !tokenAvailable to isNullOrBlank for clarity
                Timber.w("AuthRepo: No local token available for /me. Disconnecting WebSocket.")
                webSocketManager.disconnect(wasIntentional = false)
                return@safeApiCall Resource.Error("No authentication token found.", errorCode = 401)
            }
            Timber.d("AuthRepo: Calling AuthApiService.getCurrentUser() for /me")
            val response = authApiService.getCurrentUser()
            Timber.d("AuthRepo: AuthApiService.getCurrentUser() response code: ${response.code()}")

            if (response.isSuccessful && response.body() != null) {
                Timber.i("AuthRepo: /me call successful. User: ${response.body()?.username}")
                webSocketManager.connect()
                Resource.Success(response.body()!!.toDomain())
            } else {
                Timber.w("AuthRepo: /me call failed. Code: ${response.code()}. Clearing token and disconnecting WebSocket.")
                if (response.code() == 401 || response.code() == 404) {
                    tokenStorageService.clearAuthToken()
                    webSocketManager.disconnect(wasIntentional = false)
                }
                parseError(response.code(), response.errorBody()?.string())
            }
        }
    }

    override fun observeToken(): Flow<String?> {
        return tokenStorageService.getAuthToken()
    }

    override suspend fun logout() {
        // Perform local cleanup first
        val currentToken = tokenStorageService.getAuthToken().firstOrNull()
        tokenStorageService.clearAuthToken()
        webSocketManager.disconnect(wasIntentional = true) // Intentionally disconnect WebSocket
        Timber.i("User logged out, token cleared, WebSocket disconnected.")

        // Optionally: Call a backend logout endpoint if it exists.
        // This should be done *after* local cleanup or if it doesn't depend on the local token.
        // If the backend logout needs the token, pass currentToken.
        // Example:
        // if (currentToken != null) {
        //     try {
        //         val bearerToken = "Bearer $currentToken"
        //         // val response = authApiService.logoutUserOnBackend(bearerToken) // Assuming such an endpoint
        //         // if (!response.isSuccessful) {
        //         //     Timber.w("Backend logout call failed: ${response.code()}")
        //         // }
        //     } catch (e: Exception) {
        //         Timber.e(e, "Error calling backend logout.")
        //     }
        // }
    }

    // --- Helper for safe API calls and error parsing (no changes from previous) ---
    private suspend fun <T> safeApiCall(apiCall: suspend () -> Resource<T>): Resource<T> {
        return withContext(Dispatchers.IO) {
            try {
                apiCall()
            } catch (e: HttpException) {
                Timber.e(e, "HttpException in API call")
                val errorBody = e.response()?.errorBody()?.string()
                parseError(e.code(), errorBody)
            } catch (e: IOException) {
                Timber.e(e, "IOException, possibly no internet or server down")
                Resource.Error("Network error: Please check your connection or try again later.")
            } catch (e: Exception) {
                Timber.e(e, "Unexpected error in API call: ${e.message}")
                Resource.Error("An unexpected error occurred: ${e.localizedMessage}")
            }
        }
    }

    private fun <T> parseError(errorCode: Int, errorBody: String?): Resource<T> {
        val defaultMessage = "API Error: $errorCode"
        return try {
            if (errorBody != null) {
                val apiError = json.decodeFromString<ApiErrorDto>(errorBody)
                Resource.Error(apiError.error, data = null, errorCode = errorCode)
            } else {
                Resource.Error(defaultMessage, data = null, errorCode = errorCode)
            }
        } catch (e: Exception) {
            Timber.e(e, "Failed to parse error body for auth: $errorBody")
            Resource.Error(defaultMessage, data = null, errorCode = errorCode)
        }
    }

    // --- Mapper functions DTO to Domain (no changes from previous) ---
    private fun AuthSuccessResponseDto.toDomain(): AuthResult {
        return AuthResult(
            user = this.user.toDomain(),
            token = this.token
        )
    }

    private fun PublicUserDto.toDomain(): User {
        return User(
            id = this.id,
            username = this.username,
            email = this.email,
            createdAt = this.createdAt,
            updatedAt = this.updatedAt
        )
    }
}