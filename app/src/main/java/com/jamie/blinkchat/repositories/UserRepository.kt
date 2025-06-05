package com.jamie.blinkchat.repositories

import com.jamie.blinkchat.core.common.Resource
import com.jamie.blinkchat.domain.model.User
import kotlinx.coroutines.flow.Flow

interface UserRepository {

    /**
     * Searches for users based on a search term.
     * Results are fetched from the network and can be cached locally.
     *
     * @param searchTerm The term to search for (e.g., email, username).
     * @return A Flow emitting a Resource wrapping a list of [User] domain models.
     */
    fun searchUsers(searchTerm: String): Flow<Resource<List<User>>>

    /**
     * Gets a user's profile by their ID.
     * Tries to fetch from local cache first, then falls back to network if not found or stale.
     *
     * @param userId The ID of the user to fetch.
     * @param forceNetworkFetch If true, fetches from network even if cached data exists.
     * @return A Flow emitting a Resource wrapping a [User] domain model.
     */
    fun getUserById(userId: String, forceNetworkFetch: Boolean = false): Flow<Resource<out User>>

    // Optional: A method to observe a user from local DB directly
    // fun observeUserByIdLocal(userId: String): Flow<User?>
}