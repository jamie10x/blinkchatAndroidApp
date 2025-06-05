package com.jamie.blinkchat.presentation.ui.features.search

import com.jamie.blinkchat.core.mvi.UiEffect
import com.jamie.blinkchat.core.mvi.UiIntent
import com.jamie.blinkchat.core.mvi.UiState
import com.jamie.blinkchat.domain.model.User

object SearchUsersContract {

    /**
     * Represents the state of the User Search screen.
     *
     * @param searchQuery The current text in the search input field.
     * @param searchResults The list of users matching the search query.
     * @param isLoading True if a search operation is in progress.
     * @param errorMessage An error message if search fails or no results with specific message.
     * @param hasSearchedAtLeastOnce True if a search has been performed at least once.
     */
    data class State(
        val searchQuery: String = "",
        val searchResults: List<User> = emptyList(),
        val isLoading: Boolean = false,
        val errorMessage: String? = null,
        val hasSearchedAtLeastOnce: Boolean = false // To differentiate initial empty state from "no results found"
    ) : UiState

    /**
     * Defines the intents (user actions or events) for the User Search screen.
     */
    sealed interface Intent : UiIntent {
        data class SearchQueryChanged(val query: String) : Intent
        object PerformSearch : Intent // Could be triggered by a search button or automatically on query change (debounced)
        data class UserClicked(val userId: String, val username: String) : Intent // When a user from results is clicked
        object ErrorMessageShown : Intent
    }

    /**
     * Defines the one-time effects (side effects) for the User Search screen.
     */
    sealed interface Effect : UiEffect {
        // Navigate to chat screen, passing receiverId and username for a new chat
        data class NavigateToChat(val receiverId: String, val username: String) : Effect
        data class ShowSnackbar(val message: String) : Effect // For general messages or errors
    }
}