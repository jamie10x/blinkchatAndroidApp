package com.jamie.blinkchat.data.repository

import com.jamie.blinkchat.core.common.Resource
import com.jamie.blinkchat.core.common.toEpochMillis
import com.jamie.blinkchat.data.local.dato.ChatSummaryDao
import com.jamie.blinkchat.data.local.dato.UserDao
import com.jamie.blinkchat.data.model.local.ChatSummaryEntity
import com.jamie.blinkchat.data.model.local.UserEntity
import com.jamie.blinkchat.data.model.remote.ChatDto
import com.jamie.blinkchat.data.model.remote.ApiErrorDto // For parsing error
import com.jamie.blinkchat.data.remote.ChatApiService
import com.jamie.blinkchat.domain.model.ChatSummaryItem
import com.jamie.blinkchat.repositories.AuthRepository
import com.jamie.blinkchat.repositories.ChatListRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import retrofit2.HttpException
import timber.log.Timber
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ChatListRepositoryImpl @Inject constructor(
    private val chatApiService: ChatApiService,
    private val chatSummaryDao: ChatSummaryDao,
    private val userDao: UserDao, // To save participant details
    private val authRepository: AuthRepository, // To get current user ID for "isLastMessageFromCurrentUser"
    private val json: Json // For parsing error bodies
) : ChatListRepository {

    override fun getChatSummaries(forceRefresh: Boolean): Flow<Resource<List<ChatSummaryItem>>> = flow {
        emit(Resource.Loading())

        // Get current user ID to determine if last message is from current user
        val currentUserId = authRepository.observeToken().firstOrNull()?.let { token ->
            // A bit simplified: ideally, get user ID from a User object fetched using token.
            // For now, assuming AuthRepository might provide a way to get current UserId if needed,
            // or we fetch it once. For this example, let's fetch /me if token exists to get ID.
            // This is a bit heavy for just getting an ID. A better way would be to store userId with token.
            if (token.isNotBlank()) {
                when (val userResource = authRepository.getCurrentUser()) { // This is a suspend call
                    is Resource.Success -> userResource.data?.id
                    else -> null
                }
            } else null
        }


        // Combine local data emission with network refresh logic
        val localDataFlow = chatSummaryDao.getAllChatSummaries()
            .map { entities ->
                Resource.Success(entities.map { it.toDomain(currentUserId) }) as Resource<List<ChatSummaryItem>>
                // Cast is safe here because we know it's success.
            }

        // Emit local data first
        localDataFlow.collect { emit(it) }


        // Conditionally refresh from network
        // For simplicity, always try to refresh on first call or if forced.
        // More sophisticated logic could check for staleness.
        if (forceRefresh || true /* or some staleness check */) {
            when (val refreshResult = refreshChatSummaries()) {
                is Resource.Error -> {
                    // If refresh fails, the flow will have already emitted local data (if any).
                    // We can emit the error from refresh operation if no local data was present
                    // or if we want to explicitly show a refresh error.
                    // For now, just log, as local data flow continues.
                    Timber.w("Failed to refresh chat summaries: ${refreshResult.message}")
                    // If the local data was empty and refresh failed, ensure an error is emitted
                    // This is tricky with combined flows. Let's refine the error emission.
                    // If local data was emitted and it was empty, and refresh failed, then emit the error.
                    // This logic can become complex. A simpler approach is to let the UI show a refresh error separately.
                }
                else -> { /* Success or Loading for refresh, local data flow will update */ }
            }
        }
    }.flowOn(Dispatchers.IO) // Ensure all operations in this flow run on IO dispatcher

    override suspend fun refreshChatSummaries(): Resource<Unit> {
        return withContext(Dispatchers.IO) {
            try {
                Timber.d("Refreshing chat summaries from network...")
                val response = chatApiService.getChats(limit = 50) // Fetch a good number
                if (response.isSuccessful && response.body() != null) {
                    val chatDtos = response.body()!!
                    Timber.d("Fetched ${chatDtos.size} chats from network.")

                    val usersToSave = mutableListOf<UserEntity>()
                    val chatSummaryEntities = chatDtos.mapNotNull { dto ->
                        // Assuming 1:1 chat, find the other participant
                        val otherParticipantDto = dto.otherParticipants.firstOrNull()
                        if (otherParticipantDto == null) {
                            Timber.w("ChatDTO ${dto.id} has no other participant, skipping.")
                            return@mapNotNull null // Skip if no other participant for a 1:1 chat list item
                        }
                        usersToSave.add(otherParticipantDto.toEntity())
                        dto.lastMessage?.sender?.let { usersToSave.add(it.toEntity()) }

                        dto.toEntity(otherParticipantDto.id, otherParticipantDto.username)
                    }

                    if (usersToSave.isNotEmpty()) {
                        userDao.insertUsers(usersToSave.distinctBy { it.id })
                    }
                    if (chatSummaryEntities.isNotEmpty()) {
                        // For a full sync, you might delete old chats not present in the new list.
                        // For simplicity, using REPLACE on conflict.
                        chatSummaryDao.upsertChatSummaries(chatSummaryEntities)
                    }
                    Timber.d("Chat summaries saved to DB.")
                    Resource.Success(Unit)
                } else {
                    val errorBody = response.errorBody()?.string()
                    Timber.e("API Error fetching chats: ${response.code()} - $errorBody")
                    parseError(response.code(), errorBody) // Returns Resource.Error<Unit>
                }
            } catch (e: HttpException) {
                Timber.e(e, "HttpException refreshing chats")
                parseError(e.code(), e.response()?.errorBody()?.string())
            } catch (e: IOException) {
                Timber.e(e, "IOException refreshing chats (network issue)")
                Resource.Error("Network error. Could not refresh chats.", data = Unit)
            } catch (e: Exception) {
                Timber.e(e, "Unexpected error refreshing chats")
                Resource.Error("An unexpected error occurred: ${e.localizedMessage}", data = Unit)
            }
        }
    }


    // --- Helper for parsing API errors (can be moved to a common utility if used elsewhere) ---
    private fun <T> parseError(errorCode: Int, errorBody: String?): Resource<T> {
        val defaultMessage = "API Error: $errorCode"
        return try {
            if (errorBody != null) {
                val apiError = json.decodeFromString<ApiErrorDto>(errorBody) // Assuming generic ApiErrorDto
                Resource.Error(apiError.error, data = null, errorCode = errorCode)
            } else {
                Resource.Error(defaultMessage, data = null, errorCode = errorCode)
            }
        } catch (e: Exception) {
            Timber.e(e, "Failed to parse error body for chat list: $errorBody")
            Resource.Error(defaultMessage, data = null, errorCode = errorCode)
        }
    }

    // --- Mapper DTO to Entity & Entity to Domain ---
    private fun ChatDto.toEntity(otherParticipantId: String, otherUsername: String): ChatSummaryEntity {
        return ChatSummaryEntity(
            id = this.id,
            otherParticipantId = otherParticipantId,
            otherParticipantUsername = otherUsername,
            lastMessageId = this.lastMessage?.id,
            lastMessageContent = this.lastMessage?.content,
            lastMessageTimestamp = this.lastMessage?.timestamp?.toEpochMillis(),
            lastMessageSenderId = this.lastMessage?.senderId,
            lastMessageStatus = this.lastMessage?.status,
            unreadCount = this.unreadCount,
            chatCreatedAt = this.createdAt.toEpochMillis() ?: System.currentTimeMillis() // Fallback for chat creation
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

    private fun ChatSummaryEntity.toDomain(currentUserId: String?): ChatSummaryItem {
        return ChatSummaryItem(
            chatId = this.id,
            otherParticipantUsername = this.otherParticipantUsername,
            lastMessagePreview = this.lastMessageContent,
            lastMessageTimestamp = this.lastMessageTimestamp,
            lastMessageStatus = this.lastMessageStatus,
            isLastMessageFromCurrentUser = this.lastMessageSenderId == currentUserId,
            unreadCount = this.unreadCount
        )
    }
}