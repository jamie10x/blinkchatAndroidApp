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
    private var currentQueryForDebounce: String = "" // To manage debounce effectively

    companion object {
        private const val SEARCH_DEBOUNCE_MS = 500L
        private const val MIN_QUERY_LENGTH = 2 // Minimum query length for auto-search
    }

    override fun createInitialState(): SearchUsersContract.State {
        Timber.d("SearchVM: Creating initial state.")
        return SearchUsersContract.State()
    }

    override fun handleIntent(intent: SearchUsersContract.Intent) {
        Timber.d("SearchVM: Handling intent: $intent")
        when (intent) {
            is SearchUsersContract.Intent.SearchQueryChanged -> {
                val newQuery = intent.query
                setState { copy(searchQuery = newQuery, errorMessage = null, isLoading = if (newQuery.length >= MIN_QUERY_LENGTH) uiState.value.isLoading else false) }
                currentQueryForDebounce = newQuery

                searchJob?.cancel()
                if (newQuery.isNotBlank() && newQuery.length >= MIN_QUERY_LENGTH) {
                    Timber.d("SearchVM: Query '$newQuery' is valid for debounced search.")
                    searchJob = viewModelScope.launch {
                        delay(SEARCH_DEBOUNCE_MS)
                        if (newQuery == currentQueryForDebounce) { // Check if query is still the same after delay
                            Timber.d("SearchVM: Debounce finished, performing search for '$newQuery'")
                            performSearch(newQuery)
                        } else {
                            Timber.d("SearchVM: Query changed during debounce, new query is '$currentQueryForDebounce'. Old query '$newQuery' search cancelled.")
                        }
                    }
                } else {
                    Timber.d("SearchVM: Query '$newQuery' is too short or blank, clearing results.")
                    setState { copy(searchResults = emptyList(), isLoading = false, hasSearchedAtLeastOnce = uiState.value.hasSearchedAtLeastOnce.takeIf { it } ?: newQuery.isNotBlank()) }
                }
            }
            is SearchUsersContract.Intent.PerformSearch -> {
                searchJob?.cancel()
                val queryToSearch = uiState.value.searchQuery.trim() // Trim before checking length
                Timber.d("SearchVM: PerformSearch intent for query: '$queryToSearch'")
                if (queryToSearch.length >= MIN_QUERY_LENGTH) {
                    performSearch(queryToSearch)
                } else if (queryToSearch.isNotBlank()){
                    setState { copy(errorMessage = "Please enter at least $MIN_QUERY_LENGTH characters to search.", isLoading = false, searchResults = emptyList())}
                } else { // Query is blank
                    setState { copy(searchResults = emptyList(), isLoading = false, errorMessage = null) }
                }
            }
            is SearchUsersContract.Intent.UserClicked -> {
                Timber.d("SearchVM: User clicked: userId=${intent.userId}, username=${intent.username}")
                setEffect { SearchUsersContract.Effect.NavigateToChat(intent.userId, intent.username) }
            }
            is SearchUsersContract.Intent.ErrorMessageShown -> {
                Timber.d("SearchVM: ErrorMessageShown intent, clearing error message.")
                setState { copy(errorMessage = null) }
            }
        }
    }

    private fun performSearch(query: String) {
        Timber.d("SearchVM: performSearch executing for query: '$query'")
        // Cancel any previous search job if one was running from a direct PerformSearch
        // Note: searchJob here primarily refers to the debounced job.
        // A new direct search can also be started.
        viewModelScope.launch { // Ensure each performSearch call is a new launch
            userUseCases.searchUsers(query)
                .onEach { result ->
                    Timber.d("SearchVM: Received result from use case for query '$query': $result")
                    when (result) {
                        is Resource.Loading -> {
                            setState { copy(isLoading = true, errorMessage = null, hasSearchedAtLeastOnce = true) }
                        }
                        is Resource.Success -> {
                            val users = result.data ?: emptyList()
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
                .launchIn(this)
        }
    }
}