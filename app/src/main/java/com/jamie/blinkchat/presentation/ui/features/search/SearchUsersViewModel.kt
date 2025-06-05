package com.jamie.blinkchat.presentation.ui.features.search

import androidx.lifecycle.viewModelScope
import com.jamie.blinkchat.core.common.Resource
import com.jamie.blinkchat.core.mvi.BaseViewModel
import com.jamie.blinkchat.domain.usecase.user.UserUseCases
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

@HiltViewModel
class SearchUsersViewModel @Inject constructor(
    private val userUseCases: UserUseCases
) : BaseViewModel<SearchUsersContract.State, SearchUsersContract.Intent, SearchUsersContract.Effect>() {

    private var searchJob: Job? = null
    private var currentQuery: String = ""

    companion object {
        private const val SEARCH_DEBOUNCE_MS = 500L
    }

    override fun createInitialState(): SearchUsersContract.State {
        return SearchUsersContract.State()
    }

    override fun handleIntent(intent: SearchUsersContract.Intent) {
        when (intent) {
            is SearchUsersContract.Intent.SearchQueryChanged -> {
                val newQuery = intent.query
                setState { copy(searchQuery = newQuery, errorMessage = null) }
                currentQuery = newQuery

                searchJob?.cancel()
                if (newQuery.isNotBlank() && newQuery.length >= 2) {
                    searchJob = viewModelScope.launch {
                        delay(SEARCH_DEBOUNCE_MS)
                        if (newQuery == currentQuery) {
                            performSearch(newQuery)
                        }
                    }
                } else {
                    setState { copy(searchResults = emptyList(), isLoading = false, hasSearchedAtLeastOnce = uiState.value.hasSearchedAtLeastOnce) }
                }
            }
            is SearchUsersContract.Intent.PerformSearch -> {
                searchJob?.cancel()
                val queryToSearch = uiState.value.searchQuery
                if (queryToSearch.isNotBlank() && queryToSearch.length >= 2) {
                    performSearch(queryToSearch)
                } else if (queryToSearch.isNotBlank() && queryToSearch.length < 2) {
                    setState { copy(errorMessage = "Please enter at least 2 characters to search.")}
                }
                else {
                    setState { copy(searchResults = emptyList(), isLoading = false) }
                }
            }
            is SearchUsersContract.Intent.UserClicked -> {
                setEffect { SearchUsersContract.Effect.NavigateToChat(intent.userId, intent.username) }
            }
            is SearchUsersContract.Intent.ErrorMessageShown -> {
                setState { copy(errorMessage = null) }
            }
        }
    }

    private fun performSearch(query: String) {
        if (query.isBlank()) {
            setState { copy(searchResults = emptyList(), isLoading = false, hasSearchedAtLeastOnce = true) }
            return
        }

        userUseCases.searchUsers(query)
            .onEach { result ->
                when (result) {
                    is Resource.Loading -> {
                        Timber.d("UserSearch: Loading for query '$query'")
                        setState { copy(isLoading = true, errorMessage = null, hasSearchedAtLeastOnce = true) }
                    }
                    is Resource.Success -> {
                        val users = result.data ?: emptyList()
                        Timber.d("UserSearch: Success, found ${users.size} users for query '$query'")
                        setState {
                            copy(
                                isLoading = false,
                                searchResults = users,
                                errorMessage = if (users.isEmpty()) "No users found matching your search." else null,
                                hasSearchedAtLeastOnce = true
                            )
                        }
                    }
                    is Resource.Error -> {
                        Timber.w("UserSearch: Error for query '$query' - ${result.message}")
                        setState {
                            copy(
                                isLoading = false,
                                searchResults = emptyList(),
                                errorMessage = result.message ?: "An error occurred during search.",
                                hasSearchedAtLeastOnce = true
                            )
                        }
                    }
                }
            }
            .launchIn(viewModelScope)
    }
}