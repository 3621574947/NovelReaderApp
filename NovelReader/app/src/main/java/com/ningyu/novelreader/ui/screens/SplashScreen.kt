package com.ningyu.novelreader.ui.screens


import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.sp
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.delay

@Composable
fun SplashScreen(
    onFinish: (String) -> Unit
) {
    val auth = FirebaseAuth.getInstance()

    LaunchedEffect(Unit) {
        delay(1500)

        val startRoute = if (auth.currentUser != null) "booklist" else "login"

        onFinish(startRoute)
    }

    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "Novel Reader",
            style = MaterialTheme.typography.headlineLarge.copy(fontSize = 48.sp),
            color = MaterialTheme.colorScheme.primary
        )
        Text(
            text = "加载您的阅读之旅...",
            style = MaterialTheme.typography.bodyMedium
        )
    }
}