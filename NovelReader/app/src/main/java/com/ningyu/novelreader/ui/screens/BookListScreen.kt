package com.ningyu.novelreader.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Book
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ningyu.novelreader.data.Book
import com.ningyu.novelreader.ui.components.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BookListScreen(
    books: List<Book>,
    onImportClick: () -> Unit,
    onBookClick: (String) -> Unit,
    onDeleteBook: (String) -> Unit,
    onRenameBook: (String, String) -> Unit,
    onSettingsClick: () -> Unit
) {
    var manageMode by remember { mutableStateOf(false) }
    val selectedBooks = remember { mutableStateListOf<String>() }
    var showRenameDialog by remember { mutableStateOf(false) }
    var newTitle by remember { mutableStateOf("") }

    val colorPalette = listOf(
        Color(0xFFE3F2FD)
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (manageMode) "管理模式（${selectedBooks.size} 已选）" else "我的书库") },
                actions = {
                    if (manageMode) {
                        AppTextButton(onClick = {
                            manageMode = false
                            selectedBooks.clear()
                        }) { Text("退出") }
                    } else {
                        AppIconButton(onClick = { onSettingsClick() }) {
                            Icon(Icons.Default.Settings, contentDescription = "用户设置")
                        }
                        AppIconButton(onClick = { manageMode = true }) {
                            Icon(Icons.Default.MoreVert, contentDescription = "管理书籍")
                        }
                    }
                }
            )
        },
        bottomBar = {
            if (manageMode) {
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    tonalElevation = 4.dp,
                    shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
                ) {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        AppOutlinedButton(onClick = onImportClick) { Text("导入小说") }
                        AppOutlinedButton(
                            onClick = {
                                showRenameDialog = true
                            },
                            enabled = selectedBooks.size == 1
                        ) { Text("重命名") }
                        AppDestructiveOutlinedButton(
                            onClick = {
                                selectedBooks.forEach { onDeleteBook(it) }
                                selectedBooks.clear()
                            },
                            enabled = selectedBooks.isNotEmpty()
                        ) { Text("删除") }
                    }
                }
            }
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            itemsIndexed(books) { index, book ->
                val bg = colorPalette[index % colorPalette.size]
                BookRowCard(
                    title = book.title,
                    progress = book.progress,
                    selected = selectedBooks.contains(book.title),
                    manageMode = manageMode,
                    backgroundColor = bg,
                    onClick = {
                        if (manageMode) {
                            if (selectedBooks.contains(book.title))
                                selectedBooks.remove(book.title)
                            else
                                selectedBooks.add(book.title)
                        } else {
                            onBookClick(book.title)
                        }
                    }
                )
            }
        }
    }

    if (showRenameDialog) {
        AlertDialog(
            onDismissRequest = { showRenameDialog = false },
            confirmButton = {
                AppTextButton(onClick = {
                    if (newTitle.isNotBlank() && selectedBooks.size == 1) {
                        onRenameBook(selectedBooks.first(), newTitle)
                        selectedBooks.clear()
                        manageMode = false
                        showRenameDialog = false
                    }
                }) { Text("确定") }
            },
            dismissButton = {
                AppTextButton(onClick = { showRenameDialog = false }) { Text("取消") }
            },
            title = { Text("重命名书籍") },
            text = {
                OutlinedTextField(
                    value = newTitle,
                    onValueChange = { newTitle = it },
                    label = { Text("新标题") }
                )
            }
        )
    }
}

@Composable
fun BookRowCard(
    title: String,
    progress: Int,
    selected: Boolean,
    manageMode: Boolean,
    backgroundColor: Color,
    onClick: () -> Unit
) {
    val displayColor = when {
        selected -> MaterialTheme.colorScheme.secondaryContainer
        else -> backgroundColor
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = displayColor),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Row(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Icon(
                Icons.Default.Book,
                contentDescription = "Book icon",
                modifier = Modifier.size(40.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    fontSize = 18.sp,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "阅读进度: $progress 页",
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (manageMode) {
                Checkbox(
                    checked = selected,
                    onCheckedChange = { onClick() },
                    colors = CheckboxDefaults.colors(checkedColor = MaterialTheme.colorScheme.primary)
                )
            }
        }
    }
}