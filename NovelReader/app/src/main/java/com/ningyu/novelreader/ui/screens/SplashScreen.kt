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
    // onFinish 是一个回调函数，用于告诉 MainActivity 应该导航到哪个路由（login 或 booklist）
    onFinish: (String) -> Unit
) {
    val auth = FirebaseAuth.getInstance()

    // LaunchedEffect 用于控制启动页的流程
    LaunchedEffect(Unit) {
        // 1. 等待 1.5 秒（你可以根据需要调整时间）
        delay(1500)

        // 2. 根据用户登录状态确定最终路由
        val startRoute = if (auth.currentUser != null) "booklist" else "login"

        // 3. 执行导航回调
        onFinish(startRoute)
    }

    // 启动页的 UI 界面
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