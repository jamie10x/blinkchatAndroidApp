package com.jamie.blinkchat.data.repository

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
import com.jamie.blinkchat.domain.model.Message
import com.jamie.blinkchat.repositories.AuthRepository
import com.jamie.blinkchat.repositories.MessageRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import retrofit2.HttpException
import timber.log.Timber
import java.io.IOException
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MessageRepositoryImpl @Inject constructor(
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

    init {
        repositoryScope.launch {
            currentUserId = authRepository.observeToken().firstOrNull()?.let { token ->
                if (token.isNotBlank()) {
                    // Ensure Resource.Success is handled properly, casting might be risky if it's Error/Loading
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
        if (currentUserId == null) {
            emit(Resource.Error("Cannot send message: User not authenticated.", data = null))
            return@flow
        }
        val capturedCurrentUserId = currentUserId!! // Non-null assertion after check

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
        Timber.d("Optimistic message sent to UI: $clientTempId")

        if (webSocketManager.connectionState.value == WebSocketConnectionState.Connected) {
            Timber.d("Attempting to send message via WebSocket: $clientTempId")
            val wsPayload = ClientNewMessagePayloadDto(
                chatId = chatId,
                receiverId = receiverId,
                content = content,
                clientTempId = clientTempId
            )
            webSocketManager.sendTypedMessage(
                type = ClientWebSocketSendType.NEW_MESSAGE,
                payload = wsPayload,
                payloadSerializer = ClientNewMessagePayloadDto.serializer()
            )
        } else {
            Timber.w("WebSocket not connected. Attempting to send message via REST: $clientTempId")
            try {
                val restRequest = CreateMessageRequestDto(chatId, receiverId, content)
                val response = chatApiService.sendMessage(restRequest)
                if (response.isSuccessful && response.body() != null) {
                    val serverMessageDto = response.body()!!
                    messageDao.deleteMessageById(clientTempId)
                    val confirmedEntity = serverMessageDto.toEntity() // Removed unused currentUserId
                    messageDao.insertMessage(confirmedEntity)
                    val confirmedSenderUsername = userDao.getUserById(confirmedEntity.senderId)?.username ?: "Unknown"
                    emit(Resource.Success(confirmedEntity.toDomain(confirmedSenderUsername, capturedCurrentUserId)))
                    Timber.d("Message sent via REST and confirmed: ${serverMessageDto.id}")
                    updateChatSummaryLastMessage(confirmedEntity)
                } else {
                    messageDao.updateMessageStatus(clientTempId, MessageEntity.STATUS_FAILED)
                    val error = parseError<MessageDto>(response.code(), response.errorBody()?.string()) // Use .code property
                    emit(Resource.Error(error.message ?: "Failed to send message via REST", data = null))
                    Timber.e("Failed to send message via REST: ${response.code()} for $clientTempId")
                }
            } catch (e: Exception) {
                messageDao.updateMessageStatus(clientTempId, MessageEntity.STATUS_FAILED)
                emit(Resource.Error("Network error sending message: ${e.localizedMessage}", data = null))
                Timber.e(e, "Exception sending message via REST for $clientTempId")
            }
        }
    }.flowOn(Dispatchers.IO)


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
                val actualOffset = beforeTimestamp.toInt() // Still using this simplification
                val response = chatApiService.getMessagesForChat(chatId, limit, actualOffset)

                if (response.isSuccessful && response.body() != null) {
                    val messageDtos = response.body()!!
                    if (messageDtos.isNotEmpty()) {
                        val messageEntities = messageDtos.map { it.toEntity() } // Removed unused currentUserId
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
                    parseError(response.code(), response.errorBody()?.string()) // Use .code property
                }
            } catch (e: HttpException) {
                parseError(e.code(), e.response()?.errorBody()?.string()) // Use .code property
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
                    // ... (other event handling remains the same)
                    is WebSocketEvent.ConnectionEstablished -> {
                        Timber.i("WebSocket connected. Ready for real-time updates.")
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
                        val messageEntity = messagePayload.toEntity() // Removed unused currentUserId
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
                    }
                    ServerWebSocketReceiveType.ERROR -> {
                        val errorPayload = json.decodeFromJsonElement(ServerErrorPayloadDto.serializer(), payloadElement)
                        Timber.e("Received WebSocket ERROR: ${errorPayload.message} (Code: ${errorPayload.code}) for original type: ${errorPayload.originalMessageType}")
                    }
                    else -> Timber.w("Received unknown WebSocket message type: $type")
                }
            } catch (e: Exception) {
                Timber.e(e, "Error processing incoming WebSocket payload for type '$type': $payloadElement")
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

    private fun MessageDto.toEntity(): MessageEntity { // Removed unused currentUserId parameter
        return MessageEntity(
            id = this.id,
            chatId = this.chatId,
            senderId = this.senderId,
            receiverId = null,
            content = this.content,
            timestamp = this.timestamp.toEpochMillis() ?: System.currentTimeMillis(),
            status = this.status,
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