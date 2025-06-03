package com.jamie.blinkchat.data.model.remote

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// --- Request DTOs ---

@Serializable
data class CreateUserRequestDto(
    val username: String,
    val email: String,
    val password: String
)

@Serializable
data class LoginUserRequestDto(
    val email: String,
    val password: String
)

// --- Response DTOs ---

@Serializable
data class PublicUserDto(
    val id: String, // UUID
    val username: String,
    val email: String,
    @SerialName("createdAt") val createdAt: String, // ISO 8601 timestamp
    @SerialName("updatedAt") val updatedAt: String  // ISO 8601 timestamp
)

@Serializable
data class AuthSuccessResponseDto(
    val message: String,
    val token: String,
    val user: PublicUserDto
)

// Generic Error DTO (can be used for various error responses if the structure is consistent)
@Serializable
data class ApiErrorDto(
    val error: String,
    val details: String? = null // Optional further details
)