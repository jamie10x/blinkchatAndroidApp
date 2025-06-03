package com.jamie.blinkchat.presentation.ui.features.auth

import androidx.lifecycle.viewModelScope
import com.jamie.blinkchat.core.common.Resource
import com.jamie.blinkchat.core.mvi.BaseViewModel
import com.jamie.blinkchat.data.model.remote.LoginUserRequestDto
import com.jamie.blinkchat.domain.usecase.auth.AuthUseCases
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import timber.log.Timber
import javax.inject.Inject

@HiltViewModel
class LoginViewModel @Inject constructor(
    private val authUseCases: AuthUseCases
) : BaseViewModel<LoginContract.State, LoginContract.Intent, LoginContract.Effect>() {

    override fun createInitialState(): LoginContract.State {
        return LoginContract.State()
    }

    override fun handleIntent(intent: LoginContract.Intent) {
        when (intent) {
            is LoginContract.Intent.EmailChanged -> {
                setState { copy(email = intent.email, errorMessage = null) }
            }
            is LoginContract.Intent.PasswordChanged -> {
                setState { copy(password = intent.password, errorMessage = null) }
            }
            is LoginContract.Intent.LoginClicked -> {
                loginUser()
            }
            is LoginContract.Intent.NavigateToRegisterClicked -> {
                setEffect { LoginContract.Effect.NavigateToRegister }
            }
            is LoginContract.Intent.ErrorMessageShown -> {
                // Clear the error message from state after it has been shown by the UI
                setState { copy(errorMessage = null) }
            }
        }
    }

    private fun loginUser() {
        // Basic validation (can be more sophisticated)
        if (uiState.value.email.isBlank() || uiState.value.password.isBlank()) {
            setState { copy(errorMessage = "Email and password cannot be empty.") }
            return
        }
        // Could add email format validation here too

        val request = LoginUserRequestDto(
            email = uiState.value.email.trim(),
            password = uiState.value.password
        )

        authUseCases.loginUser(request)
            .onEach { result ->
                when (result) {
                    is Resource.Loading -> {
                        Timber.d("Login: Loading...")
                        setState { copy(isLoading = true, errorMessage = null) }
                    }
                    is Resource.Success -> {
                        Timber.d("Login: Success for user ${result.data?.user?.email}")
                        setState { copy(isLoading = false, isLoginSuccess = true, errorMessage = null) }
                        setEffect { LoginContract.Effect.NavigateToChatList }
                    }
                    is Resource.Error -> {
                        Timber.w("Login: Error - ${result.message}")
                        setState { copy(isLoading = false, errorMessage = result.message ?: "An unknown error occurred") }
                    }
                }
            }
            .launchIn(viewModelScope)
    }
}