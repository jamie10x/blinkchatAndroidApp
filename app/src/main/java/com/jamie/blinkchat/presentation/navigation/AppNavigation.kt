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
                onNavigateToChat = { chatId ->
                    // We will modify this soon to pass username too
                    navController.navigate(Screen.Chat("").createRoute(chatId))
                },
                onNavigateToLogin = {
                    navController.navigate(Screen.Login.route) {
                        popUpTo(Screen.ChatList.route) { inclusive = true }
                        popUpTo(Screen.Splash.route) { inclusive = false }
                    }
                }
            )
        }

        // Updated ChatScreen composable call
        composable(
            route = Screen.Chat("").route, // Base route definition still needs the placeholder
            arguments = listOf(
                navArgument(Screen.Chat.ARG_CHAT_ID) {
                    type = NavType.StringType
                    // nullable = true // If chatId could ever be null, but usually it's required for this screen
                }
                // We will add ARG_OTHER_USERNAME here soon
            )
        ) { backStackEntry ->
            // The ChatScreen Composable itself will use hiltViewModel()
            // which will use SavedStateHandle to get the chatId.
            // We no longer need to pass chatId explicitly here.
            ChatScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }

        // composable(Screen.Settings.route) { /* SettingsScreen(...) */ }
    }
}