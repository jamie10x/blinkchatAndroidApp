package com.jamie.blinkchat.data.model.remote.websockets

import com.jamie.blinkchat.core.common.Constants
import com.jamie.blinkchat.repositories.TokenStorageService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response // okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString
import timber.log.Timber
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton

// --- Event and State Definitions ---
sealed class WebSocketEvent {
    data class MessageReceived(val type: String, val payload: JsonElement) : WebSocketEvent()
    object ConnectionEstablished : WebSocketEvent() // Simplified: no WebSocket instance needed for observers
    data class ConnectionFailed(val error: Throwable?, val responseCode: Int?, val responseMessage: String?) : WebSocketEvent()
    data class ConnectionClosed(val code: Int, val reason: String?) : WebSocketEvent()
    object ConnectionClosing : WebSocketEvent() // Potentially useful if you want to differentiate
}

sealed class WebSocketConnectionState {
    object Idle : WebSocketConnectionState()
    object Connecting : WebSocketConnectionState()
    object Connected : WebSocketConnectionState()
    data class Disconnected(val code: Int? = null, val reason: String? = null, val error: Throwable? = null) : WebSocketConnectionState()
}


@Singleton
class WebSocketManager @Inject constructor(
    private val okHttpClient: OkHttpClient,
    private val tokenStorageService: TokenStorageService,
    private val json: Json
) {
    private var webSocket: WebSocket? = null
    private val coroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private val _connectionState = MutableStateFlow<WebSocketConnectionState>(WebSocketConnectionState.Idle)
    val connectionState: StateFlow<WebSocketConnectionState> = _connectionState.asStateFlow()

    // Corrected: Flow emits the base WebSocketEvent
    private val _events = MutableSharedFlow<WebSocketEvent>(replay = 0, extraBufferCapacity = 64) // Increased buffer
    val events: Flow<WebSocketEvent> = _events.asSharedFlow()

    private var currentToken: String? = null
    private var shouldAttemptReconnect = AtomicBoolean(false)
    private var retryAttempt = 0
    private val maxRetryAttempts = 5
    private val baseReconnectDelayMs = 2000L

    init {
        coroutineScope.launch {
            tokenStorageService.getAuthToken().collect { token ->
                val oldToken = currentToken
                currentToken = token
                if (token != oldToken && token != null && _connectionState.value is WebSocketConnectionState.Disconnected && shouldAttemptReconnect.get()) {
                    Timber.d("Token changed or became available while disconnected, attempting to reconnect WebSocket.")
                    connect() // connect is suspend, call it from coroutine
                } else if (token == null && oldToken != null) {
                    Timber.d("Token cleared, disconnecting WebSocket if connected.")
                    disconnect(wasIntentional = false) // Token removed, so treat as non-intentional for reconnect logic
                }
            }
        }
    }

    suspend fun connect() { // Make connect suspend for clarity with async token fetch
        if (_connectionState.value == WebSocketConnectionState.Connected || _connectionState.value == WebSocketConnectionState.Connecting) {
            Timber.d("WebSocket connection attempt ignored, already connected or connecting.")
            return
        }

        _connectionState.value = WebSocketConnectionState.Connecting
        shouldAttemptReconnect.set(true)

        // Fetch token within the suspend function
        val token = currentToken ?: tokenStorageService.getAuthToken().firstOrNull()

        if (token.isNullOrBlank()) {
            Timber.w("WebSocket connection attempt failed: No authentication token available.")
            _connectionState.value = WebSocketConnectionState.Disconnected(reason = "Auth token missing")
            _events.tryEmit(WebSocketEvent.ConnectionFailed(null, null, "Auth token missing"))
            return
        }

        val requestUrl = "${Constants.WEBSOCKET_BASE_URL}?token=$token"
        Timber.d("Attempting to connect WebSocket to: $requestUrl")
        val request = Request.Builder().url(requestUrl).build()

        // OkHttp's newWebSocket is synchronous in its initiation of the handshake.
        // The listener callbacks are asynchronous.
        withContext(Dispatchers.IO) {
            okHttpClient.newWebSocket(request, BlinkChatWebSocketListener())
        }
    }

    /**
     * Sends a pre-serialized WebSocketWrapperDto.
     * This is useful if the calling site has already constructed the full wrapper.
     */
    fun sendWrappedMessage(wrapperDto: WebSocketWrapperDto) {
        if (_connectionState.value != WebSocketConnectionState.Connected || webSocket == null) {
            Timber.w("Cannot send WebSocket message: Not connected.")
            return
        }
        try {
            val messageString = json.encodeToString(wrapperDto)
            Timber.d("Sending Wrapped WebSocket message: $messageString")
            webSocket?.send(messageString)
        } catch (e: Exception) {
            Timber.e(e, "Error serializing or sending Wrapped WebSocket message.")
        }
    }


    /**
     * Type-safe way to send a message.
     * The payload must be @Serializable.
     */
    fun <T : Any> sendTypedMessage(type: String, payload: T, payloadSerializer: KSerializer<T>) {
        if (_connectionState.value != WebSocketConnectionState.Connected || webSocket == null) {
            Timber.w("Cannot send WebSocket message: Not connected.")
            // Optionally queue message or notify error
            return
        }
        try {
            val payloadJsonElement = json.encodeToJsonElement(payloadSerializer, payload)
            val wrapper = WebSocketWrapperDto(type = type, payload = payloadJsonElement)
            val messageString = json.encodeToString(wrapper)
            Timber.d("Sending Typed WebSocket message: $messageString")
            webSocket?.send(messageString)
        } catch (e: Exception) {
            Timber.e(e, "Error serializing or sending Typed WebSocket message: $payload")
        }
    }


    fun disconnect(wasIntentional: Boolean = true) {
        Timber.d("WebSocket disconnect requested. Intentional: $wasIntentional")
        shouldAttemptReconnect.set(!wasIntentional) // Only attempt reconnect if not intentional
        webSocket?.close(1000, if (wasIntentional) "Client requested disconnect" else "Connection error")
        webSocket = null // Listener's onClosed or onFailure will also clear this
    }


    private inner class BlinkChatWebSocketListener : WebSocketListener() {
        override fun onOpen(ws: WebSocket, response: Response) {
            super.onOpen(ws, response)
            webSocket = ws
            _connectionState.value = WebSocketConnectionState.Connected
            retryAttempt = 0
            Timber.i("WebSocket Connection Opened: ${response.message}")
            _events.tryEmit(WebSocketEvent.ConnectionEstablished) // Use tryEmit for SharedFlow from non-suspending context
        }

        override fun onMessage(ws: WebSocket, text: String) {
            super.onMessage(ws, text)
            Timber.d("WebSocket Message Received (Text): $text")
            try {
                val wrapper = json.decodeFromString<WebSocketWrapperDto>(text)
                _events.tryEmit(WebSocketEvent.MessageReceived(wrapper.type, wrapper.payload))
            } catch (e: Exception) {
                Timber.e(e, "Error parsing WebSocket message: $text")
            }
        }

        override fun onMessage(ws: WebSocket, bytes: ByteString) {
            super.onMessage(ws, bytes)
            Timber.d("WebSocket Message Received (Bytes): ${bytes.hex()}")
        }

        override fun onClosing(ws: WebSocket, code: Int, reason: String) {
            super.onClosing(ws, code, reason)
            Timber.i("WebSocket Connection Closing: Code=$code, Reason=$reason")
            // _connectionState.value will be set in onClosed or onFailure
            _events.tryEmit(WebSocketEvent.ConnectionClosing)
            // ws.close(1000, null) // No need to close again, it's already closing
        }

        override fun onClosed(ws: WebSocket, code: Int, reason: String) {
            super.onClosed(ws, code, reason)
            webSocket = null
            val finalReason = if (reason.isBlank() && code != 1000) "Closed without explicit reason" else reason
            _connectionState.value = WebSocketConnectionState.Disconnected(code, finalReason)
            Timber.i("WebSocket Connection Closed: Code=$code, Reason=$finalReason")
            _events.tryEmit(WebSocketEvent.ConnectionClosed(code, finalReason))
            handleReconnection(code)
        }

        override fun onFailure(ws: WebSocket, t: Throwable, response: Response?) {
            super.onFailure(ws, t, response)
            webSocket = null
            // Use properties for code and message from OkHttp Response
            val respCode = response?.code
            val respMessage = response?.message ?: t.localizedMessage
            _connectionState.value = WebSocketConnectionState.Disconnected(respCode, respMessage, t)
            Timber.e(t, "WebSocket Connection Failure: Code=$respCode, Message=$respMessage")
            _events.tryEmit(WebSocketEvent.ConnectionFailed(t, respCode, respMessage))
            handleReconnection(respCode)
        }
    }

    private fun handleReconnection(code: Int?) {
        if (shouldAttemptReconnect.get() && code != 1000 /* Normal Closure */ && code != 1001 /* Going Away */) {
            if (retryAttempt < maxRetryAttempts) {
                retryAttempt++
                val delayTime = baseReconnectDelayMs * (1L shl (retryAttempt - 1)) // Exponential backoff: 2s, 4s, 8s...
                Timber.d("WebSocket: Reconnection attempt $retryAttempt in ${delayTime / 1000}s. Current state: ${_connectionState.value}")
                coroutineScope.launch {
                    delay(delayTime)
                    if (shouldAttemptReconnect.get() && _connectionState.value !is WebSocketConnectionState.Connected) { // Ensure still should reconnect and not already connected
                        Timber.d("WebSocket: Executing reconnection attempt $retryAttempt")
                        connect() // connect is suspend
                    } else {
                        Timber.d("WebSocket: Reconnection attempt $retryAttempt aborted (shouldAttemptReconnect is false or already connected).")
                    }
                }
            } else {
                Timber.w("WebSocket: Max reconnection attempts reached ($maxRetryAttempts). Giving up automatic reconnections.")
                shouldAttemptReconnect.set(false) // Stop trying
            }
        } else {
            Timber.d("WebSocket: Reconnection not attempted. Intentional: ${!shouldAttemptReconnect.get()}, Code: $code")
            if (code == 1000 || code == 1001) { // If normal closure or going away, ensure reconnections stop
                shouldAttemptReconnect.set(false)
            }
            retryAttempt = 0 // Reset for next manual connect() call
        }
    }
}