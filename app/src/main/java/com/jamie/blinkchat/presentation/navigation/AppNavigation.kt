package com.jamie.blinkchat.presentation.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.jamie.blinkchat.presentation.ui.features.auth.LoginScreen
import com.jamie.blinkchat.presentation.ui.features.auth.RegisterScreen
import com.jamie.blinkchat.presentation.ui.features.chat.ChatScreen
import com.jamie.blinkchat.presentation.ui.features.chat_list.ChatListScreen
import com.jamie.blinkchat.presentation.ui.features.search.SearchUsersScreen // Import new screen
import com.jamie.blinkchat.presentation.ui.features.splash.SplashScreen

@Composable
fun AppNavGraph(
    navController: NavHostController,
) {
    NavHost(
        navController = navController,
        startDestination = Screen.Splash.route
    ) {
        composable(Screen.Splash.route) {
            SplashScreen(
                onNavigateToLogin = {
                    navController.navigate(Screen.Login.route) {
                        popUpTo(Screen.Splash.route) { inclusive = true }
                    }
                },
                onNavigateToChatList = {
                    navController.navigate(Screen.ChatList.route) {
                        popUpTo(Screen.Splash.route) { inclusive = true }
                    }
                }
            )
        }

        composable(Screen.Login.route) {
            LoginScreen(
                onNavigateToChatList = {
                    navController.navigate(Screen.ChatList.route) {
                        popUpTo(Screen.Login.route) { inclusive = true }
                    }
                },
                onNavigateToRegister = {
                    navController.navigate(Screen.Register.route)
                }
            )
        }

        composable(Screen.Register.route) {
            RegisterScreen(
                onNavigateToLogin = {
                    navController.navigate(Screen.Login.route) {
                        popUpTo(Screen.Register.route) { inclusive = true }
                    }
                }
            )
        }

        composable(Screen.ChatList.route) {
            ChatListScreen(
                onNavigateToChat = { chatId, otherUsername ->
                    val route = Screen.Chat(chatId, otherUsername).createRoute(chatId, otherUsername)
                    navController.navigate(route)
                },
                onNavigateToLogin = {
                    navController.navigate(Screen.Login.route) {
                        popUpTo(Screen.ChatList.route) { inclusive = true }
                        popUpTo(Screen.Splash.route) { inclusive = false }
                    }
                }
            )
            {
                navController.navigate(Screen.SearchUsers.route)
            }
        }

        // New Composable for SearchUsersScreen
        composable(Screen.SearchUsers.route) {
            SearchUsersScreen(
                onNavigateBack = {
                    navController.popBackStack()
                },
                onNavigateToChat = { receiverId, username ->
                    // Use the createNewChatRoute for initiating a chat
                    val route = Screen.Chat("", "").createNewChatRoute(receiverId, username)
                    navController.navigate(route) {
                        // Optionally pop SearchUsersScreen off the back stack
                        // popUpTo(Screen.SearchUsers.route) { inclusive = true }
                    }
                }
            )
        }

        composable(
            route = Screen.Chat.VagueRoute(),
            arguments = listOf(
                navArgument(Screen.Chat.ARG_CHAT_ID) { type = NavType.StringType },
                navArgument(Screen.Chat.ARG_OTHER_USERNAME) { type = NavType.StringType },
                // Add receiverId as an optional argument for new chats
                navArgument(Screen.Chat.ARG_RECEIVER_ID) {
                    type = NavType.StringType
                    nullable = true // Can be null if opening an existing chat
                    defaultValue = null
                }
            )
        ) { // backStackEntry is not directly used if ViewModel handles args
            ChatScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }
    }
}