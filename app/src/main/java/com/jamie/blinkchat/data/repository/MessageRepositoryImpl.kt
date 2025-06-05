package com.jamie.blinkchat.data.repository

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.jamie.blinkchat.core.common.Resource
import com.jamie.blinkchat.core.common.toEpochMillis
import com.jamie.blinkchat.data.local.dato.ChatSummaryDao
import com.jamie.blinkchat.data.local.dato.MessageDao
import com.jamie.blinkchat.data.local.dato.UserDao
import com.jamie.blinkchat.data.model.local.MessageEntity
import com.jamie.blinkchat.data.model.local.UserEntity
import com.jamie.blinkchat.data.model.remote.ApiErrorDto
import com.jamie.blinkchat.data.model.remote.CreateMessageRequestDto
import com.jamie.blinkchat.data.model.remote.MessageDto
import com.jamie.blinkchat.data.model.remote.websockets.ClientMessageStatusUpdatePayloadDto
import com.jamie.blinkchat.data.model.remote.websockets.ClientNewMessagePayloadDto
import com.jamie.blinkchat.data.model.remote.websockets.ClientTypingIndicatorPayloadDto
import com.jamie.blinkchat.data.model.remote.websockets.ClientWebSocketSendType
import com.jamie.blinkchat.data.model.remote.websockets.ServerErrorPayloadDto
import com.jamie.blinkchat.data.model.remote.websockets.ServerMessageSentAckPayloadDto
import com.jamie.blinkchat.data.model.remote.websockets.ServerMessageStatusUpdatePayloadDto
import com.jamie.blinkchat.data.model.remote.websockets.ServerNewMessagePayloadDto
import com.jamie.blinkchat.data.model.remote.websockets.ServerTypingIndicatorPayloadDto
import com.jamie.blinkchat.data.model.remote.websockets.ServerWebSocketReceiveType
import com.jamie.blinkchat.data.model.remote.websockets.WebSocketConnectionState
import com.jamie.blinkchat.data.model.remote.websockets.WebSocketEvent
import com.jamie.blinkchat.data.model.remote.websockets.WebSocketManager
import com.jamie.blinkchat.data.remote.ChatApiService
import com.jamie.blinkchat.data.workers.SyncMessagesWorker
import com.jamie.blinkchat.domain.model.Message
import com.jamie.blinkchat.domain.model.TypingIndicatorEvent
import com.jamie.blinkchat.repositories.AuthRepository
import com.jamie.blinkchat.repositories.MessageRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.serializer
import retrofit2.HttpException
import timber.log.Timber
import java.io.IOException
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MessageRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context, // Injected for WorkManager
    private val chatApiService: ChatApiService,
    private val messageDao: MessageDao,
    private val userDao: UserDao,
    private val chatSummaryDao: ChatSummaryDao,
    private val webSocketManager: WebSocketManager,
    private val authRepository: AuthRepository,
    private val json: Json
) : MessageRepository {

    private val repositoryScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var currentUserId: String? = null
    private val _typingIndicatorEvents = MutableSharedFlow<TypingIndicatorEvent>(replay = 0, extraBufferCapacity = 16)

    init {
        repositoryScope.launch {
            currentUserId = authRepository.observeToken().firstOrNull()?.let { token ->
                if (token.isNotBlank()) {
                    val userResource = authRepository.getCurrentUser()
                    if (userResource is Resource.Success) userResource.data?.id else null
                } else null
            }
            Timber.d("MessageRepository initialized. Current User ID: $currentUserId")
        }
        observeWebSocketEvents()
    }

    override fun sendMessage(
        content: String,
        chatId: String?,
        receiverId: String?,
        clientTempId: String
    ): Flow<Resource<out Message>> = flow {
        val capturedCurrentUserId = currentUserId
        if (capturedCurrentUserId == null) {
            emit(Resource.Error("Cannot send message: User not authenticated.", data = null))
            return@flow
        }

        val optimisticTimestamp = System.currentTimeMillis()
        val optimisticMessageEntity = MessageEntity(
            id = clientTempId,
            chatId = chatId ?: "temp_chat_${UUID.randomUUID()}",
            senderId = capturedCurrentUserId,
            receiverId = receiverId,
            content = content,
            timestamp = optimisticTimestamp,
            status = MessageEntity.STATUS_SENDING,
            clientTempId = clientTempId,
            isOptimistic = true
        )
        messageDao.insertMessage(optimisticMessageEntity)
        val senderUsername = userDao.getUserById(capturedCurrentUserId)?.username ?: "You"
        emit(Resource.Success(optimisticMessageEntity.toDomain(senderUsername, capturedCurrentUserId)))
        Timber.d("Optimistic message ($clientTempId) saved with status SENDING and emitted to UI.")

        val sendSuccess = trySendActualMessage(optimisticMessageEntity)

        if (!sendSuccess) {
            Timber.w("Initial send attempt failed for $clientTempId. Worker will attempt sync if network was the issue.")
            // If an IOException occurred, scheduleMessageSyncWorker was already called in trySendActualMessage
        }
    }.flowOn(Dispatchers.IO)


    private suspend fun trySendActualMessage(messageToSync: MessageEntity): Boolean {
        val clientTempId = messageToSync.clientTempId ?: run {
            Timber.e("trySendActualMessage: messageToSync.clientTempId is null. Cannot process.")
            return false
        }
        val capturedCurrentUserId = currentUserId ?: return false

        try {
            if (webSocketManager.connectionState.value == WebSocketConnectionState.Connected) {
                Timber.d("Attempting to send message via WebSocket: $clientTempId")
                val wsPayload = ClientNewMessagePayloadDto(
                    chatId = messageToSync.chatId.takeIf { !it.startsWith("temp_chat_") },
                    receiverId = messageToSync.receiverId,
                    content = messageToSync.content,
                    clientTempId = clientTempId
                )
                webSocketManager.sendTypedMessage(
                    type = ClientWebSocketSendType.NEW_MESSAGE,
                    payload = wsPayload,
                    payloadSerializer = ClientNewMessagePayloadDto.serializer()
                )
                return true
            } else {
                Timber.w("WebSocket not connected. Attempting to send message via REST: $clientTempId")
                val restRequest = CreateMessageRequestDto(
                    chatId = messageToSync.chatId.takeIf { !it.startsWith("temp_chat_") },
                    receiverId = messageToSync.receiverId,
                    content = messageToSync.content
                )
                val response = chatApiService.sendMessage(restRequest)
                if (response.isSuccessful && response.body() != null) {
                    val serverMessageDto = response.body()!!
                    messageDao.confirmSentMessage(
                        clientTempId = clientTempId,
                        serverId = serverMessageDto.id,
                        status = MessageEntity.STATUS_SENT,
                        serverTimestamp = serverMessageDto.timestamp.toEpochMillis() ?: System.currentTimeMillis()
                    )
                    Timber.d("Message ($clientTempId -> ${serverMessageDto.id}) sent via REST and confirmed.")
                    messageDao.getMessageById(serverMessageDto.id)?.let {
                        updateChatSummaryLastMessage(it)
                    }
                    return true
                } else {
                    messageDao.updateMessageStatus(clientTempId, MessageEntity.STATUS_FAILED)
                    Timber.e("Failed to send message via REST: ${response.code()} for $clientTempId. Marked as FAILED.")
                    scheduleMessageSyncWorker() // Schedule sync if REST fails with API error too
                    return false
                }
            }
        } catch (e: IOException) {
            Timber.e(e, "IOException during send attempt for $clientTempId. Scheduling sync worker.")
            scheduleMessageSyncWorker()
            return false
        } catch (e: Exception) {
            Timber.e(e, "Unexpected exception sending message for $clientTempId. Marked as FAILED.")
            messageDao.updateMessageStatus(clientTempId, MessageEntity.STATUS_FAILED)
            scheduleMessageSyncWorker()
            return false
        }
    }

    private fun scheduleMessageSyncWorker() {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val syncWorkRequest = OneTimeWorkRequestBuilder<SyncMessagesWorker>()
            .setConstraints(constraints)
            .build()

        WorkManager.getInstance(context).enqueueUniqueWork(
            SyncMessagesWorker.WORK_NAME,
            ExistingWorkPolicy.KEEP,
            syncWorkRequest
        )
        Timber.d("SyncMessagesWorker enqueued due to send failure or offline attempt.")
    }

    // ... (getMessagesForChat, loadOlderMessages, updateMessageStatus, observeWebSocketEvents, handleIncomingWebSocketMessage, sendTypingIndicator, observeTypingIndicators, mappers, parseError - all remain the same as the last full version)
    override fun getMessagesForChat(chatId: String): Flow<List<Message>> {
        val capturedUserId = currentUserId
        if (capturedUserId == null) return flowOf(emptyList())

        return messageDao.getMessagesForChatFlow(chatId).map { entities ->
            entities.map { entity ->
                val senderUsername = userDao.getUserById(entity.senderId)?.username ?: "Unknown"
                entity.toDomain(senderUsername, capturedUserId)
            }
        }.flowOn(Dispatchers.IO)
    }

    override suspend fun loadOlderMessages(chatId: String, beforeTimestamp: Long, limit: Int): Resource<Int> {
        return withContext(Dispatchers.IO) {
            val capturedUserId = currentUserId
            if (capturedUserId == null) return@withContext Resource.Error("User not authenticated", 0)

            try {
                val actualOffset = beforeTimestamp.toInt()
                val response = chatApiService.getMessagesForChat(chatId, limit, actualOffset)

                if (response.isSuccessful && response.body() != null) {
                    val messageDtos = response.body()!!
                    if (messageDtos.isNotEmpty()) {
                        val messageEntities = messageDtos.map { it.toEntity() }
                        messageDao.insertMessages(messageEntities)

                        val usersToSave = messageDtos.mapNotNull { it.sender?.toEntity() }.distinctBy { it.id }
                        if (usersToSave.isNotEmpty()) {
                            userDao.insertUsers(usersToSave)
                        }
                        Resource.Success(messageDtos.size)
                    } else {
                        Resource.Success(0)
                    }
                } else {
                    parseError(response.code(), response.errorBody()?.string())
                }
            } catch (e: HttpException) {
                parseError(e.code(), e.response()?.errorBody()?.string())
            } catch (e: IOException) {
                Resource.Error("Network error loading older messages.", 0)
            } catch (e: Exception) {
                Resource.Error("Unexpected error: ${e.localizedMessage}", 0)
            }
        }
    }

    override suspend fun updateMessageStatus(chatId: String, messageIds: List<String>, status: String) {
        if (currentUserId == null) return
        messageIds.forEach { messageId ->
            messageDao.updateMessageStatus(messageId, status)
        }
        messageIds.forEach { messageId ->
            val payload = ClientMessageStatusUpdatePayloadDto(messageId, chatId, status)
            webSocketManager.sendTypedMessage(
                type = ClientWebSocketSendType.MESSAGE_STATUS_UPDATE,
                payload = payload,
                payloadSerializer = ClientMessageStatusUpdatePayloadDto.serializer()
            )
        }
        Timber.d("Sent status update ($status) for messages in chat $chatId via WebSocket.")
    }

    override fun observeWebSocketEvents() {
        repositoryScope.launch {
            webSocketManager.events.collect { event ->
                Timber.d("MessageRepository received WebSocket Event: $event")
                when (event) {
                    is WebSocketEvent.MessageReceived -> {
                        handleIncomingWebSocketMessage(event.type, event.payload)
                    }
                    is WebSocketEvent.ConnectionEstablished -> {
                        Timber.i("WebSocket connected. Ready for real-time updates. Consider syncing pending messages.")
                        launch { scheduleMessageSyncWorker() } // Attempt sync on (re)connect
                    }
                    is WebSocketEvent.ConnectionClosed -> {
                        Timber.i("WebSocket closed: ${event.code} - ${event.reason}")
                    }
                    is WebSocketEvent.ConnectionFailed -> {
                        Timber.e(event.error, "WebSocket connection failed: ${event.responseMessage}")
                    }
                    is WebSocketEvent.ConnectionClosing -> {
                        Timber.i("WebSocket connection is closing.")
                    }
                }
            }
        }
    }

    private suspend fun handleIncomingWebSocketMessage(type: String, payloadElement: JsonElement) {
        withContext(Dispatchers.IO) {
            val capturedUserId = currentUserId
            if (capturedUserId == null) {
                Timber.w("Received WebSocket message but currentUserId is null. Ignoring.")
                return@withContext
            }

            try {
                when (type) {
                    ServerWebSocketReceiveType.NEW_MESSAGE -> {
                        val messagePayload = json.decodeFromJsonElement(ServerNewMessagePayloadDto.serializer(), payloadElement)
                        Timber.d("Received NEW_MESSAGE via WebSocket: ${messagePayload.id}")
                        val messageEntity = messagePayload.toEntity()
                        messageDao.insertMessage(messageEntity)
                        updateChatSummaryLastMessage(messageEntity, !messageEntity.isFromCurrentUser(capturedUserId))
                        messagePayload.sender?.let { userDao.insertUser(it.toEntity()) }
                    }
                    ServerWebSocketReceiveType.MESSAGE_SENT_ACK -> {
                        val ackPayload = json.decodeFromJsonElement(ServerMessageSentAckPayloadDto.serializer(), payloadElement)
                        Timber.d("Received MESSAGE_SENT_ACK for clientTempId: ${ackPayload.clientTempId}, serverId: ${ackPayload.messageId}")
                        ackPayload.clientTempId?.let { tempId ->
                            messageDao.confirmSentMessage(
                                clientTempId = tempId,
                                serverId = ackPayload.messageId,
                                status = MessageEntity.STATUS_SENT,
                                serverTimestamp = ackPayload.timestamp.toEpochMillis() ?: System.currentTimeMillis()
                            )
                            messageDao.getMessageById(ackPayload.messageId)?.let {
                                updateChatSummaryLastMessage(it)
                            }
                        }
                    }
                    ServerWebSocketReceiveType.MESSAGE_STATUS_UPDATE -> {
                        val statusUpdate = json.decodeFromJsonElement(ServerMessageStatusUpdatePayloadDto.serializer(), payloadElement)
                        Timber.d("Received MESSAGE_STATUS_UPDATE: msgId=${statusUpdate.messageId}, status=${statusUpdate.status}")
                        messageDao.updateMessageStatus(statusUpdate.messageId, statusUpdate.status)
                        chatSummaryDao.getChatSummaryById(statusUpdate.chatId)?.let { summary ->
                            if (summary.lastMessageId == statusUpdate.messageId) {
                                messageDao.getMessageById(statusUpdate.messageId)?.let {
                                    updateChatSummaryLastMessage(it)
                                }
                            }
                        }
                    }
                    ServerWebSocketReceiveType.TYPING_INDICATOR -> {
                        val typingPayload = json.decodeFromJsonElement(ServerTypingIndicatorPayloadDto.serializer(), payloadElement)
                        Timber.d("Received TYPING_INDICATOR: chatId=${typingPayload.chatId}, user=${typingPayload.userId}, isTyping=${typingPayload.isTyping}")
                        _typingIndicatorEvents.tryEmit(
                            TypingIndicatorEvent(
                                chatId = typingPayload.chatId,
                                userId = typingPayload.userId,
                                isTyping = typingPayload.isTyping
                            )
                        )
                    }
                    ServerWebSocketReceiveType.ERROR -> {
                        val errorPayload = json.decodeFromJsonElement(ServerErrorPayloadDto.serializer(), payloadElement)
                        Timber.e("Received WebSocket ERROR: ${errorPayload.message} (Code: ${errorPayload.code}) for original type: ${errorPayload.originalMessageType}")
                    }
                    else -> Timber.w("Received unknown WebSocket message type: $type")
                }
            } catch (e: Exception) {
                Timber.e(e, "Error processing incoming WebSocket payload for type '$type': ${payloadElement.toString()}")
            }
        }
    }

    override suspend fun sendTypingIndicator(chatId: String, isTyping: Boolean) {
        val capturedUserId = currentUserId ?: return
        val payload = ClientTypingIndicatorPayloadDto(
            chatId = chatId,
            userId = capturedUserId,
            isTyping = isTyping
        )
        webSocketManager.sendTypedMessage(
            type = ClientWebSocketSendType.TYPING_INDICATOR,
            payload = payload,
            payloadSerializer = ClientTypingIndicatorPayloadDto.serializer()
        )
    }

    override fun observeTypingIndicators(chatId: String): Flow<TypingIndicatorEvent> {
        val capturedUserId = currentUserId
        return _typingIndicatorEvents.asSharedFlow()
            .filter { event ->
                event.chatId == chatId && event.userId != capturedUserId
            }
            .flowOn(Dispatchers.Default)
    }

    suspend fun getPendingMessagesForSync(): List<MessageEntity> {
        return messageDao.getPendingMessages()
    }

    suspend fun retrySendingMessage(messageEntity: MessageEntity): Boolean {
        Timber.d("Worker: Retrying message ${messageEntity.clientTempId ?: messageEntity.id}")
        return trySendActualMessage(messageEntity)
    }

    private suspend fun updateChatSummaryLastMessage(message: MessageEntity, incrementUnread: Boolean = false) {
        val capturedUserId = currentUserId ?: return
        chatSummaryDao.updateLastMessage(
            chatId = message.chatId,
            lastMessageId = message.id,
            content = message.content,
            timestamp = message.timestamp,
            senderId = message.senderId,
            status = message.status,
            incrementUnreadCount = incrementUnread && !message.isFromCurrentUser(capturedUserId)
        )
    }

    private fun MessageDto.toEntity(): MessageEntity {
        return MessageEntity(
            id = this.id,
            chatId = this.chatId,
            senderId = this.senderId,
            receiverId = null,
            content = this.content,
            timestamp = this.timestamp.toEpochMillis() ?: System.currentTimeMillis(),
            status = this.status,
            clientTempId = null,
            isOptimistic = false
        )
    }

    private fun com.jamie.blinkchat.data.model.remote.PublicUserDto.toEntity(): UserEntity {
        return UserEntity(
            id = this.id,
            username = this.username,
            email = this.email,
            createdAt = this.createdAt,
            updatedAt = this.updatedAt
        )
    }

    private fun MessageEntity.isFromCurrentUser(currentUserId: String): Boolean {
        return this.senderId == currentUserId
    }

    private fun MessageEntity.toDomain(senderUsername: String, currentUserId: String): Message {
        return Message(
            id = this.id,
            chatId = this.chatId,
            senderId = this.senderId,
            senderUsername = senderUsername,
            content = this.content,
            timestamp = this.timestamp,
            status = this.status,
            isFromCurrentUser = this.isFromCurrentUser(currentUserId),
            clientTempId = this.clientTempId,
            isOptimistic = this.isOptimistic
        )
    }

    private fun <T> parseError(errorCode: Int, errorBody: String?): Resource<T> {
        val defaultMessage = "API Error ($errorCode)"
        return try {
            if (errorBody != null) {
                val apiError = json.decodeFromString<ApiErrorDto>(errorBody)
                Resource.Error(apiError.error, data = null, errorCode = errorCode)
            } else {
                Resource.Error(defaultMessage, data = null, errorCode = errorCode)
            }
        } catch (e: Exception) {
            Timber.e(e, "Failed to parse error body: $errorBody")
            Resource.Error(defaultMessage, data = null, errorCode = errorCode)
        }
    }
}