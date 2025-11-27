package com.ningyu.novelreader.ui.screens

import android.content.Context
import android.net.Uri
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ningyu.novelreader.data.BookRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import androidx.core.content.edit
import androidx.core.net.toUri // 引入 Uri 扩展

// ⚠️ 新增：私有工具函数 (从 MainActivity 移动过来)
private fun readTextFromUri(context: Context, uri: Uri): String = try {
    context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }.orEmpty()
} catch (e: Exception) {
    e.printStackTrace()
    "无法读取文件内容"
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReaderScreen(
    title: String,
    // ⚠️ 移除 content: String,
    repository: BookRepository,
    onBack: () -> Unit
) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    // 1. 新增状态
    var content by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(true) }
    var isError by remember { mutableStateOf(false) }

    // 2. 页面/段落划分 (依赖 content 状态)
    val paragraphs = remember(content) { content.chunked(2000) }
    val lastIndex = paragraphs.lastIndex.coerceAtLeast(0)
    val totalPages = paragraphs.size.coerceAtLeast(1)

    val localPref = context.getSharedPreferences("progress", 0)
    // 3. 页面状态初始化 (初始为 0)
    val currentPage = rememberSaveable { mutableIntStateOf(0) }

    // 4. LaunchedEffect: 异步加载 Book 信息和内容
    LaunchedEffect(title, repository) {
        isLoading = true
        isError = false

        // 1. 从 Firestore 获取 Book 元数据 (localPath)
        val book = repository.getBookByTitle(title) // 依赖 BookRepository 中的新函数

        if (book != null) {
            scope.launch(Dispatchers.IO) { // 在 IO 线程读取文件
                val fileContent = readTextFromUri(context, book.localPath.toUri())
                if (fileContent.isNotEmpty() && fileContent != "无法读取文件内容") {
                    content = fileContent
                    // 2. 内容加载成功后，加载进度并确保页码在范围内
                    val savedPage = repository.getProgress(title)
                    // 重新计算 lastIndex
                    val newLastIndex = content.chunked(2000).lastIndex.coerceAtLeast(0)
                    currentPage.intValue = savedPage.coerceIn(0, newLastIndex)
                } else {
                    isError = true
                }
                isLoading = false
            }.join() // 等待文件读取完成
        } else {
            isError = true
            isLoading = false
        }
    }

    fun saveProgress(page: Int) {
        localPref.edit { putInt(title, page) }
        scope.launch(Dispatchers.IO) { repository.saveProgress(title, page) }
    }

    // 5. 加载和错误状态 UI
    if (isLoading) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }

    if (isError || content.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("抱歉，无法加载或读取小说内容。", color = MaterialTheme.colorScheme.error)
        }
        return
    }

    // 确保页码不超出新计算的范围
    LaunchedEffect(lastIndex) {
        currentPage.intValue = currentPage.intValue.coerceIn(0, lastIndex)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("$title - 第 ${currentPage.intValue + 1} / $totalPages 页") },
                navigationIcon = {
                    IconButton(onClick = {
                        saveProgress(currentPage.intValue)
                        onBack()
                    }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回") }
                }
            )
        },
        bottomBar = {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Button(
                    onClick = {
                        if (currentPage.intValue > 0) {
                            currentPage.intValue--
                            saveProgress(currentPage.intValue)
                        }
                    },
                    enabled = currentPage.intValue > 0 // 增强 UX
                ) { Text("上一页") }

                Button(
                    onClick = {
                        if (currentPage.intValue < lastIndex) {
                            currentPage.intValue++
                            saveProgress(currentPage.intValue)
                        }
                    },
                    enabled = currentPage.intValue < lastIndex // 增强 UX
                ) { Text("下一页") }
            }
        }
    ) { innerPadding ->
        Text(
            text = paragraphs[currentPage.intValue],
            modifier = Modifier
                .padding(innerPadding)
                .padding(16.dp)
                .fillMaxSize(),
            fontSize = 18.sp,
            lineHeight = 26.sp
        )
    }
}