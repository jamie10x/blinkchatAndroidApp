package com.jamie.blinkchat.presentation.ui.features.chat

import android.annotation.SuppressLint
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
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
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.surfaceColorAtElevation
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.jamie.blinkchat.domain.model.Message
import com.jamie.blinkchat.ui.theme.BlinkChatTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import timber.log.Timber
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ChatScreen(
    viewModel: ChatViewModel = hiltViewModel(),
    onNavigateBack: () -> Unit
) {
    val state by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val lazyListState = rememberLazyListState()

    LaunchedEffect(key1 = Unit) {
        viewModel.effect.collectLatest { effect ->
            when (effect) {
                is ChatContract.Effect.ShowErrorSnackbar -> {
                    scope.launch {
                        snackbarHostState.showSnackbar(effect.message, duration = SnackbarDuration.Short)
                    }
                }
                is ChatContract.Effect.MessageSentSuccessfully -> { /* Input field cleared by VM */ }
                is ChatContract.Effect.ScrollToBottom -> {
                    if (state.messages.isNotEmpty()) {
                        scope.launch {
                            delay(100)
                            lazyListState.animateScrollToItem(state.messages.size - 1)
                        }
                    }
                }
            }
        }
    }

    // Trigger loading older messages
    LaunchedEffect(lazyListState, state.canLoadMoreOlderMessages, state.isLoadingOlderMessages) {
        snapshotFlow { lazyListState.firstVisibleItemIndex }
            .filter { index ->
                index < 3 &&
                        state.messages.isNotEmpty() &&
                        state.canLoadMoreOlderMessages &&
                        !state.isLoadingOlderMessages
            }
            .distinctUntilChanged()
            .collect {
                Timber.d("ChatScreen: Reached top (index $it), loading older messages.")
                viewModel.setIntent(ChatContract.Intent.LoadOlderMessages)
            }
    }

    // Auto-scroll for new messages
    LaunchedEffect(state.messages.size, state.messages.lastOrNull()?.id) {
        if (state.messages.isNotEmpty()) {
            val lastMessage = state.messages.last()
            val lastVisibleItem = lazyListState.layoutInfo.visibleItemsInfo.lastOrNull()
            val isNearBottom = lastVisibleItem != null && (state.messages.size - 1 - lastVisibleItem.index) < 3
            val isOwnOptimisticMessage = lastMessage.isFromCurrentUser && lastMessage.isOptimistic

            if (isOwnOptimisticMessage || isNearBottom) {
                scope.launch {
                    delay(100)
                    lazyListState.animateScrollToItem(state.messages.size - 1)
                }
            }
        }
    }

    // LaunchedEffect for Read Receipts
    LaunchedEffect(remember { derivedStateOf { lazyListState.layoutInfo } }, state.messages, state.currentUserId) {
        snapshotFlow { lazyListState.layoutInfo }
            .map { layoutInfo ->
                layoutInfo.visibleItemsInfo
                    .mapNotNull { itemInfo ->
                        if (itemInfo.index >= 0 && itemInfo.index < state.messages.size) {
                            state.messages[itemInfo.index]
                        } else {
                            null
                        }
                    }
                    .filter { message ->
                        state.currentUserId != null &&
                                message.senderId != state.currentUserId &&
                                message.status != Message.STATUS_READ &&
                                !message.isOptimistic
                    }
                    .map { it.id }
            }
            .distinctUntilChanged()
            .filter { it.isNotEmpty() }
            .collectLatest { unreadVisibleMessageIds ->
                Timber.d("Visible unread messages from others: $unreadVisibleMessageIds to mark as READ")
                viewModel.setIntent(ChatContract.Intent.MessagesDisplayed(unreadVisibleMessageIds))
            }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(state.otherParticipantUsername ?: state.chatId ?: "Chat", maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (state.isOtherUserTyping) {
                        Text("Typing...", fontStyle = FontStyle.Italic, modifier = Modifier.padding(end = 12.dp))
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
                modifier = Modifier.imePadding()
            )
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
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
                        Text(state.loadMessagesError!!, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyLarge)
                        Spacer(modifier = Modifier.height(8.dp))
                        Button(onClick = { viewModel.setIntent(ChatContract.Intent.RetryLoadMessages) }) {
                            Text("Retry")
                        }
                    }
                }
                else -> {
                    LazyColumn(
                        state = lazyListState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp),
                    ) {
                        if (state.isLoadingOlderMessages && state.canLoadMoreOlderMessages) {
                            item {
                                Box(modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp), contentAlignment = Alignment.Center) {
                                    CircularProgressIndicator()
                                }
                            }
                        }
                        itemsIndexed(state.messages, key = { _, message -> message.clientTempId ?: message.id }) { index, message ->
                            val showDateHeader = index == 0 || isDifferentDay(state.messages.getOrNull(index - 1)?.timestamp, message.timestamp)
                            if (showDateHeader) {
                                DateHeader(timestamp = message.timestamp)
                            }
                            MessageBubble(
                                message = message,
                                isFromCurrentUser = message.isFromCurrentUser
                            )
                        }
                    }
                }
            }

            if (state.sendMessageError != null) {
                LaunchedEffect(state.sendMessageError) {
                    scope.launch {
                        snackbarHostState.showSnackbar(
                            message = state.sendMessageError!!,
                            duration = SnackbarDuration.Short
                        )
                        viewModel.setIntent(ChatContract.Intent.ClearSendMessageError)
                    }
                }
            } else if (state.loadMessagesError != null && state.messages.isNotEmpty()) {
                LaunchedEffect(state.loadMessagesError) {
                    scope.launch {
                        snackbarHostState.showSnackbar(
                            message = state.loadMessagesError!!,
                            duration = SnackbarDuration.Short
                        )
                        viewModel.setIntent(ChatContract.Intent.ClearLoadMessagesError)
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
        shadowElevation = 4.dp,
        color = MaterialTheme.colorScheme.surfaceColorAtElevation(3.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.Bottom
        ) {
            OutlinedTextField(
                value = text,
                onValueChange = onTextChanged,
                modifier = Modifier.weight(1f),
                placeholder = { Text(if (isConnected) "Message..." else "Connecting...") },
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Sentences,
                    imeAction = ImeAction.Send
                ),
                keyboardActions = KeyboardActions(onSend = {
                    if (text.isNotBlank() && isConnected) onSendClicked()
                }),
                maxLines = 5,
                shape = RoundedCornerShape(24.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
                    cursorColor = MaterialTheme.colorScheme.primary
                ),
            )
            Spacer(modifier = Modifier.width(8.dp))
            IconButton(
                onClick = onSendClicked,
                enabled = text.isNotBlank() && isConnected,
                modifier = Modifier.size(48.dp),
                colors = IconButtonDefaults.filledIconButtonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    disabledContainerColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f),
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
    val bubbleColor = if (isFromCurrentUser) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondaryContainer
    val textColor = if (isFromCurrentUser) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSecondaryContainer
    // This outer alignment determines if the whole bubble+status column is to the left or right
    val columnHorizontalAlignment = if (isFromCurrentUser) Alignment.End else Alignment.Start

    val bubbleShape = RoundedCornerShape(
        topStart = if (isFromCurrentUser) 16.dp else 4.dp,
        topEnd = if (isFromCurrentUser) 4.dp else 16.dp,
        bottomStart = 16.dp,
        bottomEnd = 16.dp
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                start = if (isFromCurrentUser) 48.dp else 8.dp, // Less indent for other user, more for self
                end = if (isFromCurrentUser) 8.dp else 48.dp,   // Less indent for self, more for other user
                top = 4.dp,
                bottom = 4.dp
            ),
        horizontalAlignment = columnHorizontalAlignment // This aligns the Box and Row below it
    ) {
        Box(
            modifier = Modifier
                .widthIn(max = LocalConfiguration.current.screenWidthDp.dp * 0.75f) // Max width for bubble
                .clip(bubbleShape)
                .background(bubbleColor)
                .padding(horizontal = 12.dp, vertical = 8.dp)
            // The content within this Box (the Text) will naturally align start due to Box's default
        ) {
            Text(text = message.content, style = MaterialTheme.typography.bodyLarge, color = textColor)
        }
        Row(
            modifier = Modifier.padding(top = 2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (message.isOptimistic && message.status == Message.STATUS_SENDING) {
                Text(
                    "Sending...",
                    style = MaterialTheme.typography.labelSmall,
                    fontStyle = FontStyle.Italic,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else if (!message.isOptimistic) {
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
        Message.STATUS_DELIVERED -> Icons.Filled.Done
        Message.STATUS_READ -> Icons.Filled.Done
        else -> null
    }
    val iconColor = if (status == Message.STATUS_READ) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)

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
    val formatter = SimpleDateFormat("MMMM d, yyyy", Locale.getDefault())
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 16.dp, horizontal = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = formatter.format(Date(timestamp)),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                .padding(horizontal = 10.dp, vertical = 4.dp)
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

// --- Previews ---
@Preview(showBackground = true, device = "id:pixel_5")
@Composable
fun ChatScreenPreview_Empty() {
    BlinkChatTheme {
        ChatScreen(onNavigateBack = {})
    }
}

@Preview(showBackground = true)
@Composable
fun MessageBubbleCurrentUserPreview() {
    BlinkChatTheme {
        Surface(color = MaterialTheme.colorScheme.background) {
            MessageBubble(
                message = Message("1", "c1", "u1", "Jamie", "Hello there, this is a bit of a longer message to see how it wraps!", System.currentTimeMillis(), Message.STATUS_READ, true, isOptimistic = false),
                isFromCurrentUser = true
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
fun MessageBubbleOtherUserPreview() {
    BlinkChatTheme {
        Surface(color = MaterialTheme.colorScheme.background) {
            MessageBubble(
                message = Message("2", "c1", "u2", "Alex", "Hi Jamie! How are you today? Hope you're doing well.", System.currentTimeMillis() - 5000, Message.STATUS_DELIVERED, false, isOptimistic = false),
                isFromCurrentUser = false
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
fun MessageInputBarPreview() {
    BlinkChatTheme {
        MessageInputBar(text = "Hello", onTextChanged = {}, onSendClicked = {}, isConnected = true)
    }
}

@Preview(showBackground = true)
@Composable
fun MessageInputBarDisconnectedPreview() {
    BlinkChatTheme {
        MessageInputBar(text = "", onTextChanged = {}, onSendClicked = {}, isConnected = false)
    }
}

@Preview(showBackground = true)
@Composable
fun DateHeaderPreview(){
    BlinkChatTheme{
        DateHeader(timestamp = System.currentTimeMillis())
    }
}