package com.jamie.blinkchat.data.repository

import com.jamie.blinkchat.core.common.Resource
import com.jamie.blinkchat.data.local.dato.UserDao
import com.jamie.blinkchat.data.model.local.UserEntity
import com.jamie.blinkchat.data.model.remote.ApiErrorDto
import com.jamie.blinkchat.data.model.remote.PublicUserDto
import com.jamie.blinkchat.data.remote.UserApiService
import com.jamie.blinkchat.domain.model.User
import com.jamie.blinkchat.repositories.UserRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import retrofit2.HttpException
import timber.log.Timber
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class UserRepositoryImpl @Inject constructor(
    private val userApiService: UserApiService,
    private val userDao: UserDao,
    private val json: Json
) : UserRepository {

    override fun searchUsers(searchTerm: String): Flow<Resource<List<User>>> = flow {
        emit(Resource.Loading())
        if (searchTerm.isBlank()) {
            emit(Resource.Success(emptyList()))
            return@flow
        }
        try {
            val response = userApiService.searchUsers(searchTerm.trim())
            if (response.isSuccessful && response.body() != null) {
                val userDtos = response.body()!!
                // Save/update fetched users in local DB for consistency and potential offline use
                val userEntities = userDtos.map { it.toEntity() }
                userDao.insertUsers(userEntities) // Uses OnConflictStrategy.REPLACE

                val domainUsers = userDtos.map { it.toDomain() }
                emit(Resource.Success(domainUsers))
            } else {
                val error = parseError<List<PublicUserDto>>(response.code(), response.errorBody()?.string())
                emit(Resource.Error(error.message ?: "User search failed", errorCode = error.errorCode))
            }
        } catch (e: HttpException) {
            Timber.e(e, "HttpException during user search")
            val error = parseError<List<PublicUserDto>>(e.code(), e.response()?.errorBody()?.string())
            emit(Resource.Error(error.message ?: "User search HTTP error", errorCode = error.errorCode))
        } catch (e: IOException) {
            Timber.e(e, "IOException during user search (network issue)")
            emit(Resource.Error("Network error during user search. Please check connection."))
        } catch (e: Exception) {
            Timber.e(e, "Unexpected error during user search")
            emit(Resource.Error("An unexpected error occurred: ${e.localizedMessage}"))
        }
    }.flowOn(Dispatchers.IO)


    override fun getUserById(userId: String, forceNetworkFetch: Boolean): Flow<Resource<out User>> = channelFlow {
        send(Resource.Loading()) // Emit loading state

        // Observe local DB for immediate data and updates
        launch {
            userDao.observeUserById(userId)
                .distinctUntilChanged() // Only emit if data actually changes
                .collectLatest { entity -> // Use collectLatest to cancel previous collection if new one starts
                    send(Resource.Success(entity?.toDomain()))
                    // If we got data from DB and not forcing network, we might consider this "good enough"
                    // but a network fetch can update it.
                }
        }

        // Determine if network fetch is needed
        val localUser = userDao.getUserById(userId) // Quick check for existence
        if (forceNetworkFetch || localUser == null /* Or add staleness check here */) {
            Timber.d("Fetching user $userId from network. Force: $forceNetworkFetch, Local found: ${localUser != null}")
            try {
                val response = userApiService.getUserById(userId)
                if (response.isSuccessful && response.body() != null) {
                    val userDto = response.body()!!
                    userDao.insertUser(userDto.toEntity()) // Cache the fetched user
                    // The Flow observing the DB will automatically emit the updated Resource.Success
                    // No need to send(Resource.Success(userDto.toDomain())) here if DB observation is active.
                } else {
                    // If network fails but we had local data, the local data flow continues to emit.
                    // If no local data and network fails, we need to emit an error.
                    if (localUser == null) { // Only send error if there was no local data to show
                        val error = parseError<PublicUserDto>(response.code(), response.errorBody()?.string())
                        send(Resource.Error(error.message ?: "Failed to fetch user", data = null, errorCode = error.errorCode))
                    } else {
                        Timber.w("Network fetch failed for user $userId, but local data exists.")
                    }
                }
            } catch (e: HttpException) {
                Timber.e(e, "HttpException fetching user $userId")
                if (localUser == null) {
                    val error = parseError<PublicUserDto>(e.code(), e.response()?.errorBody()?.string())
                    send(Resource.Error(error.message ?: "HTTP error fetching user", data = null, errorCode = error.errorCode))
                }
            } catch (e: IOException) {
                Timber.e(e, "IOException fetching user $userId")
                if (localUser == null) {
                    send(Resource.Error("Network error fetching user.", data = null))
                }
            } catch (e: Exception) {
                Timber.e(e, "Unexpected error fetching user $userId")
                if (localUser == null) {
                    send(Resource.Error("Unexpected error: ${e.localizedMessage}", data = null))
                }
            }
        } else if (!forceNetworkFetch) {
            // Already emitted by the DB observation, or if it was null initially,
            // the DB observation will emit null.
            // No explicit Resource.Success(localUser.toDomain()) needed here if already observing DB.
            Timber.d("User $userId found in cache, not forcing network fetch.")
        }
    }.flowOn(Dispatchers.IO)


    // --- Mappers ---
    private fun PublicUserDto.toEntity(): UserEntity {
        return UserEntity(
            id = this.id,
            username = this.username,
            email = this.email,
            createdAt = this.createdAt,
            updatedAt = this.updatedAt
        )
    }

    private fun UserEntity.toDomain(): User {
        return User(
            id = this.id,
            username = this.username,
            email = this.email,
            createdAt = this.createdAt,
            updatedAt = this.updatedAt
        )
    }

    private fun PublicUserDto.toDomain(): User { // Direct DTO to Domain for search results
        return User(
            id = this.id,
            username = this.username,
            email = this.email,
            createdAt = this.createdAt,
            updatedAt = this.updatedAt
        )
    }

    // --- Error Parsing Utility (can be common) ---
    private fun <T> parseError(errorCode: Int, errorBody: String?): Resource<T> {
        val defaultMessage = "API Error ($errorCode)"
        return try {
            if (errorBody != null) {
                val apiError = json.decodeFromString<ApiErrorDto>(errorBody)
                Resource.Error(apiError.error, data = null, errorCode = errorCode)
            } else {
                Resource.Error(defaultMessage, data = null, errorCode = errorCode)
            }
        } catch (e: Exception) {
            Timber.e(e, "Failed to parse error body: $errorBody")
            Resource.Error(defaultMessage, data = null, errorCode = errorCode)
        }
    }
}