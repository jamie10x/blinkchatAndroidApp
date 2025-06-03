package com.jamie.blinkchat.presentation.ui.features.splash

import androidx.lifecycle.viewModelScope
import com.jamie.blinkchat.core.common.Resource
import com.jamie.blinkchat.core.mvi.BaseViewModel
import com.jamie.blinkchat.domain.usecase.auth.AuthUseCases
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

@HiltViewModel
class SplashViewModel @Inject constructor(
    private val authUseCases: AuthUseCases
) : BaseViewModel<SplashContract.State, SplashContract.Intent, SplashContract.Effect>() {

    init {
        // Automatically check auth status when ViewModel is created
        setIntent(SplashContract.Intent.CheckAuthStatus)
    }

    override fun createInitialState(): SplashContract.State {
        return SplashContract.State()
    }

    override fun handleIntent(intent: SplashContract.Intent) {
        when (intent) {
            is SplashContract.Intent.CheckAuthStatus -> {
                checkAuthenticationStatus()
            }
        }
    }

    private fun checkAuthenticationStatus() {
        viewModelScope.launch {
            // Add a small artificial delay for splash screen visibility,
            // remove if auth check is inherently long enough.
            delay(1000) // e.g., 1 second minimum splash time

            // Option 1: Observe the token directly (simpler, faster)
            val token = authUseCases.observeAuthToken().firstOrNull() // Get current token
            if (token != null && token.isNotBlank()) {
                Timber.d("Splash: Token found, attempting to validate by fetching user.")
                // Optionally, validate token by fetching user details
                // This confirms the token is not just present but also valid with the backend.
                authUseCases.getCurrentUser().onEach { result ->
                    when (result) {
                        is Resource.Loading -> {
                            Timber.d("Splash: Validating token (fetching user)...")
                            // State might already be isLoading = true from initialState
                        }
                        is Resource.Success -> {
                            Timber.d("Splash: Token valid, user ${result.data?.username} fetched. Navigating to ChatList.")
                            setState { copy(isLoading = false) }
                            setEffect { SplashContract.Effect.NavigateToChatList }
                        }
                        is Resource.Error -> {
                            Timber.w("Splash: Token invalid or user fetch failed (${result.message}). Navigating to Login.")
                            // Token exists but is invalid, or other error.
                            // AuthRepository's getCurrentUser might clear the token if it's 401/404.
                            setState { copy(isLoading = false) }
                            setEffect { SplashContract.Effect.NavigateToLogin }
                        }
                    }
                }.launchIn(viewModelScope) // Launch this nested flow collection
            } else {
                Timber.d("Splash: No token found. Navigating to Login.")
                setState { copy(isLoading = false) }
                setEffect { SplashContract.Effect.NavigateToLogin }
            }

            // Option 2 (Alternative): Directly try to get current user without checking token first.
            // The GetCurrentUserUseCase will handle the case of no token internally.
            /*
            authUseCases.getCurrentUser()
                .onEach { result ->
                    setState { copy(isLoading = false) } // Stop loading once we have a result
                    when (result) {
                        is Resource.Success -> {
                            Timber.d("Splash: User authenticated (${result.data?.username}). Navigating to ChatList.")
                            setEffect { SplashContract.Effect.NavigateToChatList }
                        }
                        is Resource.Error -> {
                            Timber.d("Splash: User not authenticated or error (${result.message}). Navigating to Login.")
                            setEffect { SplashContract.Effect.NavigateToLogin }
                        }
                        is Resource.Loading -> {
                            // Already handled by initial state or previous setState
                             setState { copy(isLoading = true) }
                        }
                    }
                }
                .launchIn(viewModelScope)
            */
        }
    }
}