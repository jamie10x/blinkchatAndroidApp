package com.jamie.blinkchat.domain.usecase.auth

import com.jamie.blinkchat.core.common.Resource
import com.jamie.blinkchat.repositories.AuthRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import javax.inject.Inject

class LogoutUserUseCase @Inject constructor(
    private val authRepository: AuthRepository
) {
    operator fun invoke(): Flow<Resource<Unit>> = flow { // Emits Resource<Unit> for success/failure indication
        emit(Resource.Loading())
        try {
            authRepository.logout()
            emit(Resource.Success(Unit)) // Indicate success with Unit
        } catch (e: Exception) {
            // Log the exception if needed
            emit(Resource.Error("Logout failed: ${e.localizedMessage}", data = Unit))
        }
    }
}