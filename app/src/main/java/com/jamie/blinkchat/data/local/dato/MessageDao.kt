package com.jamie.blinkchat.data.local.dato

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.jamie.blinkchat.data.model.local.MessageEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface MessageDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE) // Replace if optimistic message has same clientTempId (which becomes ID)
    suspend fun insertMessage(message: MessageEntity)

    @Insert(onConflict = OnConflictStrategy.IGNORE) // Ignore if message from server already exists (e.g. from REST sync)
    suspend fun insertMessages(messages: List<MessageEntity>)

    // Get messages for a specific chat, ordered by timestamp (oldest first for chat display)
    // Implement pagination here (e.g., using LIMIT and OFFSET)
    @Query("SELECT * FROM messages WHERE chatId = :chatId ORDER BY timestamp ASC")
    fun getMessagesForChatFlow(chatId: String): Flow<List<MessageEntity>>

    // Example for paginated fetch (older messages)
    @Query("SELECT * FROM messages WHERE chatId = :chatId AND timestamp < :beforeTimestamp ORDER BY timestamp DESC LIMIT :limit")
    suspend fun getMessagesForChatPaginated(chatId: String, beforeTimestamp: Long, limit: Int): List<MessageEntity>


    @Query("SELECT * FROM messages WHERE id = :messageId")
    suspend fun getMessageById(messageId: String): MessageEntity?

    @Query("SELECT * FROM messages WHERE clientTempId = :clientTempId")
    suspend fun getMessageByClientTempId(clientTempId: String): MessageEntity?

    // Update a message, typically after receiving an ACK from the server
    // This replaces the message with clientTempId as PK with the server-confirmed one
    @Update
    suspend fun updateMessage(message: MessageEntity)

    // More specific updates
    @Query("UPDATE messages SET status = :status WHERE id = :messageId")
    suspend fun updateMessageStatus(messageId: String, status: String)

    @Query("UPDATE messages SET status = :newStatus WHERE chatId = :chatId AND status = :oldStatus AND senderId != :currentUserId")
    suspend fun updateStatusesForChat(chatId: String, oldStatus: String, newStatus: String, currentUserId: String)


    // When a message ACK is received: delete the temp message and insert the confirmed one
    // Or, update the existing message by its clientTempId
    @Query("UPDATE messages SET id = :serverId, status = :status, timestamp = :serverTimestamp, isOptimistic = 0 WHERE clientTempId = :clientTempId")
    suspend fun confirmSentMessage(clientTempId: String, serverId: String, status: String, serverTimestamp: Long)


    @Query("DELETE FROM messages WHERE id = :messageId")
    suspend fun deleteMessageById(messageId: String)

    @Query("DELETE FROM messages WHERE chatId = :chatId")
    suspend fun deleteMessagesForChat(chatId: String)
}