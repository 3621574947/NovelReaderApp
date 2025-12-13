package com.ningyu.novelreader.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.delay

/**
 * Splash screen shown when the app starts.
 * Checks authentication state and navigates to either BookList or Login after a short delay.
 */
@Composable
fun SplashScreen(
    onNavigationReady: (destination: String) -> Unit
) {
    val auth = FirebaseAuth.getInstance()

    // Automatically navigate after 1.5 seconds
    LaunchedEffect(Unit) {
        delay(1500L)
        val route = if (auth.currentUser != null) "booklist" else "login"
        onNavigationReady(route)
    }

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = "Novel Reader",
                style = MaterialTheme.typography.displayMedium.copy(fontSize = 48.sp),
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(Modifier.height(16.dp))
            Text(
                text = "Loading your reading journey...",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
                textAlign = TextAlign.Center
            )
        }
    }
}