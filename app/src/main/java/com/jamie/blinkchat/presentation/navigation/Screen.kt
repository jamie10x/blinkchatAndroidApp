package com.jamie.blinkchat.presentation.navigation

import java.net.URLEncoder
import java.nio.charset.StandardCharsets

sealed class Screen(val route: String) {
    object Splash : Screen("splash_screen")
    object Login : Screen("login_screen")
    object Register : Screen("register_screen")
    object ChatList : Screen("chat_list_screen")

    data class Chat(
        // These properties are more for conceptual grouping if needed,
        val chatIdArgForPattern: String = "{${ARG_CHAT_ID}}",
        val otherUsernameArgForPattern: String = "{${ARG_OTHER_USERNAME}}"
    ) : Screen("chat_screen/$chatIdArgForPattern?${ARG_OTHER_USERNAME}=$otherUsernameArgForPattern") {
        // Function to create the actual navigation route with dynamic values
        fun createRoute(chatId: String, otherUsername: String): String {
            val encodedUsername = URLEncoder.encode(otherUsername, StandardCharsets.UTF_8.toString())
            return "chat_screen/$chatId?${ARG_OTHER_USERNAME}=$encodedUsername"
        }

        companion object {
            const val ARG_CHAT_ID = "chatId"
            const val ARG_OTHER_USERNAME = "otherUsername"

            // This is the pattern used in NavHost for defining the composable
            // It includes placeholders for all arguments.
            fun VagueRoute(): String = "chat_screen/{$ARG_CHAT_ID}?$ARG_OTHER_USERNAME={$ARG_OTHER_USERNAME}"

        }
    }
    object Settings : Screen("settings_screen")

    // Add other screens here as needed
}