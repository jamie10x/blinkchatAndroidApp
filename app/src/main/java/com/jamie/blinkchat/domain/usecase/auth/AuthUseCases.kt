package com.jamie.blinkchat.domain.usecase.auth

import javax.inject.Inject

data class AuthUseCases @Inject constructor(
    val registerUser: RegisterUserUseCase,
    val loginUser: LoginUserUseCase,
    val getCurrentUser: GetCurrentUserUseCase,
    val logoutUser: LogoutUserUseCase,
    val observeAuthToken: ObserveAuthTokenUseCase
    // Add more auth-related use cases here if they arise
)