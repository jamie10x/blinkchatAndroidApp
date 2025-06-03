package com.jamie.blinkchat.presentation.ui.features.auth

import com.jamie.blinkchat.core.mvi.UiEffect
import com.jamie.blinkchat.core.mvi.UiIntent
import com.jamie.blinkchat.core.mvi.UiState

object LoginContract {

    /**
     * Represents the state of the Login screen.
     *
     * @param email The current value of the email input field.
     * @param password The current value of the password input field.
     * @param isLoading True if a login operation is in progress, false otherwise.
     * @param errorMessage A message to display if an error occurs, null otherwise.
     * @param isLoginSuccess True if login was successful, to trigger navigation.
     */
    data class State(
        val email: String = "",
        val password: String = "",
        val isLoading: Boolean = false,
        val errorMessage: String? = null,
        val isLoginSuccess: Boolean = false // Could also be handled purely by an Effect
    ) : UiState

    /**
     * Defines the intents (user actions or events) for the Login screen.
     */
    sealed interface Intent : UiIntent {
        data class EmailChanged(val email: String) : Intent
        data class PasswordChanged(val password: String) : Intent
        object LoginClicked : Intent
        object NavigateToRegisterClicked : Intent
        object ErrorMessageShown : Intent
    }

    /**
     * Defines the one-time effects (side effects) for the Login screen.
     */
    sealed interface Effect : UiEffect {
        object NavigateToChatList : Effect
        object NavigateToRegister : Effect
        data class ShowErrorSnackbar(val message: String) : Effect
    }

}