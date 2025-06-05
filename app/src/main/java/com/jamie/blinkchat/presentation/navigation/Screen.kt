package com.jamie.blinkchat.presentation.navigation

import java.net.URLEncoder
import java.nio.charset.StandardCharsets

sealed class Screen(val route: String) {
    object Splash : Screen("splash_screen")
    object Login : Screen("login_screen")
    object Register : Screen("register_screen")
    object ChatList : Screen("chat_list_screen")
    object SearchUsers : Screen("search_users_screen")

    data class Chat(
        val chatIdArgForPattern: String = "{${ARG_CHAT_ID}}",
        val otherUsernameArgForPattern: String = "{${ARG_OTHER_USERNAME}}"
    ) : Screen("chat_screen/$chatIdArgForPattern?${ARG_OTHER_USERNAME}=$otherUsernameArgForPattern") {
        fun createRoute(chatId: String, otherUsername: String): String {
            val encodedUsername = URLEncoder.encode(otherUsername, StandardCharsets.UTF_8.toString())
            return "chat_screen/$chatId?${ARG_OTHER_USERNAME}=$encodedUsername"
        }

        // Overload for starting a new chat where chatId might not exist yet, but receiverId and username do
        // This assumes receiverId will be implicitly handled by ChatViewModel when chatId is null
        // or that ChatViewModel will also accept receiverId as a nav arg if needed.
        // For now, let's assume ChatViewModel can infer new chat from null chatId and selected otherUsername.
        fun createNewChatRoute(otherUserId: String, otherUsername: String): String {
            // If your ChatScreen needs receiverId explicitly when chatId is null:
            // "chat_screen/new?receiverId=$otherUserId&otherUsername=${encodedUsername}"
            // For now, we'll rely on ChatViewModel to handle "new chat" when chatId is "new" or similar.
            // Or, we just pass the username and the ViewModel starts with null chatId.
            // Let's assume we'll pass "null" (as a string or special value) for chatId if not available,
            // or simply rely on the ChatViewModel logic.
            // For this step, we'll pass otherUsername. The ChatScreen will have a null chatId.
            val encodedUsername = URLEncoder.encode(otherUsername, StandardCharsets.UTF_8.toString())
            // We need a way to distinguish a new chat. We could use a special chatId value
            // or pass receiverId. Let's stick to the existing Chat route and let ChatViewModel handle it.
            // The chatId here will be a placeholder indicating a new chat for the ViewModel
            // Or the ViewModel gets receiverId and username and handles null chatId
            return "chat_screen/$NEW_CHAT_PLACEHOLDER_ID?${ARG_OTHER_USERNAME}=$encodedUsername&${ARG_RECEIVER_ID}=$otherUserId"
        }


        companion object {
            const val ARG_CHAT_ID = "chatId"
            const val ARG_OTHER_USERNAME = "otherUsername"
            const val ARG_RECEIVER_ID = "receiverId" // New argument for starting a new chat
            const val NEW_CHAT_PLACEHOLDER_ID = "new" // Special value for chatId when starting new chat


            fun VagueRoute(): String = "chat_screen/{$ARG_CHAT_ID}?$ARG_OTHER_USERNAME={$ARG_OTHER_USERNAME}&$ARG_RECEIVER_ID={$ARG_RECEIVER_ID}"
        }
    }
    object Settings : Screen("settings_screen")
}