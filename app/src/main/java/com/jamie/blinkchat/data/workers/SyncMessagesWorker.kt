package com.jamie.blinkchat.data.workers

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.jamie.blinkchat.data.repository.MessageRepositoryImpl
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber

@HiltWorker
class SyncMessagesWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val messageRepository: MessageRepositoryImpl
) : CoroutineWorker(appContext, workerParams) {

    companion object {
        const val WORK_NAME = "SyncMessagesWorker"
    }

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        Timber.d("SyncMessagesWorker: Starting to sync pending messages.")
        try {
            val pendingMessages = messageRepository.getPendingMessagesForSync()

            if (pendingMessages.isEmpty()) {
                Timber.d("SyncMessagesWorker: No pending messages to sync.")
                return@withContext Result.success()
            }

            Timber.d("SyncMessagesWorker: Found ${pendingMessages.size} pending messages.")
            var allInitiatedSuccessfully = true

            for (message in pendingMessages) {
                Timber.d("SyncMessagesWorker: Attempting to resend message with clientTempId: ${message.clientTempId ?: message.id}")
                val initiated = messageRepository.retrySendingMessage(message)
                if (!initiated) {
                    Timber.w("SyncMessagesWorker: Failed to initiate send for message ${message.clientTempId ?: message.id}. Will retry worker.")
                    allInitiatedSuccessfully = false
                    // If one fails to even start sending (e.g., still no network),
                    // we'll retry the whole worker later.
                    // More granular per-message retry logic could be added here if needed.
                    // break // Optionally break if one fails, to retry all together later
                } else {
                    Timber.d("SyncMessagesWorker: Send initiated for message ${message.clientTempId ?: message.id}.")
                }
            }

            if (allInitiatedSuccessfully) {
                Timber.d("SyncMessagesWorker: All pending message send attempts initiated successfully.")
                Result.success()
            } else {
                // If any message failed to even *initiate* a send (e.g., an immediate IOException again),
                // then retry the worker. The message is still in a pending state in DB.
                Timber.d("SyncMessagesWorker: Some message send attempts failed to initiate. Scheduling retry for the worker.")
                Result.retry()
            }

        } catch (e: Exception) {
            Timber.e(e, "SyncMessagesWorker: Error during sync process.")
            Result.retry()
        }
    }
}