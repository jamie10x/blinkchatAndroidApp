package com.jamie.blinkchat.domain.usecase.auth

import com.jamie.blinkchat.core.common.Resource
import com.jamie.blinkchat.data.model.remote.CreateUserRequestDto
import com.jamie.blinkchat.domain.model.AuthResult
import com.jamie.blinkchat.repositories.AuthRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import javax.inject.Inject

class RegisterUserUseCase @Inject constructor(
    private val authRepository: AuthRepository
) {
    operator fun invoke(request: CreateUserRequestDto): Flow<Resource<AuthResult>> = flow {
        emit(Resource.Loading())
        val result = authRepository.register(request)
        emit(result)
    }
}