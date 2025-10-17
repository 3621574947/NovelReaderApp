package com.ningyu.novelreader.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ningyu.novelreader.data.BookRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import androidx.core.content.edit

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReaderScreen(
    title: String,
    content: String,
    repository: BookRepository,
    onBack: () -> Unit
) {
    val scope = rememberCoroutineScope()
    val paragraphs = remember(content) { content.chunked(2000) }

    val localPref = LocalContext.current.getSharedPreferences("progress", 0)
    val currentPage = rememberSaveable {
        mutableIntStateOf(localPref.getInt(title, 0).coerceIn(0, paragraphs.lastIndex))
    }

    LaunchedEffect(title) {
        val savedPage = repository.getProgress(title)
        if (savedPage != currentPage.intValue) {
            currentPage.intValue = savedPage.coerceIn(0, paragraphs.lastIndex)
        }
    }

    fun saveProgress(page: Int) {
        localPref.edit { putInt(title, page) }
        scope.launch(Dispatchers.IO) { repository.saveProgress(title, page) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("$title - 第 ${currentPage.intValue + 1} / ${paragraphs.size} 页") },
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
                Button(onClick = {
                    if (currentPage.intValue > 0) {
                        currentPage.intValue--
                        saveProgress(currentPage.intValue)
                    }
                }) { Text("上一页") }

                Button(onClick = {
                    if (currentPage.intValue < paragraphs.lastIndex) {
                        currentPage.intValue++
                        saveProgress(currentPage.intValue)
                    }
                }) { Text("下一页") }
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
