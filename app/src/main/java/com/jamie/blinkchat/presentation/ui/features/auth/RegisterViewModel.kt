package com.jamie.blinkchat.presentation.ui.features.auth

import androidx.lifecycle.viewModelScope
import com.jamie.blinkchat.core.common.Resource
import com.jamie.blinkchat.core.mvi.BaseViewModel
import com.jamie.blinkchat.data.model.remote.CreateUserRequestDto
import com.jamie.blinkchat.domain.usecase.auth.AuthUseCases
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import timber.log.Timber
import javax.inject.Inject

@HiltViewModel
class RegisterViewModel @Inject constructor(
    private val authUseCases: AuthUseCases
) : BaseViewModel<RegisterContract.State, RegisterContract.Intent, RegisterContract.Effect>() {

    override fun createInitialState(): RegisterContract.State {
        return RegisterContract.State()
    }

    override fun handleIntent(intent: RegisterContract.Intent) {
        when (intent) {
            is RegisterContract.Intent.UsernameChanged -> {
                setState { copy(username = intent.username.trim(), errorMessage = null) }
            }
            is RegisterContract.Intent.EmailChanged -> {
                setState { copy(email = intent.email.trim(), errorMessage = null) }
            }
            is RegisterContract.Intent.PasswordChanged -> {
                setState { copy(password = intent.password, errorMessage = null) }
            }
            is RegisterContract.Intent.ConfirmPasswordChanged -> {
                setState { copy(confirmPassword = intent.confirmPassword, errorMessage = null) }
            }
            is RegisterContract.Intent.RegisterClicked -> {
                registerUser()
            }
            is RegisterContract.Intent.NavigateToLoginClicked -> {
                setEffect { RegisterContract.Effect.NavigateToLogin }
            }
            is RegisterContract.Intent.ErrorMessageShown -> {
                setState { copy(errorMessage = null) }
            }
        }
    }

    private fun registerUser() {
        val currentState = uiState.value
        // Basic client-side validation
        if (currentState.username.isBlank() || currentState.email.isBlank() ||
            currentState.password.isBlank() || currentState.confirmPassword.isBlank()
        ) {
            setState { copy(errorMessage = "All fields are required.") }
            return
        }
        if (currentState.password.length < 6) { // As per API docs
            setState { copy(errorMessage = "Password must be at least 6 characters long.") }
            return
        }
        if (currentState.password != currentState.confirmPassword) {
            setState { copy(errorMessage = "Passwords do not match.") }
            return
        }
        // TODO: Add more robust email validation (e.g., regex or Patterns.EMAIL_ADDRESS)
        if (!android.util.Patterns.EMAIL_ADDRESS.matcher(currentState.email).matches()) {
            setState { copy(errorMessage = "Invalid email format.")}
            return
        }


        val request = CreateUserRequestDto(
            username = currentState.username,
            email = currentState.email,
            password = currentState.password
        )

        authUseCases.registerUser(request)
            .onEach { result ->
                when (result) {
                    is Resource.Loading -> {
                        Timber.d("Registration: Loading...")
                        setState { copy(isLoading = true, errorMessage = null) }
                    }
                    is Resource.Success -> {
                        Timber.d("Registration: Success for user ${result.data?.user?.email}")
                        // Don't automatically log in; registration success means user needs to log in.
                        // Token received here is stored by AuthRepository, but we navigate to login.
                        setState { copy(isLoading = false, registrationSuccess = true, errorMessage = null) }
                        setEffect { RegisterContract.Effect.ShowSuccessSnackbar("Registration successful! Please log in.") }
                        setEffect { RegisterContract.Effect.NavigateToLogin }
                    }
                    is Resource.Error -> {
                        Timber.w("Registration: Error - ${result.message}")
                        setState { copy(isLoading = false, errorMessage = result.message ?: "An unknown error occurred") }
                    }
                }
            }
            .launchIn(viewModelScope)
    }
}