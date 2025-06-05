package com.jamie.blinkchat.domain.usecase.user

import javax.inject.Inject

data class UserUseCases @Inject constructor(
    val searchUsers: SearchUsersUseCase,
    val getUserProfile: GetUserProfileUseCase
    // Add other user-related use cases here if they arise
)