package com.jamie.blinkchat.domain.usecase.auth

import com.jamie.blinkchat.core.common.Resource
import com.jamie.blinkchat.domain.model.User
import com.jamie.blinkchat.repositories.AuthRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import timber.log.Timber
import javax.inject.Inject

class GetCurrentUserUseCase @Inject constructor(
    private val authRepository: AuthRepository
) {
        operator fun invoke(): Flow<Resource<User>> = flow {
            Timber.d("GetCurrentUserUseCase: invoked")
            emit(Resource.Loading())
            Timber.d("GetCurrentUserUseCase: Emitted Loading")

            val result = authRepository.getCurrentUser()
            Timber.d("GetCurrentUserUseCase: authRepository.getCurrentUser() result: $result")
            emit(result)
            Timber.d("GetCurrentUserUseCase: Emitted final result.")
        }
}