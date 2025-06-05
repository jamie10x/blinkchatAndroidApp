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

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessage(message: MessageEntity)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertMessages(messages: List<MessageEntity>)

    @Query("SELECT * FROM messages WHERE chatId = :chatId ORDER BY timestamp ASC")
    fun getMessagesForChatFlow(chatId: String): Flow<List<MessageEntity>>

    @Query("SELECT * FROM messages WHERE chatId = :chatId AND timestamp < :beforeTimestamp ORDER BY timestamp DESC LIMIT :limit")
    suspend fun getMessagesForChatPaginated(chatId: String, beforeTimestamp: Long, limit: Int): List<MessageEntity>

    @Query("SELECT * FROM messages WHERE id = :messageId")
    suspend fun getMessageById(messageId: String): MessageEntity?

    @Query("SELECT * FROM messages WHERE clientTempId = :clientTempId")
    suspend fun getMessageByClientTempId(clientTempId: String): MessageEntity?

    @Update
    suspend fun updateMessage(message: MessageEntity)

    @Query("UPDATE messages SET status = :status WHERE id = :messageId")
    suspend fun updateMessageStatus(messageId: String, status: String)

    @Query("UPDATE messages SET status = :newStatus WHERE chatId = :chatId AND status = :oldStatus AND senderId != :currentUserId")
    suspend fun updateStatusesForChat(chatId: String, oldStatus: String, newStatus: String, currentUserId: String)

    @Query("UPDATE messages SET id = :serverId, status = :status, timestamp = :serverTimestamp, isOptimistic = 0, clientTempId = NULL WHERE clientTempId = :clientTempId") // Nullify clientTempId after successful sync
    suspend fun confirmSentMessage(clientTempId: String, serverId: String, status: String, serverTimestamp: Long)

    @Query("DELETE FROM messages WHERE id = :messageId")
    suspend fun deleteMessageById(messageId: String)

    @Query("DELETE FROM messages WHERE chatId = :chatId")
    suspend fun deleteMessagesForChat(chatId: String)

    @Query("SELECT * FROM messages WHERE isOptimistic = 1 AND (status = :statusSending OR status = :statusFailed)")
    suspend fun getPendingMessages(statusSending: String = MessageEntity.STATUS_SENDING, statusFailed: String = MessageEntity.STATUS_FAILED): List<MessageEntity>
}