package com.jamie.blinkchat.presentation.ui.features.auth

import com.jamie.blinkchat.core.mvi.UiEffect
import com.jamie.blinkchat.core.mvi.UiIntent
import com.jamie.blinkchat.core.mvi.UiState

object RegisterContract {

    /**
     * Represents the state of the Register screen.
     */
    data class State(
        val username: String = "",
        val email: String = "",
        val password: String = "",
        val confirmPassword: String = "",
        val isLoading: Boolean = false,
        val errorMessage: String? = null,
        val registrationSuccess: Boolean = false
    ) : UiState

    /**
     * Defines the intents (user actions or events) for the Register screen.
     */
    sealed interface Intent : UiIntent {
        data class UsernameChanged(val username: String) : Intent
        data class EmailChanged(val email: String) : Intent
        data class PasswordChanged(val password: String) : Intent
        data class ConfirmPasswordChanged(val confirmPassword: String) : Intent
        object RegisterClicked : Intent
        object NavigateToLoginClicked : Intent
        object ErrorMessageShown : Intent
    }

    /**
     * Defines the one-time effects (side effects) for the Register screen.
     */
    sealed interface Effect : UiEffect {
        object NavigateToLogin : Effect
        data class ShowSuccessSnackbar(val message: String) : Effect
        data class ShowErrorSnackbar(val message: String) : Effect
    }
}