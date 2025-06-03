package com.jamie.blinkchat.domain.usecase.chat_list

import com.jamie.blinkchat.core.common.Resource
import com.jamie.blinkchat.domain.model.ChatSummaryItem
import com.jamie.blinkchat.repositories.ChatListRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class GetChatListUseCase @Inject constructor(
    private val chatListRepository: ChatListRepository
) {
    /**
     * Invokes the use case to get a flow of chat summaries.
     *
     * @param forceRefresh If true, attempts to fetch fresh data from the network.
     *                     Defaults to false. The repository handles initial fetch logic.
     * @return A Flow emitting a Resource wrapping a list of [ChatSummaryItem].
     */
    operator fun invoke(forceRefresh: Boolean = false): Flow<Resource<List<ChatSummaryItem>>> {
        return chatListRepository.getChatSummaries(forceRefresh = forceRefresh)
    }
}