package com.jamie.blinkchat.domain.model

// Domain model representing a user
data class User(
    val id: String,
    val username: String,
    val email: String,
    val createdAt: String,
    val updatedAt: String
)