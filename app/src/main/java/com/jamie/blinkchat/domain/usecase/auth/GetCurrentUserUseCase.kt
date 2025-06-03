package com.jamie.blinkchat.domain.usecase.auth

import com.jamie.blinkchat.core.common.Resource
import com.jamie.blinkchat.domain.model.User
import com.jamie.blinkchat.repositories.AuthRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import javax.inject.Inject

class GetCurrentUserUseCase @Inject constructor(
    private val authRepository: AuthRepository
) {
    operator fun invoke(): Flow<Resource<User>> = flow {
        emit(Resource.Loading())
        // Check if token exists first. If not, no need to call API.
        // This basic check can be enhanced.
        var token: String? = null
        authRepository.observeToken().collect { token = it; return@collect }

        if (token == null) {
            emit(Resource.Error("User not authenticated. No token found.", errorCode = 401))
        } else {
            val result = authRepository.getCurrentUser()
            emit(result)
        }
    }
}