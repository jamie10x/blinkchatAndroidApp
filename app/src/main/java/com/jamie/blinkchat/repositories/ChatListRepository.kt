package com.jamie.blinkchat.repositories

import com.jamie.blinkchat.core.common.Resource
import com.jamie.blinkchat.domain.model.ChatSummaryItem
import kotlinx.coroutines.flow.Flow

interface ChatListRepository {

    /**
     * Gets a flow of chat summaries.
     * This flow will emit data from the local database and automatically update
     * when new data is fetched from the network and saved.
     *
     * @param forceRefresh If true, will attempt to fetch fresh data from the network
     *                     even if local data exists.
     * @return A Flow emitting a Resource which wraps a list of [ChatSummaryItem].
     *         The Resource can indicate Loading, Success (with data), or Error.
     */
    fun getChatSummaries(forceRefresh: Boolean = false): Flow<Resource<List<ChatSummaryItem>>>

    /**
     * Fetches fresh chat summaries from the network and updates the local database.
     * Typically called internally by getChatSummaries or for manual refresh actions.
     * @return A Resource indicating the outcome of the network operation.
     */
    suspend fun refreshChatSummaries(): Resource<Unit> // Unit for success if data is just saved
}