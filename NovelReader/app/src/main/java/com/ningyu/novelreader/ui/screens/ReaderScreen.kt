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
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Constraints
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
 * Utility function to read the entire text content from a Content URI (SAF-based file access).
 * @param context The application context for content resolution.
 * @param uriString The string representation of the persistent Content URI.
 * @return The full text content of the file, or an error message if reading fails.
 */
private fun readTextFromUri(context: Context, uriString: String): String = try {
    val uri = uriString.toUri()
    context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
        ?: "File is empty"
} catch (e: Exception) {
    "Failed to read file: ${e.message}"
}

@SuppressLint("UnusedMaterial3ScaffoldPaddingParameter", "LocalContextResourcesRead")
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

    val textMeasurer = rememberTextMeasurer()

    var pages by remember { mutableStateOf(emptyList<String>()) }
    var pageOffsets by remember { mutableStateOf(emptyList<Int>()) }

    var isLoading by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var isFullPaginationComplete by remember { mutableStateOf(false) }

    var currentGlobalIndex by remember { mutableIntStateOf(0) }
    var isFirstLoad by remember { mutableStateOf(true) }

    // Reading settings variables that trigger recomposition and repagination.
    val fontSizeSp = settings.fontSizeSp
    val lineHeightMultiplier = settings.lineHeightMultiplier
    val isNightMode = settings.isNightMode

    var showSettingsDialog by remember { mutableStateOf(false) }
    var topBarVisible by remember { mutableStateOf(true) }

    // Calculate the text style based on current settings.
    val textStyle = TextStyle(
        fontSize = fontSizeSp.sp,
        lineHeight = (fontSizeSp * lineHeightMultiplier).sp,
        fontWeight = FontWeight.Normal
    )
    val textColor = if (isNightMode) Color(0xFFE0E0E0) else Color.Black
    val backgroundColor = if (isNightMode) Color(0xFF0D1117) else Color.White

    // Padding values for the text content area.
    val horizontalPaddingDp = 20.dp
    val verticalPaddingDp = 16.dp

    val pagerState = rememberPagerState(pageCount = { pages.size })

    // Retrieve the locally saved reading progress for the current book.
    val initialLocalPage = remember(title) {
        context.getSharedPreferences("progress", Context.MODE_PRIVATE).getInt(title, 0)
    }

    /**
     * Function to save the current page progress locally (SharedPreferences) and remotely (Firestore).
     */
    val saveProgress: () -> Unit = {
        val page = pagerState.currentPage
        context.getSharedPreferences("progress", Context.MODE_PRIVATE).edit {
            putInt(title, page)
            apply()
        }
        scope.launch(Dispatchers.IO) { repository.saveProgress(title, page) }
    }

    /**
     * Effect to handle book loading and text pagination.
     * Reruns when title or text style settings change.
     */
    LaunchedEffect(title, fontSizeSp, lineHeightMultiplier) {
        isLoading = true
        errorMessage = null
        isFullPaginationComplete = false

        val screenWidthPx = context.resources.displayMetrics.widthPixels
        val screenHeightPx = context.resources.displayMetrics.heightPixels

        val contentWidth = with(density) { screenWidthPx - (horizontalPaddingDp.toPx() * 2) }.toInt()
        val contentHeight = with(density) { screenHeightPx - (verticalPaddingDp.toPx() * 2) }.toInt()

        val constraints = Constraints(maxWidth = contentWidth, maxHeight = contentHeight)

        withContext(Dispatchers.Default) {
            val book = repository.getBookByTitle(title) ?: run {
                errorMessage = "Book not found"
                return@withContext
            }

            var fullText = readTextFromUri(context, book.localPath)
                .replace("\uFEFF", "") // BOM (Byte Order Mark)
                .replace("\u200B", "") // Zero Width Space


            if (fullText.length < 5) {
                errorMessage = "File is empty or corrupted"
                return@withContext
            }

            val pagesList = mutableListOf<String>()
            val offsetsList = mutableListOf<Int>()
            var currentOffset = 0

            /**
             * Core pagination function. Uses TextMeasurer to find the character offset
             * where the text overflows the screen constraints.
             * It paginates a limited amount (pageLimit) quickly for the initial display.
             */
            fun paginate(text: String, pageLimit: Int = Int.MAX_VALUE): String {
                var remaining = text
                var pagesAdded = 0

                while (remaining.isNotEmpty() && pagesAdded < pageLimit) {
                    offsetsList.add(currentOffset)

                    // Measure in chunks to avoid measuring the entire text at once (performance).
                    val measureChunkSize = 3000
                    val chunkToMeasure = if (remaining.length > measureChunkSize) {
                        remaining.substring(0, measureChunkSize)
                    } else {
                        remaining
                    }

                    val result = textMeasurer.measure(
                        text = AnnotatedString(chunkToMeasure),
                        style = textStyle,
                        constraints = constraints
                    )

                    if (result.hasVisualOverflow) {
                        val lastVisibleLineIndex = (0 until result.lineCount).lastOrNull {
                            result.getLineBottom(it) <= contentHeight
                        } ?: 0

                        val endOffset = result.getLineEnd(lastVisibleLineIndex, visibleEnd = true)

                        val pageText = chunkToMeasure.substring(0, endOffset)

                        pagesList.add(pageText)
                        currentOffset += pageText.length

                        remaining = remaining.substring(endOffset)
                    } else {
                        if (chunkToMeasure.length == remaining.length) {
                            pagesList.add(remaining)
                            currentOffset += remaining.length
                            remaining = ""
                        } else {
                            pagesList.add(chunkToMeasure)
                            currentOffset += chunkToMeasure.length
                            remaining = remaining.substring(chunkToMeasure.length)
                        }
                    }
                    pagesAdded++
                }
                return remaining
            }

            var remainingText = fullText
            remainingText = paginate(remainingText, 200)

            pages = pagesList
            pageOffsets = offsetsList
            isLoading = false

            val targetPage = if (isFirstLoad) {
                val p = initialLocalPage.coerceIn(0, pagesList.lastIndex)
                if (offsetsList.isNotEmpty() && p < offsetsList.size) {
                    currentGlobalIndex = offsetsList[p] // Record the global index.
                }
                p
            } else {
                var foundPage = 0
                for (i in offsetsList.indices) {
                    if (offsetsList[i] > currentGlobalIndex) {
                        break
                    }
                    foundPage = i
                }

                if (foundPage > 0 && offsetsList[foundPage] > currentGlobalIndex) foundPage - 1 else foundPage
            }

            withContext(Dispatchers.Main) {
                if (pagesList.isNotEmpty()) {
                    pagerState.scrollToPage(targetPage)
                }
                isFirstLoad = false
            }

            // Start background pagination for the rest of the book content.
            if (remainingText.isNotEmpty()) {
                scope.launch(Dispatchers.Default) {
                    val bgPages = pagesList.toMutableList()
                    val bgOffsets = offsetsList.toMutableList()
                    var bgRemaining = remainingText
                    var bgCurrentOffset = currentOffset

                    while (bgRemaining.isNotEmpty()) {
                        bgOffsets.add(bgCurrentOffset)

                        val measureChunkSize = 3000
                        val chunkToMeasure = if (bgRemaining.length > measureChunkSize) {
                            bgRemaining.substring(0, measureChunkSize)
                        } else {
                            bgRemaining
                        }

                        val result = textMeasurer.measure(
                            text = AnnotatedString(chunkToMeasure),
                            style = textStyle,
                            constraints = constraints
                        )

                        if (result.hasVisualOverflow) {
                            val lastVisibleLineIndex = (0 until result.lineCount).lastOrNull {
                                result.getLineBottom(it) <= contentHeight
                            } ?: 0
                            val endOffset = result.getLineEnd(lastVisibleLineIndex, visibleEnd = true)
                            val pageText = chunkToMeasure.substring(0, endOffset)

                            bgPages.add(pageText)
                            bgCurrentOffset += pageText.length
                            bgRemaining = bgRemaining.substring(endOffset)
                        } else {
                            if (chunkToMeasure.length == bgRemaining.length) {
                                bgPages.add(bgRemaining)
                                bgCurrentOffset += bgRemaining.length
                                bgRemaining = ""
                            } else {
                                bgPages.add(chunkToMeasure)
                                bgCurrentOffset += chunkToMeasure.length
                                bgRemaining = bgRemaining.substring(chunkToMeasure.length)
                            }
                        }
                    }

                    pages = bgPages
                    pageOffsets = bgOffsets
                    isFullPaginationComplete = true
                }
            } else {
                isFullPaginationComplete = true
            }
        }
    }

    // Effect to update the global index and save progress when the page changes.
    LaunchedEffect(pagerState.currentPage) {
        if (!isLoading && pages.isNotEmpty() && pagerState.currentPage < pageOffsets.size) {
            currentGlobalIndex = pageOffsets[pagerState.currentPage]
            if (!pagerState.isScrollInProgress) saveProgress() // Save progress only if not scrolling.
        }
    }

    // Effect to save progress immediately after scrolling stops.
    LaunchedEffect(pagerState.isScrollInProgress) {
        if (!pagerState.isScrollInProgress) saveProgress()
    }

    // Effect to sync progress from the cloud on initial load, overriding local state if cloud is further.
    LaunchedEffect(Unit) {
        if (pages.isNotEmpty()) {
            val cloudPage = withContext(Dispatchers.IO) { repository.getProgress(title) }
            if (cloudPage > pagerState.currentPage && cloudPage < pages.size) {
                pagerState.scrollToPage(cloudPage)
                if (cloudPage < pageOffsets.size) {
                    currentGlobalIndex = pageOffsets[cloudPage]
                }
            }
        }
    }

    // Effect to auto-hide the top bar after a delay.
    LaunchedEffect(topBarVisible) {
        if (topBarVisible) {
            delay(3000) // Wait 3 seconds.
            topBarVisible = false
        }
    }

    // Custom back handler to save progress before navigating back.
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
                            } else "${pagerState.currentPage + 1}/..."
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
                            // Customize top bar colors based on night mode.
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
                    CircularProgressIndicator()
                }
            } else if (errorMessage != null) {
                Box(Modifier.fillMaxSize(), Alignment.Center) {
                    Text(errorMessage!!, color = MaterialTheme.colorScheme.error)
                }
            } else {
                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier.padding(paddingValues),
                    beyondViewportPageCount = 1
                ) { pageIndex ->
                    if (pageIndex < pages.size) {
                        Text(
                            text = pages[pageIndex],
                            style = textStyle.copy(color = textColor),
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = horizontalPaddingDp, vertical = verticalPaddingDp)
                        )
                    }
                }
            }
        }

        // Reading Settings Dialog
        if (showSettingsDialog) {
            AlertDialog(
                onDismissRequest = {
                    settings.save()
                    showSettingsDialog = false
                },
                title = { Text("Reading Settings") },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(24.dp)) {
                        // Font Size Slider
                        Column {
                            Text("Font Size: ${settings.fontSizeSp.toInt()} sp")
                            Slider(
                                value = settings.fontSizeSp,
                                onValueChange = { settings.updateFontSize(it) },
                                valueRange = 14f..32f,
                                steps = 18
                            )
                        }
                        // Line Spacing Slider
                        Column {
                            Text("Line Spacing: ${"%.1f".format(settings.lineHeightMultiplier)}")
                            Slider(
                                value = settings.lineHeightMultiplier,
                                onValueChange = { settings.updateLineHeight(it) },
                                valueRange = 1.0f..2.5f,
                                steps = 15
                            )
                        }
                        // Night Mode Switch
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