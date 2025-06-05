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
import com.jamie.blinkchat.presentation.ui.features.splash.SplashScreen
// Removed unused URLDecoder and StandardCharsets as decoding happens in ViewModel

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
        }

        composable(
            route = Screen.Chat.VagueRoute(), // Use the VagueRoute to define argument placeholders
            arguments = listOf(
                navArgument(Screen.Chat.ARG_CHAT_ID) { type = NavType.StringType },
                navArgument(Screen.Chat.ARG_OTHER_USERNAME) {
                    type = NavType.StringType
                    nullable = false // Assuming username is always passed for now
                }
            )
        ) { backStackEntry -> // backStackEntry is not directly used if ViewModel handles args
            ChatScreen(
                // ViewModel gets arguments from SavedStateHandle
                onNavigateBack = { navController.popBackStack() }
            )
        }

        // composable(Screen.Settings.route) { /* SettingsScreen(...) */ }
    }
}