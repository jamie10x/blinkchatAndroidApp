package com.jamie.blinkchat.domain.usecase.user

import com.jamie.blinkchat.repositories.UserRepository
import com.jamie.blinkchat.core.common.Resource
import com.jamie.blinkchat.domain.model.User
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import javax.inject.Inject

class SearchUsersUseCase @Inject constructor(
    private val userRepository: UserRepository
) {
    /**
     * Searches for users based on a query.
     * Includes a debounce-like delay before actually performing the search
     * if the query is not blank.
     *
     * @param query The search term.
     * @return A Flow emitting a Resource wrapping a list of [User] domain models.
     */
    operator fun invoke(query: String): Flow<Resource<List<User>>> {
        if (query.isBlank()) {
            return flow { emit(Resource.Success(emptyList())) } // Emit empty list immediately for blank query
        }

        // Return the flow from the repository directly.
        // The repository handles emitting Loading and then Success/Error.
        // Debouncing could also be handled in the ViewModel by delaying intent processing,
        // but for simplicity here, we can rely on the repository or add a small delay if needed.
        // For a true debounce, the ViewModel would typically manage it.
        // Let's assume for now the ViewModel will handle any necessary debounce before calling this.
        return userRepository.searchUsers(query)

        // Example if you wanted a small fixed delay within the use case itself:
        /*
        return flow {
            emit(Resource.Loading())
            delay(300) // Small delay to simulate debounce or reduce rapid API calls
            userRepository.searchUsers(query).collect { emit(it) }
        }
        */
    }
}