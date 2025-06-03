package com.jamie.blinkchat.presentation.ui.features.splash

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.hilt.navigation.compose.hiltViewModel
import com.jamie.blinkchat.ui.theme.BlinkChatTheme
import kotlinx.coroutines.flow.collectLatest

@Composable
fun SplashScreen(
    viewModel: SplashViewModel = hiltViewModel(),
    onNavigateToLogin: () -> Unit,
    onNavigateToChatList: () -> Unit
) {
    // val state by viewModel.uiState.collectAsState() // State is simple, mostly for loading visual

    LaunchedEffect(key1 = Unit) {
        viewModel.effect.collectLatest { effect ->
            when (effect) {
                SplashContract.Effect.NavigateToLogin -> onNavigateToLogin()
                SplashContract.Effect.NavigateToChatList -> onNavigateToChatList()
            }
        }
    }

    // Simple UI for splash screen
    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator()
            // You could add your app logo here:
            // Image(painter = painterResource(id = R.drawable.your_logo), contentDescription = "App Logo")
            // Text("BlinkChat", style = MaterialTheme.typography.headlineLarge)
        }
    }
}

@Preview(showBackground = true)
@Composable
fun SplashScreenPreview() {
    BlinkChatTheme {
        // Preview will just show the CircularProgressIndicator
        // as effect collection won't run to navigate.
        SplashScreen(onNavigateToLogin = {}, onNavigateToChatList = {})
    }
}