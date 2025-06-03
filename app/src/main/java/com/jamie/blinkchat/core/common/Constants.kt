package com.jamie.blinkchat.core.common

object Constants {
    // Updated with the provided Base URL
    const val BASE_URL = "https://chat.boynazar.tech/api/v1/" // Using v1 as per docs

    const val PREFERENCES_NAME = "blink_chat_prefs"
    const val PREF_AUTH_TOKEN = "auth_token"

    // WebSocket URL derived from the base domain
    // Assuming it's at the root of the domain, not under /api/v1/
    const val WEBSOCKET_BASE_URL = "wss://chat.boynazaar.tech/ws" // Use wss for secure
}