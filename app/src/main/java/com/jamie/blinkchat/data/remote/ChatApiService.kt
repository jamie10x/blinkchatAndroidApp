package com.jamie.blinkchat.data.remote

import com.jamie.blinkchat.data.model.remote.ChatDto
import com.jamie.blinkchat.data.model.remote.CreateMessageRequestDto
import com.jamie.blinkchat.data.model.remote.MessageDto
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Query

interface ChatApiService {

    @GET("chats")
    suspend fun getChats(
        @Query("limit") limit: Int? = null,
        @Query("offset") offset: Int? = null
    ): Response<List<ChatDto>>

    /**
     * Retrieves a paginated list of messages for a specific chat.
     * Endpoint: GET /api/v1/messages
     */
    @GET("messages")
    suspend fun getMessagesForChat(
        @Query("chatId") chatId: String,
        @Query("limit") limit: Int? = null, // e.g., 20
        @Query("offset") offset: Int? = null  // e.g., 0 for the first page
    ): Response<List<MessageDto>>

    /**
     * Sends a new message via REST.
     * Endpoint: POST /api/v1/messages
     */
    @POST("messages")
    suspend fun sendMessage(
        @Body request: CreateMessageRequestDto
    ): Response<MessageDto> // API returns the created MessageDto
}