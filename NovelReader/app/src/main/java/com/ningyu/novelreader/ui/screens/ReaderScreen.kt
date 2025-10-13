package com.ningyu.novelreader.ui.screens

import android.content.Context
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

val Context.dataStore by preferencesDataStore("reading_progress")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReaderScreen(
    title: String,
    content: String,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val paragraphs = remember(content) { content.chunked(2000) }
    var currentPage by rememberSaveable { mutableIntStateOf(0) }

    LaunchedEffect(title) {
        val key = intPreferencesKey("progress_$title")
        val savedPage = context.dataStore.data.map { it[key] ?: 0 }.first()
        currentPage = savedPage.coerceIn(0, paragraphs.lastIndex)
    }

    fun saveProgress(page: Int) {
        val key = intPreferencesKey("progress_$title")
        scope.launch(Dispatchers.IO) {
            context.dataStore.edit { prefs -> prefs[key] = page }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("$title - 第 ${currentPage + 1} / ${paragraphs.size} 页") },
                navigationIcon = {
                    IconButton(onClick = {
                        saveProgress(currentPage)
                        onBack()
                    }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "返回")
                    }
                }
            )
        },
        bottomBar = {
            ReaderControls(
                currentPage = currentPage,
                totalPages = paragraphs.size,
                onPrev = {
                    if (currentPage > 0) {
                        currentPage--
                        saveProgress(currentPage)
                    }
                },
                onNext = {
                    if (currentPage < paragraphs.lastIndex) {
                        currentPage++
                        saveProgress(currentPage)
                    }
                }
            )
        }
    ) { innerPadding ->
        Text(
            text = paragraphs[currentPage],
            modifier = Modifier
                .padding(innerPadding)
                .padding(16.dp)
                .fillMaxSize()
        )
    }
}

@Composable
private fun ReaderControls(
    currentPage: Int,
    totalPages: Int,
    onPrev: () -> Unit,
    onNext: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Button(onClick = onPrev, enabled = currentPage > 0) { Text("上一页") }
        Button(onClick = onNext, enabled = currentPage < totalPages - 1) { Text("下一页") }
    }
}
