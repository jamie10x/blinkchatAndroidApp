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
        Timber.d("UserRepo: searchUsers called. SearchTerm: '$searchTerm'")
        if (searchTerm.isBlank()) {
            Timber.d("UserRepo: SearchTerm is blank, emitting Success with empty list.")
            emit(Resource.Success(emptyList()))
            return@flow
        }
        try {
            val trimmedSearchTerm = searchTerm.trim()
            Timber.d("UserRepo: Calling UserApiService.searchUsers with trimmed searchTerm: '$trimmedSearchTerm'")
            val response = userApiService.searchUsers(trimmedSearchTerm)
            Timber.i("UserRepo: API Raw Response for searchUsers: Code=${response.code()}, IsSuccessful=${response.isSuccessful}, Message=${response.message()}, Body=${response.body()?.joinToString { it.username }}, ErrorBody=${response.errorBody()?.string()}")
            // Note: Reading errorBody consumes it. For logging, use a peek method or duplicate if needed for parsing.
            // For simplicity, the HttpLoggingInterceptor should capture the full error body.

            if (response.isSuccessful && response.body() != null) {
                val userDtos = response.body()!!
                Timber.d("UserRepo: API searchUsers success. DTOs count: ${userDtos.size}")
                if (userDtos.isNotEmpty()) {
                    val userEntities = userDtos.map { it.toEntity() }
                    userDao.insertUsers(userEntities)
                    Timber.d("UserRepo: Saved/Updated ${userEntities.size} users to local DB.")
                }
                val domainUsers = userDtos.map { it.toDomain() }
                emit(Resource.Success(domainUsers))
            } else {
                // Re-read error body if it was consumed above, or rely on HttpLoggingInterceptor for full detail
                val errorBodyString = response.errorBody()?.string() ?: "Unknown error structure"
                Timber.w("UserRepo: API searchUsers error. Code: ${response.code()}, ErrorBody: $errorBodyString")
                val errorResource = parseError<List<User>>(response.code(), errorBodyString) // Ensure parseError can handle List<User> or its DTO type
                emit(Resource.Error(errorResource.message ?: "User search failed (API error)", errorCode = errorResource.errorCode))
            }
        } catch (e: HttpException) {
            Timber.e(e, "UserRepo: HttpException during user search. Code: ${e.code()}, Message: ${e.message()}")
            val errorResource = parseError<List<User>>(e.code(), e.response()?.errorBody()?.string())
            emit(Resource.Error(errorResource.message ?: "User search HTTP error", errorCode = errorResource.errorCode))
        } catch (e: IOException) {
            Timber.e(e, "UserRepo: IOException during user search (network issue)")
            emit(Resource.Error("Network error during user search. Please check connection."))
        } catch (e: Exception) {
            Timber.e(e, "UserRepo: Unexpected error during user search")
            emit(Resource.Error("An unexpected error occurred: ${e.localizedMessage}"))
        }
    }.flowOn(Dispatchers.IO)

    override fun getUserById(userId: String, forceNetworkFetch: Boolean): Flow<Resource<out User>> = channelFlow {
        send(Resource.Loading())
        Timber.d("UserRepo: getUserById called for userId: '$userId', forceNetworkFetch: $forceNetworkFetch")

        launch {
            userDao.observeUserById(userId)
                .distinctUntilChanged()
                .collectLatest { entity ->
                    Timber.d("UserRepo: DB observation for userId '$userId' emitted: ${entity?.username}")
                    send(Resource.Success(entity?.toDomain()))
                }
        }

        val localUser = userDao.getUserById(userId)
        if (forceNetworkFetch || localUser == null) {
            Timber.d("UserRepo: Attempting network fetch for userId '$userId'.")
            try {
                val response = userApiService.getUserById(userId)
                Timber.i("UserRepo: API Raw Response for getUserById: Code=${response.code()}, IsSuccessful=${response.isSuccessful}, Body=${response.body()?.username}")
                if (response.isSuccessful && response.body() != null) {
                    val userDto = response.body()!!
                    userDao.insertUser(userDto.toEntity())
                    Timber.d("UserRepo: Fetched and cached user '$userId' from network.")
                    // DB observation will emit this new user
                } else {
                    if (localUser == null) {
                        val errorBodyString = response.errorBody()?.string()
                        Timber.w("UserRepo: API getUserById error and no local cache. Code: ${response.code()}, ErrorBody: $errorBodyString")
                        val errorResource = parseError<User>(response.code(), errorBodyString)
                        send(Resource.Error(errorResource.message ?: "Failed to fetch user", data = null, errorCode = errorResource.errorCode))
                    } else {
                        Timber.w("UserRepo: Network fetch failed for user $userId, but local data exists and was emitted.")
                    }
                }
            } catch (e: HttpException) {
                Timber.e(e, "UserRepo: HttpException fetching user $userId")
                if (localUser == null) {
                    val errorResource = parseError<User>(e.code(), e.response()?.errorBody()?.string())
                    send(Resource.Error(errorResource.message ?: "HTTP error fetching user", data = null, errorCode = errorResource.errorCode))
                }
            } catch (e: IOException) {
                Timber.e(e, "UserRepo: IOException fetching user $userId")
                if (localUser == null) {
                    send(Resource.Error("Network error fetching user.", data = null))
                }
            } catch (e: Exception) {
                Timber.e(e, "UserRepo: Unexpected error fetching user $userId")
                if (localUser == null) {
                    send(Resource.Error("Unexpected error: ${e.localizedMessage}", data = null))
                }
            }
        } else {
            Timber.d("UserRepo: User '$userId' found in cache, not forcing network fetch.")
        }
    }.flowOn(Dispatchers.IO)


    private fun PublicUserDto.toEntity(): UserEntity {
        return UserEntity(id = this.id, username = this.username, email = this.email, createdAt = this.createdAt, updatedAt = this.updatedAt)
    }
    private fun UserEntity.toDomain(): User {
        return User(id = this.id, username = this.username, email = this.email, createdAt = this.createdAt, updatedAt = this.updatedAt)
    }
    private fun PublicUserDto.toDomain(): User {
        return User(id = this.id, username = this.username, email = this.email, createdAt = this.createdAt, updatedAt = this.updatedAt)
    }
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