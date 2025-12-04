package com.ningyu.novelreader.ui.screens

import android.content.Context
import android.content.Intent
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
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.edit
import androidx.core.net.toUri
import com.google.firebase.auth.FirebaseAuth
import com.ningyu.novelreader.data.BookRepository
import com.ningyu.novelreader.ui.components.AppButton
import com.ningyu.novelreader.ui.components.AppDestructiveButton
import com.ningyu.novelreader.ui.components.AppDestructiveOutlinedButton
import com.ningyu.novelreader.ui.components.AppTextButton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File
import java.io.InputStream
import java.io.IOException

private fun saveImageToInternalStorage(context: Context, uri: Uri): Uri? {
    return try {
        val inputStream = context.contentResolver.openInputStream(uri)
        val file = File(context.filesDir, "avatar_image.jpg")

        file.outputStream().use { output ->
            inputStream?.copyTo(output)
        }

        context.getSharedPreferences("user_settings", 0).edit {
            putString("avatar_path", file.absolutePath)
        }

        return file.toUri()
    } catch (e: Exception) {
        e.printStackTrace()
        null
    }
}

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
    repository: BookRepository,
    onAccountDeleted: () -> Unit
) {
    val auth = FirebaseAuth.getInstance()
    val user = auth.currentUser
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val snackbarHostState = remember { SnackbarHostState() }

    var avatarBitmap by remember { mutableStateOf<android.graphics.Bitmap?>(null) }
    var avatarUri by remember { mutableStateOf<Uri?>(null) }
    var isLoadingAvatar by remember { mutableStateOf(false) }

    var showDeleteDialog by remember { mutableStateOf(false) }
    var snackBarMessage by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        val prefs = context.getSharedPreferences("user_settings", 0)
        prefs.getString("avatar_path", null)?.let { path ->
            val file = File(path)
            if (file.exists()) {
                avatarUri = file.toUri()
            }
        }
    }

    LaunchedEffect(avatarUri) {
        avatarUri?.let { uri ->
            isLoadingAvatar = true
            scope.launch(Dispatchers.IO) {
                avatarBitmap = loadBitmapFromUri(context, uri)
                isLoadingAvatar = false
            }
        } ?: run {
            avatarBitmap = null
            isLoadingAvatar = false
        }
    }

    LaunchedEffect(snackBarMessage) {
        snackBarMessage?.let { message ->
            snackbarHostState.showSnackbar(message)
            snackBarMessage = null
        }
    }
    var showDeleteAllDialog by remember { mutableStateOf(false) }
    val imagePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            scope.launch(Dispatchers.IO) {
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
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) }
    ) { padding ->

        Column(
            modifier = Modifier
                .padding(padding)
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {

            Text("账号信息", style = MaterialTheme.typography.titleLarge)

            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
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

            HorizontalDivider(Modifier, DividerDefaults.Thickness, DividerDefaults.color)

            AppButton(
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

            AppButton(
                onClick = {
                    val prefs = context.getSharedPreferences("progress", 0)
                    prefs.edit { clear() }
                    scope.launch {
                        repository.clearAllProgress()
                    }
                    snackBarMessage = "已清除本地阅读进度"
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("清除本地阅读进度")
            }



            AppDestructiveButton(
                onClick = { showDeleteAllDialog = true },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("一键删除本地书籍")
            }

            if (showDeleteAllDialog) {
                AlertDialog(
                    onDismissRequest = { showDeleteAllDialog = false },
                    confirmButton = {
                        TextButton(onClick = {
                            scope.launch(Dispatchers.IO) {
                                val books = repository.getAllBooks()
                                val prefs = context.getSharedPreferences("progress", 0)
                                prefs.edit { clear() }
                                books.forEach { book ->
                                    val uri = book.localPath.toUri()
                                    try {
                                        context.contentResolver.releasePersistableUriPermission(
                                            uri,
                                            Intent.FLAG_GRANT_READ_URI_PERMISSION
                                        )
                                    } catch (e: Exception) {
                                    }
                                    repository.deleteBook(book.title)
                                }
                                snackBarMessage = "已删除所有书籍"
                            }
                            showDeleteAllDialog = false
                        }) {
                            Text("确认")
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { showDeleteAllDialog = false }) {
                            Text("取消")
                        }
                    },
                    title = { Text("确定删除所有书籍？") },
                    text = { Text("此操作将删除所有书籍记录和释放本地文件权限，无法恢复。") }
                )
            }

            AppDestructiveButton(
                onClick = {
                    auth.signOut()
                    onLogout()
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("退出登录")
            }

            AppDestructiveOutlinedButton(
                onClick = { showDeleteDialog = true },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("删除账户")
            }
        }

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

    }
}