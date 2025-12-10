package com.ningyu.novelreader.ui.screens

import android.annotation.SuppressLint
import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.edit
import androidx.core.net.toUri
import com.ningyu.novelreader.data.BookRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
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

    val settings = rememberReadingSettings()

    var pages by remember { mutableStateOf(emptyList<String>()) }
    var isLoading by remember { mutableStateOf(true) }
    var errorMsg by remember { mutableStateOf<String?>(null) }

    var isFullPaginationComplete by remember { mutableStateOf(false) }

    val fontSizeSp = settings.fontSizeSp
    val lineHeightMultiplier = settings.lineHeightMultiplier
    val isNightMode = settings.isNightMode

    var showSettingsDialog by remember { mutableStateOf(false) }
    var topBarVisible by remember { mutableStateOf(false) }

    val lineHeightSp = fontSizeSp * lineHeightMultiplier
    val textStyle = TextStyle(fontSize = fontSizeSp.sp, lineHeight = lineHeightSp.sp)
    val textColor = if (isNightMode) Color(0xFFE0E0E0) else Color.Black
    val bgColor = if (isNightMode) Color(0xFF0D1117) else Color.White

    val pagerState = rememberPagerState(pageCount = { pages.size })

    val localProgress = remember(title) {
        context.getSharedPreferences("progress", Context.MODE_PRIVATE).getInt(title, 0)
    }

    val saveProgress = {
        val page = pagerState.currentPage
        context.getSharedPreferences("progress", Context.MODE_PRIVATE).edit {
            putInt(title, page)
            apply()
        }
        scope.launch(Dispatchers.IO) { repository.saveProgress(title, page) }
    }

    LaunchedEffect(title, fontSizeSp, lineHeightMultiplier) {
        isLoading = true
        errorMsg = null
        isFullPaginationComplete = false

        val lineHeightPx = with(density) { lineHeightSp.sp.toPx() }
        val screenHeightPx = context.resources.displayMetrics.heightPixels

        val verticalPaddingPx = with(density) { 16.dp.toPx() * 2 }

        val availableHeightPx = screenHeightPx - verticalPaddingPx

        val maxLinesPerPage = ((availableHeightPx / lineHeightPx) * 0.95f).toInt().coerceAtLeast(20)

        val baseFontSize = 19.0f
        val baseCharsPerLine = 38
        val charsPerLine = (baseCharsPerLine * (baseFontSize / fontSizeSp)).toInt().coerceAtLeast(20)

        var charsPerPage = maxLinesPerPage * charsPerLine
        charsPerPage = (charsPerPage - 5).coerceAtLeast(charsPerLine)

        val (initialPages, initialRemaining, initialErrorMsg) = withContext(Dispatchers.IO) {
            val book = repository.getBookByTitle(title)
                ?: return@withContext Triple(emptyList<String>(), "", "书籍不存在")

            var fullText = readTextFromUri(context, book.localPath)

            fullText = fullText
                .replace("\uFEFF", "")
                .replace("\u200B", "")
                .replace(Regex("[\\p{Cntrl}&&[^\r\n\t]]"), "")

            if (fullText.length < 10) {
                return@withContext Triple(emptyList<String>(), "", "文件为空或损坏")
            }

            val initialPageLimit = 50
            val pagesList = mutableListOf<String>()
            var remaining = fullText

            while (remaining.isNotEmpty() && pagesList.size < initialPageLimit) {
                if (remaining.length <= charsPerPage) {
                    pagesList.add(remaining)
                    remaining = ""
                    break
                }

                var cutPos = charsPerPage.coerceAtMost(remaining.length)

                for (i in cutPos downTo (cutPos - 200).coerceAtLeast(0)) {
                    val char = remaining[i]
                    if (char == ' ' || char == '\n') {
                        cutPos = i + 1
                        break
                    }
                }

                pagesList.add(remaining.substring(0, cutPos).trim())
                remaining = remaining.substring(cutPos).trimStart()
            }

            return@withContext Triple(pagesList, remaining, null)
        }

        pages = initialPages
        errorMsg = initialErrorMsg
        isLoading = false

        if (initialRemaining.isNotEmpty() && initialErrorMsg == null) {
            scope.launch(Dispatchers.IO) {
                var remaining = initialRemaining
                val backgroundPagesList = initialPages.toMutableList()

                while (remaining.isNotEmpty()) {
                    if (remaining.length <= charsPerPage) {
                        backgroundPagesList.add(remaining)
                        break
                    }

                    var cutPos = charsPerPage.coerceAtMost(remaining.length)

                    for (i in cutPos downTo (cutPos - 200).coerceAtLeast(0)) {
                        val char = remaining[i]
                        if (char == ' ' || char == '\n') {
                            cutPos = i + 1
                            break
                        }
                    }

                    backgroundPagesList.add(remaining.substring(0, cutPos).trim())
                    remaining = remaining.substring(cutPos).trimStart()
                }

                withContext(Dispatchers.Main) {
                    pages = backgroundPagesList
                    isFullPaginationComplete = true
                }
            }
        } else {
            isFullPaginationComplete = true
        }
    }

    LaunchedEffect(pages) {
        if (pages.isNotEmpty()) {
            val target = localProgress.coerceIn(0, pages.lastIndex)
            pagerState.scrollToPage(target)
        }
    }

    LaunchedEffect(pages) {
        if (pages.isNotEmpty()) {
            val cloud = withContext(Dispatchers.IO) { repository.getProgress(title) }
                .coerceIn(0, pages.lastIndex)
            if (cloud > pagerState.currentPage) pagerState.scrollToPage(cloud)
        }
    }

    LaunchedEffect(pagerState.currentPage, pagerState.isScrollInProgress) {
        if (!pagerState.isScrollInProgress) saveProgress()
    }

    BackHandler { saveProgress(); onBack() }

    if (isLoading) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
            Text("正在快速加载...", modifier = Modifier.offset(y = 40.dp))
        }
        return
    }

    if (errorMsg != null || pages.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(errorMsg ?: "暂无内容", color = MaterialTheme.colorScheme.error)
        }
        return
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(bgColor)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) { topBarVisible = !topBarVisible }
    ) {
        Scaffold(
            topBar = {
                if (topBarVisible) {
                    CenterAlignedTopAppBar(
                        title = {
                            val pageText = if (isFullPaginationComplete) {
                                "${pagerState.currentPage + 1}/${pages.size}"
                            } else {
                                "${pagerState.currentPage + 1}/???"
                            }
                            Text("$title  •  $pageText")
                        },
                        navigationIcon = {
                            IconButton(onClick = { saveProgress(); onBack() }) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回")
                            }
                        },
                        actions = {
                            IconButton(onClick = { showSettingsDialog = true }) {
                                Icon(Icons.Default.Settings, "阅读设置")
                            }
                        },
                        colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                            containerColor = if (isNightMode) Color(0xFF1E1E1E)
                            else MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.95f)
                        )
                    )
                }
            },
            containerColor = Color.Transparent
        ) { padding ->
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.padding(padding),
                beyondViewportPageCount = 3
            ) { index ->
                Text(
                    text = pages[index],
                    style = textStyle.copy(color = textColor),
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 20.dp, vertical = 16.dp)
                )
            }
        }

        LaunchedEffect(topBarVisible) {
            if (topBarVisible) {
                delay(3000)
                topBarVisible = false
            }
        }
    }

    if (showSettingsDialog) {
        AlertDialog(
            onDismissRequest = {
                settings.save()
                showSettingsDialog = false
            },
            title = { Text("阅读设置") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(24.dp)) {
                    Column {
                        Text("字体大小：${settings.fontSizeSp.toInt()} sp")
                        Slider(
                            value = settings.fontSizeSp,
                            onValueChange = { settings.fontSizeSp = it },
                            valueRange = 14f..32f,
                            steps = 17
                        )
                    }
                    Column {
                        Text("行间距：${"%.2f".format(settings.lineHeightMultiplier)}")
                        Slider(
                            value = settings.lineHeightMultiplier,
                            onValueChange = { settings.lineHeightMultiplier = it },
                            valueRange = 1.3f..2.6f,
                            steps = 13
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("夜间模式")
                        Switch(
                            checked = settings.isNightMode,
                            onCheckedChange = { settings.isNightMode = it }
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    settings.save()
                    showSettingsDialog = false
                }) { Text("完成") }
            }
        )
    }
}