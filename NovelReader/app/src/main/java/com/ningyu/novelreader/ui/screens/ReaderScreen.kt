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

/**
 * Reads a local text file (via Content URI) and displays it with custom pagination.
 * Supports font size, line height, night mode, and cloud + local progress sync.
 */
private fun readTextFromUri(context: Context, uriString: String): String = try {
    val uri = uriString.toUri()
    context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
        ?: "File is empty"
} catch (e: Exception) {
    "Failed to read file: ${e.message}"
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
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var isFullPaginationComplete by remember { mutableStateOf(false) }

    val fontSizeSp = settings.fontSizeSp
    val lineHeightMultiplier = settings.lineHeightMultiplier
    val isNightMode = settings.isNightMode

    var showSettingsDialog by remember { mutableStateOf(false) }
    var topBarVisible by remember { mutableStateOf(true) }

    val textStyle = TextStyle(fontSize = fontSizeSp.sp, lineHeight = (fontSizeSp * lineHeightMultiplier).sp)
    val textColor = if (isNightMode) Color(0xFFE0E0E0) else Color.Black
    val backgroundColor = if (isNightMode) Color(0xFF0D1117) else Color.White

    val pagerState = rememberPagerState(pageCount = { pages.size })

    // Local progress (fallback when no internet)
    val localProgress = remember(title) {
        context.getSharedPreferences("progress", Context.MODE_PRIVATE).getInt(title, 0)
    }

    // Save progress both locally and to cloud
    val saveProgress: () -> Unit = {
        val page = pagerState.currentPage
        context.getSharedPreferences("progress", Context.MODE_PRIVATE).edit {
            putInt(title, page)
            apply()
        }
        scope.launch(Dispatchers.IO) { repository.saveProgress(title, page) }
    }

    // Load and paginate book content
    LaunchedEffect(title, fontSizeSp, lineHeightMultiplier) {
        isLoading = true
        errorMessage = null
        isFullPaginationComplete = false

        val lineHeightPx = with(density) { (fontSizeSp * lineHeightMultiplier).sp.toPx() }
        val screenHeightPx = context.resources.displayMetrics.heightPixels
        val verticalPaddingPx = with(density) { 32.dp.toPx() }
        val availableHeightPx = screenHeightPx - verticalPaddingPx
        val maxLinesPerPage = (availableHeightPx / lineHeightPx).toInt().coerceAtLeast(20)

        val charsPerLine = (38 * (19f / fontSizeSp)).toInt().coerceAtLeast(20)
        var charsPerPage = maxLinesPerPage * charsPerLine - 10

        withContext(Dispatchers.IO) {
            val book = repository.getBookByTitle(title) ?: run {
                errorMessage = "Book not found"
                return@withContext
            }

            var fullText = readTextFromUri(context, book.localPath)
                .replace("\uFEFF", "")
                .replace("\u200B", "")
                .replace(Regex("[\\p{Cntrl}&&[^\r\n\t]]"), "")

            if (fullText.length < 10) {
                errorMessage = "File is empty or corrupted"
                return@withContext
            }

            val pagesList = mutableListOf<String>()
            var remainingText = fullText

            while (remainingText.isNotEmpty() && pagesList.size < 50) {
                if (remainingText.length <= charsPerPage) {
                    pagesList.add(remainingText.trim())
                    break
                }

                var cutPos = charsPerPage.coerceAtMost(remainingText.length)
                for (i in cutPos downTo (cutPos - 200).coerceAtLeast(0)) {
                    if (remainingText[i] in "\n ") {
                        cutPos = i + 1
                        break
                    }
                }

                pagesList.add(remainingText.substring(0, cutPos).trim())
                remainingText = remainingText.substring(cutPos).trimStart()
            }

            pages = pagesList
            isLoading = false

            // Continue pagination in background if needed
            if (remainingText.isNotEmpty()) {
                scope.launch(Dispatchers.IO) {
                    val backgroundList = pagesList.toMutableList()
                    var text = remainingText
                    while (text.isNotEmpty()) {
                        if (text.length <= charsPerPage) {
                            backgroundList.add(text.trim())
                            break
                        }
                        var pos = charsPerPage.coerceAtMost(text.length)
                        for (i in pos downTo (pos - 200).coerceAtLeast(0)) {
                            if (text[i] in "\n ") {
                                pos = i + 1
                                break
                            }
                        }
                        backgroundList.add(text.substring(0, pos).trim())
                        text = text.substring(pos).trimStart()
                    }
                    pages = backgroundList
                    isFullPaginationComplete = true
                }
            } else {
                isFullPaginationComplete = true
            }
        }
    }

    // Restore local progress
    LaunchedEffect(pages) {
        if (pages.isNotEmpty()) {
            val target = localProgress.coerceIn(0, pages.lastIndex)
            pagerState.scrollToPage(target)
        }
    }

    // Try to restore cloud progress (higher priority)
    LaunchedEffect(pages) {
        if (pages.isNotEmpty()) {
            val cloudPage = withContext(Dispatchers.IO) { repository.getProgress(title) }
                .coerceIn(0, pages.lastIndex)
            if (cloudPage > pagerState.currentPage) {
                pagerState.scrollToPage(cloudPage)
            }
        }
    }

    // Auto-save when page changes
    LaunchedEffect(pagerState.currentPage, pagerState.isScrollInProgress) {
        if (!pagerState.isScrollInProgress) saveProgress()
    }

    // Hide top bar after 3 seconds of inactivity
    LaunchedEffect(topBarVisible) {
        if (topBarVisible) {
            delay(3000)
            topBarVisible = false
        }
    }

    BackHandler { saveProgress(); onBack() }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(backgroundColor)
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
                            val pageInfo = if (isFullPaginationComplete) {
                                "${pagerState.currentPage + 1}/${pages.size}"
                            } else "${pagerState.currentPage + 1}/???"
                            Text("$title • $pageInfo")
                        },
                        navigationIcon = {
                            IconButton(onClick = { saveProgress(); onBack() }) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                            }
                        },
                        actions = {
                            IconButton(onClick = { showSettingsDialog = true }) {
                                Icon(Icons.Default.Settings, contentDescription = "Reading Settings")
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
        ) { paddingValues ->
            if (isLoading) {
                Box(Modifier.fillMaxSize(), Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator()
                        Spacer(Modifier.height(16.dp))
                        Text("Loading book...", color = textColor)
                    }
                }
            } else if (errorMessage != null) {
                Box(Modifier.fillMaxSize(), Alignment.Center) {
                    Text(errorMessage!!, color = MaterialTheme.colorScheme.error)
                }
            } else {
                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier.padding(paddingValues),
                    beyondViewportPageCount = 3
                ) { pageIndex ->
                    Text(
                        text = pages[pageIndex],
                        style = textStyle.copy(color = textColor),
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 20.dp, vertical = 16.dp)
                    )
                }
            }
        }

        // Reading settings dialog
        if (showSettingsDialog) {
            AlertDialog(
                onDismissRequest = {
                    settings.save()
                    showSettingsDialog = false
                },
                title = { Text("Reading Settings") },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(24.dp)) {
                        Column {
                            Text("Font Size: ${settings.fontSizeSp.toInt()} sp")
                            Slider(
                                value = settings.fontSizeSp,
                                onValueChange = { settings.updateFontSize(it) },
                                valueRange = 14f..32f,
                                steps = 17
                            )
                        }
                        Column {
                            Text("Line Spacing: ${"%.2f".format(settings.lineHeightMultiplier)}")
                            Slider(
                                value = settings.lineHeightMultiplier,
                                onValueChange = { settings.updateLineHeight(it) },
                                valueRange = 1.3f..2.6f,
                                steps = 13
                            )
                        }
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Night Mode")
                            Switch(
                                checked = settings.isNightMode,
                                onCheckedChange = { settings.toggleNightMode(it) }
                            )
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = {
                        settings.save()
                        showSettingsDialog = false
                    }) { Text("Done") }
                }
            )
        }
    }
}