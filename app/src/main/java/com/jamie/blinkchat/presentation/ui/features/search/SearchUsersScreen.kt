package com.jamie.blinkchat.presentation.ui.features.search

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.jamie.blinkchat.domain.model.User
import com.jamie.blinkchat.ui.theme.BlinkChatTheme
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchUsersScreen(
    viewModel: SearchUsersViewModel = hiltViewModel(),
    onNavigateBack: () -> Unit,
    onNavigateToChat: (receiverId: String, username: String) -> Unit
) {
    val state by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val keyboardController = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current

    //LaunchedEffect to focus the search field on screen open
    LaunchedEffect(Unit) {
        // Consider if you want to auto-focus and show keyboard.
        // For now, let's not auto-show keyboard, but focus can be requested.
        // focusRequester.requestFocus() // Needs a FocusRequester
    }

    LaunchedEffect(key1 = Unit) {
        viewModel.effect.collectLatest { effect ->
            when (effect) {
                is SearchUsersContract.Effect.NavigateToChat -> {
                    keyboardController?.hide()
                    focusManager.clearFocus()
                    onNavigateToChat(effect.receiverId, effect.username)
                }
                is SearchUsersContract.Effect.ShowSnackbar -> {
                    scope.launch {
                        snackbarHostState.showSnackbar(
                            message = effect.message,
                            duration = SnackbarDuration.Short
                        )
                    }
                }
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Search Users") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp)
        ) {
            OutlinedTextField(
                value = state.searchQuery,
                onValueChange = { viewModel.setIntent(SearchUsersContract.Intent.SearchQueryChanged(it)) },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Search by email or username") },
                placeholder = { Text("Enter search query...") },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = "Search Icon") },
                trailingIcon = {
                    if (state.searchQuery.isNotEmpty()) {
                        IconButton(onClick = { viewModel.setIntent(SearchUsersContract.Intent.SearchQueryChanged("")) }) {
                            Icon(Icons.Filled.Clear, contentDescription = "Clear Search")
                        }
                    }
                },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = {
                    keyboardController?.hide()
                    focusManager.clearFocus()
                    viewModel.setIntent(SearchUsersContract.Intent.PerformSearch)
                }),
                singleLine = true
            )
            Spacer(modifier = Modifier.height(16.dp))

            when {
                state.isLoading -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }
                state.errorMessage != null -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(state.errorMessage!!, color = MaterialTheme.colorScheme.error)
                    }
                }
                state.searchResults.isEmpty() && state.hasSearchedAtLeastOnce -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("No users found matching your criteria.")
                    }
                }
                state.searchResults.isEmpty() && !state.hasSearchedAtLeastOnce && state.searchQuery.isBlank() -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("Enter a query to search for users.")
                    }
                }
                else -> {
                    LazyColumn(modifier = Modifier.fillMaxSize()) {
                        items(state.searchResults, key = { it.id }) { user ->
                            UserListItem(
                                user = user,
                                onClick = {
                                    viewModel.setIntent(SearchUsersContract.Intent.UserClicked(user.id, user.username))
                                }
                            )
                            Divider()
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun UserListItem(
    user: User,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp, horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Placeholder for Avatar
        // Box(modifier = Modifier.size(40.dp).background(MaterialTheme.colorScheme.secondaryContainer, CircleShape))
        Spacer(modifier = Modifier.width(16.dp))
        Column {
            Text(user.username, style = MaterialTheme.typography.titleMedium)
            Text(user.email, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Preview(showBackground = true, device = "id:pixel_5")
@Composable
fun SearchUsersScreenPreview_Empty() {
    BlinkChatTheme {
        SearchUsersScreen(onNavigateBack = {}, onNavigateToChat = { _, _ -> })
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Preview(showBackground = true, device = "id:pixel_5")
@Composable
fun SearchUsersScreenPreview_WithResults() {
    // This preview would need a mock ViewModel providing state.
    // For simplicity, we'll just show the structure.
    BlinkChatTheme {
        val sampleUsers = listOf(
            User("1", "Alice Wonderland", "alice@example.com", "", ""),
            User("2", "Bob The Builder", "bob@example.com", "", "")
        )
        // Simulate state directly for preview if not using mock ViewModel
        Scaffold(topBar = { TopAppBar(title = { Text("Search Users") }) }) { padding ->
            Column(Modifier.padding(padding).padding(16.dp)) {
                OutlinedTextField(value = "test", onValueChange = {}, label = { Text("Search") }, modifier = Modifier.fillMaxWidth())
                Spacer(modifier = Modifier.height(16.dp))
                LazyColumn {
                    items(sampleUsers) { user -> UserListItem(user = user, onClick = {}) }
                }
            }
        }
    }
}