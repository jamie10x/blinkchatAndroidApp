package com.jamie.blinkchat.domain.usecase.user

import com.jamie.blinkchat.core.common.Resource
import com.jamie.blinkchat.domain.model.User
import com.jamie.blinkchat.repositories.UserRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import timber.log.Timber // Import Timber
import javax.inject.Inject

class SearchUsersUseCase @Inject constructor(
    private val userRepository: UserRepository
) {
    operator fun invoke(query: String): Flow<Resource<List<User>>> {
        Timber.d("SearchUsersUseCase: Invoked with query: '$query'")
        if (query.isBlank()) {
            Timber.d("SearchUsersUseCase: Blank query, returning Resource.Success with empty list.")
            return flow { emit(Resource.Success(emptyList())) }
        }
        // The ViewModel now handles debouncing, so this use case directly calls the repository
        Timber.d("SearchUsersUseCase: Calling repository.searchUsers for query: '$query'")
        return userRepository.searchUsers(query)
    }
}