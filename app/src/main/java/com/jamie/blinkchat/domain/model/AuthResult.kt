package com.jamie.blinkchat.domain.model

// Domain model representing the result of a successful authentication (register/login)
data class AuthResult(
    val user: User,
    val token: String
)