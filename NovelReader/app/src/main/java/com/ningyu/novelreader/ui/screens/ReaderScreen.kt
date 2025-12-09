package com.ningyu.novelreader.ui.screens

import android.annotation.SuppressLint
import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.edit
import androidx.core.net.toUri
import com.ningyu.novelreader.data.BookRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private fun readTextFromUri(context: Context, uriString: String): String = try {
    val uri = uriString.toUri()
    context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
        ?: "文件为空"
} catch (e: Exception) {
    e.printStackTrace()
    "读取失败：${e.message}"
}

@SuppressLint("UnusedMaterial3ScaffoldPaddingParameter")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReaderScreen(
    title: String,
    repository: BookRepository,
    onBack: () -> Unit
) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val density = LocalDensity.current

    var pages by remember { mutableStateOf(emptyList<String>()) }
    var isLoading by remember { mutableStateOf(true) }
    var errorMsg by remember { mutableStateOf<String?>(null) }

    val fontSize = 19.sp
    val lineHeight = 32.sp
    val textStyle = TextStyle(fontSize = fontSize, lineHeight = lineHeight)

    val pagerState = rememberPagerState(pageCount = { pages.size })

    val localProgress = remember(title) {
        context.getSharedPreferences("progress", Context.MODE_PRIVATE)
            .getInt(title, 0)
    }

    val saveProgress = {
        val page = pagerState.currentPage
        context.getSharedPreferences("progress", Context.MODE_PRIVATE).edit {
            putInt(title, page)
            apply()
        }
        scope.launch(Dispatchers.IO) {
            repository.saveProgress(title, page)
        }
    }

    LaunchedEffect(title) {
        isLoading = true
        errorMsg = null

        val book = repository.getBookByTitle(title)
        if (book == null) {
            errorMsg = "书籍不存在或已被删除"
            isLoading = false
            return@LaunchedEffect
        }

        val fullText = withContext(Dispatchers.IO) {
            readTextFromUri(context, book.localPath)
        }

        if (fullText.length < 10) {
            errorMsg = "文件为空或损坏"
            isLoading = false
            return@LaunchedEffect
        }

        val chunks = mutableListOf<String>()
        val sb = StringBuilder()
        for (c in fullText) {
            sb.append(c)
            if (sb.length >= 1200) {
                val breakPos = sb.lastIndexOfAny(charArrayOf('。', '！', '？', '”', '\n'))
                    .let { if (it > 500) it + 1 else sb.length }
                chunks.add(sb.substring(0, breakPos))
                sb.delete(0, breakPos)
            }
        }
        if (sb.isNotEmpty()) chunks.add(sb.toString())

        val finalPages = mutableListOf<String>()
        val pageBuilder = StringBuilder()
        var currentLineCount = 0

        val maxLinesPerPage = with(density) {
            (context.resources.displayMetrics.heightPixels / lineHeight.toPx() * 0.75f).toInt()
                .coerceAtLeast(28)
        }

        for (chunk in chunks) {
            val estimatedLines = chunk.count { it == '\n' } + chunk.length / 38 + 2

            if (currentLineCount + estimatedLines > maxLinesPerPage && pageBuilder.isNotEmpty()) {
                finalPages.add(pageBuilder.toString())
                pageBuilder.clear()
                currentLineCount = 0
            }

            if (pageBuilder.isNotEmpty()) pageBuilder.append("\n\n")
            pageBuilder.append(chunk)
            currentLineCount += estimatedLines
        }
        if (pageBuilder.isNotEmpty()) finalPages.add(pageBuilder.toString())

        pages = finalPages
        isLoading = false
    }

    LaunchedEffect(pages) {
        if (pages.isNotEmpty()) {
            val target = localProgress.coerceIn(0, pages.lastIndex)
            pagerState.scrollToPage(target)
        }
    }

    LaunchedEffect(pages) {
        if (pages.isNotEmpty()) {
            val cloudPage = withContext(Dispatchers.IO) {
                repository.getProgress(title)
            }.coerceIn(0, pages.lastIndex)
            if (cloudPage != localProgress) {
                pagerState.scrollToPage(cloudPage)
            }
        }
    }

    LaunchedEffect(pagerState.currentPage, pagerState.isScrollInProgress) {
        if (!pagerState.isScrollInProgress) {
            saveProgress()
        }
    }

    BackHandler {
        saveProgress()
        onBack()
    }

    if (isLoading) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }

    if (errorMsg != null || pages.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                text = errorMsg ?: "暂无内容",
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyLarge
            )
        }
        return
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text("$title  •  ${pagerState.currentPage + 1}/${pages.size}")
                },
                navigationIcon = {
                    IconButton(onClick = { saveProgress(); onBack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                }
            )
        }
    ) { paddingValues ->
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.padding(paddingValues),
            beyondViewportPageCount = 3
        ) { index ->
            Text(
                text = pages[index],
                style = textStyle,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 20.dp, vertical = 12.dp)
            )
        }
    }
}
