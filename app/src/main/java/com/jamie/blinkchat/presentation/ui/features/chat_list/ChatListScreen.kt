package com.jamie.blinkchat.presentation.ui.features.chat_list

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.pullrefresh.PullRefreshIndicator
import androidx.compose.material.pullrefresh.pullRefresh
import androidx.compose.material.pullrefresh.rememberPullRefreshState
import androidx.compose.material3.Badge
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.jamie.blinkchat.domain.model.ChatSummaryItem
import com.jamie.blinkchat.ui.theme.BlinkChatTheme
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale


@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterialApi::class)
@Composable
fun ChatListScreen(
    viewModel: ChatListViewModel = hiltViewModel(),
    onNavigateToChat: (chatId: String) -> Unit,
    onNavigateToLogin: () -> Unit
) {
    val state by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    // For new pull-to-refresh
    val pullRefreshState = rememberPullRefreshState(
        refreshing = state.isRefreshing,
        onRefresh = { viewModel.setIntent(ChatListContract.Intent.RefreshChatList) }
    )

    LaunchedEffect(key1 = Unit) {
        viewModel.effect.collectLatest { effect ->
            when (effect) {
                is ChatListContract.Effect.NavigateToChat -> {
                    onNavigateToChat(effect.chatId)
                }
                is ChatListContract.Effect.NavigateToLogin -> {
                    onNavigateToLogin()
                }
                is ChatListContract.Effect.ShowErrorSnackbar -> {
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
                title = { Text("BlinkChat") },
                actions = {
                    IconButton(onClick = { viewModel.setIntent(ChatListContract.Intent.LogoutClicked) }) {
                        Icon(Icons.AutoMirrored.Filled.ExitToApp, contentDescription = "Logout")
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .pullRefresh(pullRefreshState) // Apply pull-to-refresh modifier here
        ) {
            when {
                state.isLoading && state.chats.isEmpty() -> { // Initial loading state
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                }

                state.errorMessage != null && state.chats.isEmpty() -> { // Error state when no chats loaded
                    Column(
                        modifier = Modifier.fillMaxSize().padding(16.dp),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = state.errorMessage!!,
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.error
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(onClick = { viewModel.setIntent(ChatListContract.Intent.LoadChatList) }) {
                            Text("Retry")
                        }
                    }
                }

                state.chats.isEmpty() && !state.isLoading -> { // Empty state (no chats, not loading, no error)
                    Column(
                        modifier = Modifier.fillMaxSize().padding(16.dp),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            "No chats yet.",
                            style = MaterialTheme.typography.headlineSmall
                        )
                        Text(
                            "Start a new conversation!", // Placeholder for initiating new chat
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }

                else -> { // Display chat list
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(vertical = 8.dp)
                    ) {
                        items(state.chats, key = { it.chatId }) { chatItem ->
                            ChatListItem(
                                chatItem = chatItem,
                                onClick = { viewModel.setIntent(ChatListContract.Intent.ChatClicked(chatItem.chatId)) }
                            )
                            HorizontalDivider()
                        }
                    }
                }
            }

            PullRefreshIndicator(
                refreshing = state.isRefreshing,
                state = pullRefreshState,
                modifier = Modifier.align(Alignment.TopCenter)
            )

            // Show error as Snackbar if chats are already loaded but refresh failed
            if (state.errorMessage != null && state.chats.isNotEmpty() && !state.isLoading) {
                LaunchedEffect(state.errorMessage) { // Show snackbar when errorMessage changes and is not null
                    scope.launch {
                        snackbarHostState.showSnackbar(
                            message = state.errorMessage!!,
                            duration = SnackbarDuration.Short
                        )
                        viewModel.setIntent(ChatListContract.Intent.ErrorMessageShown) // Inform VM it's shown
                    }
                }
            }
        }
    }
}

@Composable
fun ChatListItem(
    chatItem: ChatSummaryItem,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Placeholder for Avatar - replace with actual Coil image loading later
        // Box(modifier = Modifier.size(48.dp).background(MaterialTheme.colorScheme.primaryContainer, CircleShape))

        Spacer(modifier = Modifier.width(16.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = chatItem.otherParticipantUsername,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = if (chatItem.unreadCount > 0) FontWeight.Bold else FontWeight.Normal
            )
            Spacer(modifier = Modifier.height(4.dp))
            val messagePrefix = if (chatItem.isLastMessageFromCurrentUser) "You: " else ""
            Text(
                text = "$messagePrefix${chatItem.lastMessagePreview ?: "No messages yet"}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                fontWeight = if (chatItem.unreadCount > 0) FontWeight.Bold else FontWeight.Normal
            )
        }
        Spacer(modifier = Modifier.width(16.dp))
        Column(horizontalAlignment = Alignment.End) {
            chatItem.lastMessageTimestamp?.let { timestamp ->
                Text(
                    text = formatTimestamp(timestamp), // Simple timestamp formatting
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (chatItem.unreadCount > 0) {
                Spacer(modifier = Modifier.height(4.dp))
                Badge { Text(chatItem.unreadCount.toString()) } // Material 3 Badge
            }
        }
    }
}

// Simple timestamp formatter
private fun formatTimestamp(timestampMillis: Long): String {
    val messageDate = Date(timestampMillis)
    val currentDate = Date()

    val sameDayFormat = SimpleDateFormat("h:mm a", Locale.getDefault())
    val otherDayFormat = SimpleDateFormat("MMM d", Locale.getDefault())

    // Check if the message was sent today
    val cal1 = java.util.Calendar.getInstance().apply { time = currentDate }
    val cal2 = java.util.Calendar.getInstance().apply { time = messageDate }

    return if (cal1.get(java.util.Calendar.YEAR) == cal2.get(java.util.Calendar.YEAR) &&
        cal1.get(java.util.Calendar.DAY_OF_YEAR) == cal2.get(java.util.Calendar.DAY_OF_YEAR)
    ) {
        sameDayFormat.format(messageDate)
    } else {
        otherDayFormat.format(messageDate)
        // Optionally, add year if not current year:
        val yearFormat = SimpleDateFormat("MMM d, yyyy", Locale.getDefault())
        if (cal1.get(java.util.Calendar.YEAR) != cal2.get(java.util.Calendar.YEAR)) yearFormat.format(messageDate) else otherDayFormat.format(messageDate)
    }
}


@OptIn(ExperimentalMaterial3Api::class)
@Preview(showBackground = true)
@Composable
fun ChatListScreenPreview_Loading() {
    BlinkChatTheme {
        val previewState = ChatListContract.State(isLoading = true, chats = emptyList())
        // This is a simplified way to preview. For complex states, a mock VM is better.
        Scaffold(topBar = { TopAppBar(title = {Text("BlinkChat")})}) { padding ->
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Preview(showBackground = true)
@Composable
fun ChatListScreenPreview_WithData() {
    BlinkChatTheme {
        val sampleChats = listOf(
            ChatSummaryItem("1", "Alice", "You: Hey!", System.currentTimeMillis() - 100000, "read", true, 0),
            ChatSummaryItem("2", "Bob", "See you then!", System.currentTimeMillis() - 2000000, "delivered", false, 2)
        )
        val previewState = ChatListContract.State(isLoading = false, chats = sampleChats)
        Scaffold(topBar = { TopAppBar(title = {Text("BlinkChat")})}) { padding ->
            LazyColumn(Modifier.padding(padding)) {
                items(sampleChats) { chat -> ChatListItem(chatItem = chat, onClick = {}) }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun ChatListItemPreview() {
    BlinkChatTheme {
        Surface {
            ChatListItem(
                chatItem = ChatSummaryItem(
                    chatId = "1",
                    otherParticipantUsername = "John Doe",
                    lastMessagePreview = "Okay, sounds good! Let's meet up later today then for sure.",
                    lastMessageTimestamp = System.currentTimeMillis() - 3600000, // 1 hour ago
                    lastMessageStatus = "read",
                    isLastMessageFromCurrentUser = false,
                    unreadCount = 3
                ),
                onClick = {}
            )
        }
    }
}