package com.jamie.blinkchat.domain.usecase.auth

import com.jamie.blinkchat.repositories.AuthRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class ObserveAuthTokenUseCase @Inject constructor(
    private val authRepository: AuthRepository
) {
    operator fun invoke(): Flow<String?> {
        return authRepository.observeToken()
    }
}