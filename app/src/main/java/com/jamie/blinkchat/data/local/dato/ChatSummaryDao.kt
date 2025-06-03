package com.jamie.blinkchat.data.local.dato

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.jamie.blinkchat.data.model.local.ChatSummaryEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ChatSummaryDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertChatSummary(chatSummary: ChatSummaryEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertChatSummaries(chatSummaries: List<ChatSummaryEntity>)

    // Observe all chat summaries, ordered by last message timestamp (newest first)
    // then by chat creation time as a secondary sort.
    @Query("SELECT * FROM chat_summaries ORDER BY lastMessageTimestamp DESC, chatCreatedAt DESC")
    fun getAllChatSummaries(): Flow<List<ChatSummaryEntity>>

    @Query("SELECT * FROM chat_summaries WHERE id = :chatId")
    suspend fun getChatSummaryById(chatId: String): ChatSummaryEntity?

    @Query("DELETE FROM chat_summaries")
    suspend fun deleteAllChatSummaries()

    // Example of a more complex update (e.g., when a new last message arrives)
    @Query("UPDATE chat_summaries SET " +
            "lastMessageId = :lastMessageId, " +
            "lastMessageContent = :content, " +
            "lastMessageTimestamp = :timestamp, " +
            "lastMessageSenderId = :senderId, " +
            "lastMessageStatus = :status, " +
            "unreadCount = unreadCount + CASE WHEN :incrementUnreadCount THEN 1 ELSE 0 END " + // Increment conditionally
            "WHERE id = :chatId")
    suspend fun updateLastMessage(
        chatId: String,
        lastMessageId: String,
        content: String,
        timestamp: Long,
        senderId: String,
        status: String,
        incrementUnreadCount: Boolean // To decide if unreadCount should be incremented
    )

    @Query("UPDATE chat_summaries SET unreadCount = 0 WHERE id = :chatId")
    suspend fun resetUnreadCount(chatId: String)

    // Transaction example for upserting a list of chats, ensuring atomicity
    @Transaction
    suspend fun upsertChatSummaries(chatSummaries: List<ChatSummaryEntity>) {
        // Potentially delete old ones not in the new list if full sync,
        // or just rely on REPLACE for simplicity.
        // For now, just insert/replace.
        insertChatSummaries(chatSummaries)
    }
}