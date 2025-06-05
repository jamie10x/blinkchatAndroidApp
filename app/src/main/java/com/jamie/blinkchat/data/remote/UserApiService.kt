package com.jamie.blinkchat.data.remote

import com.jamie.blinkchat.data.model.remote.PublicUserDto
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

/**
 * API service interface for User Management related endpoints.
 * Base Path: /users (prepended by Retrofit's BaseUrl: /api/v1/users)
 * All endpoints require authentication (handled by AuthInterceptor).
 */
interface UserApiService {

    /**
     * Retrieves the public profile of a specific user by their UUID.
     * Endpoint: GET /api/v1/users/:id
     */
    @GET("users/{id}")
    suspend fun getUserById(
        @Path("id") userId: String
    ): Response<PublicUserDto>

    /**
     * Searches for users.
     * Endpoint: GET /api/v1/users
     */
    @GET("users") // Base path for users, search term is a query parameter
    suspend fun searchUsers(
        @Query("search") searchTerm: String
    ): Response<List<PublicUserDto>> // API returns an array of users
}