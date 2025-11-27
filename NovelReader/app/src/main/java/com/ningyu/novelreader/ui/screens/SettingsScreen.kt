package com.ningyu.novelreader.ui.screens

import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.edit // 引入 SharedPreferences 扩展
import androidx.core.net.toUri // 引入 File.toUri() 扩展
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File
import java.io.InputStream
import java.io.IOException

// ----------------------------------------------------
// ⚠️ 新增：私有工具函数 (用于安全地将图片复制到 App 内部存储)
// ----------------------------------------------------
private fun saveImageToInternalStorage(context: Context, uri: Uri): Uri? {
    return try {
        // 1. 获取输入流
        val inputStream = context.contentResolver.openInputStream(uri)
        // 2. 定义内部存储路径
        val file = File(context.filesDir, "avatar_image.jpg")

        // 3. 将输入流复制到 App 的私有文件
        file.outputStream().use { output ->
            inputStream?.copyTo(output)
        }

        // 4. 将内部文件路径保存到 SharedPreferences
        context.getSharedPreferences("user_settings", 0).edit {
            putString("avatar_path", file.absolutePath)
        }

        // 返回新的内部文件 URI
        return file.toUri()
    } catch (e: Exception) {
        e.printStackTrace()
        null
    }
}

// ⚠️ 新增：私有工具函数 (用于在 IO 线程安全地加载 Bitmap)
private fun loadBitmapFromUri(context: Context, uri: Uri): android.graphics.Bitmap? {
    return try {
        context.contentResolver.openInputStream(uri)?.use { inputStream ->
            BitmapFactory.decodeStream(inputStream)
        }
    } catch (e: Exception) {
        e.printStackTrace()
        null
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onLogout: () -> Unit,
    onAccountDeleted: () -> Unit
) {
    val auth = FirebaseAuth.getInstance()
    val user = auth.currentUser
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // 1. Snackbar Fix: 定义并记住 SnackbarHostState
    val snackbarHostState = remember { SnackbarHostState() }

    // 2. Avatar States
    var avatarBitmap by remember { mutableStateOf<android.graphics.Bitmap?>(null) }
    var avatarUri by remember { mutableStateOf<Uri?>(null) } // 存储内部文件 URI
    var isLoadingAvatar by remember { mutableStateOf(false) } // 头像加载状态

    var showDeleteDialog by remember { mutableStateOf(false) }
    var snackBarMessage by remember { mutableStateOf<String?>(null) } // 触发消息

    // 3. LaunchedEffect: 启动时加载本地头像路径
    LaunchedEffect(Unit) {
        val prefs = context.getSharedPreferences("user_settings", 0)
        prefs.getString("avatar_path", null)?.let { path ->
            val file = File(path)
            if (file.exists()) {
                avatarUri = file.toUri()
            }
        }
    }

    // 4. LaunchedEffect: 监听 avatarUri 变化并异步加载 Bitmap
    LaunchedEffect(avatarUri) {
        avatarUri?.let { uri ->
            isLoadingAvatar = true
            scope.launch(Dispatchers.IO) { // ⚠️ 在 IO 线程执行耗时操作
                avatarBitmap = loadBitmapFromUri(context, uri)
                isLoadingAvatar = false
            }
        } ?: run {
            avatarBitmap = null
            isLoadingAvatar = false
        }
    }

    // 5. LaunchedEffect: 监听 snackBarMessage 状态变化并显示 Snackbar
    LaunchedEffect(snackBarMessage) {
        snackBarMessage?.let { message ->
            snackbarHostState.showSnackbar(message)
            snackBarMessage = null // 清除状态以便下次可以再次显示
        }
    }

    // 图片选择器
    val imagePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            scope.launch(Dispatchers.IO) {
                // 使用新的本地持久化函数
                val internalUri = saveImageToInternalStorage(context, uri)
                if (internalUri != null) {
                    avatarUri = internalUri
                    snackBarMessage = "头像已本地保存"
                } else {
                    snackBarMessage = "保存头像失败"
                }
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("用户设置") }
            )
        },
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) } // ⚠️ Snackbar Fix
    ) { padding ->

        Column(
            modifier = Modifier
                .padding(padding)
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {

            // ============= 用户信息展示 =============
            Text("账号信息", style = MaterialTheme.typography.titleLarge)

            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                // ⚠️ 新的头像显示逻辑 (支持加载状态和异步加载)
                Box(
                    modifier = Modifier
                        .size(80.dp)
                        .clip(CircleShape)
                        .clickable(enabled = !isLoadingAvatar) { imagePicker.launch("image/*") },
                    contentAlignment = Alignment.Center
                ) {
                    when {
                        isLoadingAvatar -> {
                            CircularProgressIndicator(Modifier.fillMaxSize())
                        }
                        avatarBitmap != null -> {
                            Image(
                                bitmap = avatarBitmap!!.asImageBitmap(),
                                contentDescription = "User Avatar",
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                        else -> {
                            // 默认图标
                            Surface(
                                modifier = Modifier.fillMaxSize(),
                                color = MaterialTheme.colorScheme.primaryContainer
                            ) {
                                Icon(Icons.Default.Person, contentDescription = null, Modifier.padding(20.dp))
                            }
                        }
                    }
                }

                Spacer(Modifier.width(16.dp))
                Text(user?.email ?: "未知用户", style = MaterialTheme.typography.bodyLarge)
            }

            Divider()

            // ============= 修改密码 =============
            Button(
                onClick = {
                    val email = user?.email
                    if (email != null) {
                        auth.sendPasswordResetEmail(email)
                        snackBarMessage = "重置密码邮件已发送到：$email"
                    } else {
                        snackBarMessage = "请先登录"
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("修改密码（邮件重置）")
            }

            // ============= 清除阅读进度 =============
            Button(
                onClick = {
                    val prefs = context.getSharedPreferences("progress", 0)
                    prefs.edit().clear().apply()
                    snackBarMessage = "已清除本地阅读进度"
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("清除本地阅读进度")
            }

            // ============= 删除账户 =============
            OutlinedButton(
                onClick = { showDeleteDialog = true },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = MaterialTheme.colorScheme.error
                )
            ) {
                Text("删除账户")
            }

            // ============= 退出登录 =============
            Button(
                onClick = {
                    auth.signOut()
                    onLogout()
                },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error
                )
            ) {
                Text("退出登录")
            }
        }

        // ============= 删除账户弹窗 =============
        if (showDeleteDialog) {
            AlertDialog(
                onDismissRequest = { showDeleteDialog = false },
                confirmButton = {
                    TextButton(onClick = {
                        user?.delete()?.addOnCompleteListener { t ->
                            if (t.isSuccessful) {
                                onAccountDeleted()
                            } else {
                                snackBarMessage = "删除失败，请重新登录后再尝试"
                            }
                        }
                        showDeleteDialog = false
                    }) {
                        Text("确认删除")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showDeleteDialog = false }) {
                        Text("取消")
                    }
                },
                title = { Text("确定删除账户？") },
                text = { Text("删除后无法恢复，请谨慎操作。") }
            )
        }

        // ⚠️ 移除原始的错误 Snackbar 逻辑
        /*
        snackBarMessage?.let {
            SnackbarHost(
                hostState = SnackbarHostState()
            )
            snackBarMessage = null
        }
        */
    }
}