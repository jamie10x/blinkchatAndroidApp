package com.jamie.blinkchat.presentation.navigation

/**
 * Sealed class representing the different screens in the application
 * and their corresponding routes for Jetpack Navigation Compose.
 */
sealed class Screen(val route: String) {
    object Splash : Screen("splash_screen")
    object Login : Screen("login_screen")
    object Register : Screen("register_screen")
    object ChatList : Screen("chat_list_screen")
    data class Chat(val chatId: String) : Screen("chat_screen/{$ARG_CHAT_ID}") {
        fun createRoute(chatId: String) =
            "chat_screen/$chatId"

        companion object {
            const val ARG_CHAT_ID = "chatId"
        }
    }

    object Settings : Screen("settings_screen")

    // Add other screens here as needed
}