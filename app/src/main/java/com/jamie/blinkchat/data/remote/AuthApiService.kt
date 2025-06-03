package com.jamie.blinkchat.data.remote

import com.jamie.blinkchat.data.model.remote.AuthSuccessResponseDto
import com.jamie.blinkchat.data.model.remote.CreateUserRequestDto
import com.jamie.blinkchat.data.model.remote.LoginUserRequestDto
import com.jamie.blinkchat.data.model.remote.PublicUserDto
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST

/**
 * API service interface for Authentication related endpoints.
 * Base Path: /auth
 */
interface AuthApiService {

    @POST("auth/register")
    suspend fun registerUser(
        @Body request: CreateUserRequestDto
    ): Response<AuthSuccessResponseDto>

    @POST("auth/login")
    suspend fun loginUser(
        @Body request: LoginUserRequestDto
    ): Response<AuthSuccessResponseDto>

    @GET("auth/me")
    suspend fun getCurrentUser(
    ): Response<PublicUserDto>
}