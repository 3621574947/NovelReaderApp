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
import androidx.core.net.toUri

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
    repository: BookRepository,
    onBack: () -> Unit
) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    var content by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(true) }
    var isError by remember { mutableStateOf(false) }

    val paragraphs = remember(content) { content.chunked(2000) }
    val lastIndex = paragraphs.lastIndex.coerceAtLeast(0)
    val totalPages = paragraphs.size.coerceAtLeast(1)

    val localPref = context.getSharedPreferences("progress", 0)
    val currentPage = rememberSaveable { mutableIntStateOf(0) }

    LaunchedEffect(title, repository) {
        isLoading = true
        isError = false

        val book = repository.getBookByTitle(title)

        if (book != null) {
            scope.launch(Dispatchers.IO) {
                val fileContent = readTextFromUri(context, book.localPath.toUri())
                if (fileContent.isNotEmpty() && fileContent != "无法读取文件内容") {
                    content = fileContent
                    val savedPage = repository.getProgress(title)
                    val newLastIndex = content.chunked(2000).lastIndex.coerceAtLeast(0)
                    currentPage.intValue = savedPage.coerceIn(0, newLastIndex)
                } else {
                    isError = true
                }
                isLoading = false
            }.join()
        } else {
            isError = true
            isLoading = false
        }
    }

    fun saveProgress(page: Int) {
        localPref.edit { putInt(title, page) }
        scope.launch(Dispatchers.IO) { repository.saveProgress(title, page) }
    }

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
                    enabled = currentPage.intValue > 0
                ) { Text("上一页") }

                Button(
                    onClick = {
                        if (currentPage.intValue < lastIndex) {
                            currentPage.intValue++
                            saveProgress(currentPage.intValue)
                        }
                    },
                    enabled = currentPage.intValue < lastIndex
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