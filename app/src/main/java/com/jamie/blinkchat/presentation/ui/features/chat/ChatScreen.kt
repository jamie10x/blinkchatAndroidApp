package com.jamie.blinkchat.presentation.ui.features.chat

import android.annotation.SuppressLint
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Done
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.jamie.blinkchat.domain.model.Message
import com.jamie.blinkchat.ui.theme.BlinkChatTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import timber.log.Timber
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ChatScreen(
    // chatId is now handled by ViewModel via SavedStateHandle
    viewModel: ChatViewModel = hiltViewModel(),
    onNavigateBack: () -> Unit
) {
    val state by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val lazyListState = rememberLazyListState()
    val keyboardController = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current

    // Effect collection for navigation, snackbars, scrolling
    LaunchedEffect(key1 = Unit) {
        viewModel.effect.collectLatest { effect ->
            when (effect) {
                is ChatContract.Effect.ShowErrorSnackbar -> {
                    scope.launch {
                        snackbarHostState.showSnackbar(effect.message, duration = SnackbarDuration.Short)
                    }
                }
                is ChatContract.Effect.MessageSentSuccessfully -> {
                    // Input field is cleared by ViewModel state update
                }
                is ChatContract.Effect.ScrollToBottom -> {
                    if (state.messages.isNotEmpty()) {
                        // Delay slightly to allow new item to render before scrolling
                        scope.launch {
                            delay(100)
                            lazyListState.animateScrollToItem(state.messages.size - 1)
                        }
                    }
                }
            }
        }
    }

    // Trigger loading older messages when scrolling near the top
    LaunchedEffect(lazyListState) {
        snapshotFlow { lazyListState.firstVisibleItemIndex }
            .collect { firstVisibleIndex ->
                if (firstVisibleIndex < 3 && state.messages.isNotEmpty() && state.canLoadMoreOlderMessages && !state.isLoadingOlderMessages) {
                    Timber.d("ChatScreen: Reached top, loading older messages.")
                    viewModel.setIntent(ChatContract.Intent.LoadOlderMessages)
                }
            }
    }

    // When new messages arrive and we are near the bottom, scroll down.
    LaunchedEffect(state.messages.size) {
        if (state.messages.isNotEmpty()) {
            val lastVisibleItemIndex = lazyListState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            // If the last few items are visible or it's a new message from current user
            if ( (state.messages.size - lastVisibleItemIndex < 5) ||
                (state.messages.lastOrNull()?.isFromCurrentUser == true && state.messages.lastOrNull()?.isOptimistic == true) ) {
                scope.launch {
                    delay(100) // allow layout
                    lazyListState.animateScrollToItem(state.messages.size - 1)
                }
            }
        }
    }


    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(state.otherParticipantUsername ?: state.chatId ?: "Chat") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (state.isOtherUserTyping) { // Placeholder for typing indicator
                        Text("Typing...", fontStyle = FontStyle.Italic, modifier = Modifier.padding(end = 8.dp))
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = {
            MessageInputBar(
                text = state.currentInputText,
                onTextChanged = { viewModel.setIntent(ChatContract.Intent.InputTextChanged(it)) },
                onSendClicked = { viewModel.setIntent(ChatContract.Intent.SendMessageClicked) },
                isConnected = state.isConnected,
                modifier = Modifier
                    .imePadding() // Handles IME padding automatically
                    .navigationBarsPadding() // Add padding for navigation bar if needed
            )
        }
    ) { paddingValues ->
        Box(modifier = Modifier.fillMaxSize().padding(paddingValues)) {
            when {
                state.isLoadingMessages && state.messages.isEmpty() -> {
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                }
                state.loadMessagesError != null && state.messages.isEmpty() -> {
                    Column(
                        modifier = Modifier.fillMaxSize().padding(16.dp),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(state.loadMessagesError!!, color = MaterialTheme.colorScheme.error)
                        Spacer(modifier = Modifier.height(8.dp))
                        Button(onClick = { viewModel.setIntent(ChatContract.Intent.RetryLoadMessages) }) {
                            Text("Retry")
                        }
                    }
                }
                // No need for "empty chats" state as a chat screen implies a chat exists.
                // If messages list is empty after load, it just shows an empty list.
                else -> {
                    LazyColumn(
                        state = lazyListState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp),
                        reverseLayout = false // Keep true if you want input at bottom and messages growing up
                        // Set to false if input is at bottom and messages also appear from top down.
                        // For typical chat apps, reverseLayout = true is common when new messages appear at the bottom.
                        // Let's adjust this if needed after seeing behavior.
                        // For now: false, and we scroll to bottom.
                    ) {
                        if (state.isLoadingOlderMessages && state.canLoadMoreOlderMessages) {
                            item {
                                Box(modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp), contentAlignment = Alignment.Center) {
                                    CircularProgressIndicator()
                                }
                            }
                        }

                        itemsIndexed(state.messages, key = { _, message -> message.id }) { index, message ->
                            val showDateHeader = index == 0 || isDifferentDay(state.messages.getOrNull(index - 1)?.timestamp, message.timestamp)
                            if (showDateHeader) {
                                DateHeader(timestamp = message.timestamp)
                            }
                            MessageBubble(
                                message = message,
                                // Corrected: Use the pre-calculated field from the domain model
                                isFromCurrentUser = message.isFromCurrentUser
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun MessageInputBar(
    text: String,
    onTextChanged: (String) -> Unit,
    onSendClicked: () -> Unit,
    isConnected: Boolean,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shadowElevation = 4.dp, // Or tonalElevation for M3
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.95f) // Slight transparency or distinct color
    ) {
        Row(
            modifier = Modifier
                .padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = text,
                onValueChange = onTextChanged,
                modifier = Modifier.weight(1f),
                placeholder = { Text(if (isConnected) "Type a message..." else "Connecting...") },
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Sentences,
                    imeAction = ImeAction.Send
                ),
                keyboardActions = KeyboardActions(onSend = { if (text.isNotBlank() && isConnected) onSendClicked() }),
                maxLines = 5, // Allow multi-line input
                shape = RoundedCornerShape(24.dp), // Rounded corners
                colors = OutlinedTextFieldDefaults.colors( // M3 OutlinedTextFieldDefaults
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
                )
            )
            Spacer(modifier = Modifier.width(8.dp))
            IconButton(
                onClick = onSendClicked,
                enabled = text.isNotBlank() && isConnected,
                colors = IconButtonDefaults.iconButtonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    disabledContainerColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f),
                    disabledContentColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                )
            ) {
                Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Send Message")
            }
        }
    }
}

@SuppressLint("ConfigurationScreenWidthHeight")
@Composable
fun MessageBubble(message: Message, isFromCurrentUser: Boolean) {
    val bubbleColor = if (isFromCurrentUser) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.secondaryContainer
    val textColor = if (isFromCurrentUser) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSecondaryContainer
    val alignment = if (isFromCurrentUser) Alignment.CenterEnd else Alignment.CenterStart
    val bubbleShape = if (isFromCurrentUser) {
        RoundedCornerShape(topStart = 16.dp, topEnd = 4.dp, bottomStart = 16.dp, bottomEnd = 16.dp)
    } else {
        RoundedCornerShape(topStart = 4.dp, topEnd = 16.dp, bottomStart = 16.dp, bottomEnd = 16.dp)
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalAlignment = alignment as Alignment.Horizontal
    ) {
        Box(
            modifier = Modifier
                .widthIn(max = LocalConfiguration.current.screenWidthDp.dp * 0.75f) // Max width for bubble
                .clip(bubbleShape)
                .background(bubbleColor)
                .padding(horizontal = 12.dp, vertical = 8.dp)
        ) {
            Column {
                // If not from current user and group messages by same sender, show username
                // if (!isFromCurrentUser) {
                //    Text(message.senderUsername ?: "Unknown", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = textColor.copy(alpha = 0.7f))
                // }
                Text(text = message.content, style = MaterialTheme.typography.bodyLarge, color = textColor)
            }
        }
        Row(
            modifier = Modifier.padding(start = if (!isFromCurrentUser) 8.dp else 0.dp, end = if (isFromCurrentUser) 8.dp else 0.dp, top = 2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (message.isOptimistic) {
                Text(
                    "Sending...",
                    style = MaterialTheme.typography.labelSmall,
                    fontStyle = FontStyle.Italic,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                Text(
                    text = formatMessageTimestamp(message.timestamp),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (isFromCurrentUser && message.status != Message.STATUS_SENDING) {
                    Spacer(modifier = Modifier.width(4.dp))
                    MessageStatusIcon(status = message.status)
                }
            }
        }
    }
}

@Composable
fun MessageStatusIcon(status: String) {
    val icon = when (status) {
        Message.STATUS_SENT -> Icons.Filled.Done
        Message.STATUS_DELIVERED -> Icons.Filled.Done // Double tick
        Message.STATUS_READ -> Icons.Filled.Done // Double tick, colored for read
        else -> null // Or a clock icon for "sending" if not handled by text
    }
    val iconColor = if (status == Message.STATUS_READ) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant

    icon?.let {
        Icon(
            imageVector = it,
            contentDescription = "Status: $status",
            tint = iconColor,
            modifier = Modifier.size(16.dp)
        )
    }
}


@Composable
fun DateHeader(timestamp: Long) {
    val formatter = SimpleDateFormat("MMMM d, yyyy", Locale.getDefault()) // e.g., June 3, 2025
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 16.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = formatter.format(Date(timestamp)),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .background(MaterialTheme.colorScheme.surfaceColorAtElevation(2.dp), RoundedCornerShape(8.dp))
                .padding(horizontal = 8.dp, vertical = 4.dp)
        )
    }
}

private fun isDifferentDay(timestamp1: Long?, timestamp2: Long): Boolean {
    if (timestamp1 == null) return true
    val cal1 = java.util.Calendar.getInstance().apply { timeInMillis = timestamp1 }
    val cal2 = java.util.Calendar.getInstance().apply { timeInMillis = timestamp2 }
    return cal1.get(java.util.Calendar.DAY_OF_YEAR) != cal2.get(java.util.Calendar.DAY_OF_YEAR) ||
            cal1.get(java.util.Calendar.YEAR) != cal2.get(java.util.Calendar.YEAR)
}

private fun formatMessageTimestamp(timestampMillis: Long): String {
    return SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date(timestampMillis))
}


@Preview(showBackground = true, device = "id:pixel_5")
@Composable
fun ChatScreenPreview() {
    BlinkChatTheme {
        // This preview will be very basic as it doesn't have a real ViewModel or chatId.
        // More comprehensive previews would mock the ViewModel state.
        ChatScreen(onNavigateBack = {})
    }
}

@Preview
@Composable
fun MessageBubbleCurrentUserPreview() {
    BlinkChatTheme {
        MessageBubble(
            message = Message("1", "c1", "u1", "Jamie", "Hello there!", System.currentTimeMillis(), Message.STATUS_READ, true, isOptimistic = false),
            isFromCurrentUser = true
        )
    }
}

@Preview
@Composable
fun MessageBubbleOtherUserPreview() {
    BlinkChatTheme {
        MessageBubble(
            message = Message("2", "c1", "u2", "Alex", "Hi Jamie! How are you?", System.currentTimeMillis() - 5000, Message.STATUS_DELIVERED, false, isOptimistic = false),
            isFromCurrentUser = false
        )
    }
}