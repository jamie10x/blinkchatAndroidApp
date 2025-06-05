package com.jamie.blinkchat.domain.usecase.user

import com.jamie.blinkchat.core.common.Resource
import com.jamie.blinkchat.domain.model.User
import com.jamie.blinkchat.repositories.UserRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class GetUserProfileUseCase @Inject constructor(
    private val userRepository: UserRepository
) {
    /**
     * Gets a user's profile by their ID.
     *
     * @param userId The ID of the user.
     * @param forceRefresh Optionally force a network fetch, bypassing cache.
     * @return A Flow emitting a Resource wrapping a nullable [User] domain model.
     */
    operator fun invoke(userId: String, forceRefresh: Boolean = false): Flow<Resource<out User?>> {
        if (userId.isBlank()) {
            // Or throw IllegalArgumentException
            return kotlinx.coroutines.flow.flowOf(Resource.Error("User ID cannot be blank.", null))
        }
        return userRepository.getUserById(userId, forceRefresh)
    }
}