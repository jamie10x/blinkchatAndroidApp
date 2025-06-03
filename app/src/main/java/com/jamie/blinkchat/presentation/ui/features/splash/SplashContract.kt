package com.jamie.blinkchat.presentation.ui.features.splash

import com.jamie.blinkchat.core.mvi.UiEffect
import com.jamie.blinkchat.core.mvi.UiIntent
import com.jamie.blinkchat.core.mvi.UiState

object SplashContract {

    data class State(
        val isLoading: Boolean = true
    ) : UiState

    sealed interface Intent : UiIntent {
        object CheckAuthStatus : Intent
    }

    sealed interface Effect : UiEffect {
        object NavigateToLogin : Effect
        object NavigateToChatList : Effect
    }
}