package com.ningyu.novelreader.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.filled.Settings
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BookListScreen(
    books: List<String>,
    onImportClick: () -> Unit,
    onBookClick: (String) -> Unit,
    onDeleteBook: (String) -> Unit,
    onRenameBook: (String, String) -> Unit,
    onBatchRename: (List<String>, String) -> Unit,
    onSettingsClick: () -> Unit

    ) {
    var manageMode by remember { mutableStateOf(false) }
    val selectedBooks = remember { mutableStateListOf<String>() }
    var showRenameDialog by remember { mutableStateOf(false) }
    var renamePrefix by remember { mutableStateOf("") }

    val colorPalette = listOf(
        Color(0xFFE3F2FD),

    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (manageMode) "管理模式（${selectedBooks.size} 已选）" else "我的书库") },
                actions = {
                    if (manageMode) {
                        TextButton(onClick = {
                            manageMode = false
                            selectedBooks.clear()
                        }) { Text("退出") }
                    } else {
                        IconButton(onClick = { onSettingsClick() }) {
                            Icon(Icons.Default.Settings, contentDescription = "用户设置")
                        }
                        IconButton(onClick = { manageMode = true }) {
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
                        OutlinedButton(onClick = onImportClick) { Text("导入小说") }
                        OutlinedButton(
                            onClick = { showRenameDialog = true },
                            enabled = selectedBooks.isNotEmpty()
                        ) { Text("重命名") }
                        OutlinedButton(
                            onClick = {
                                selectedBooks.forEach { onDeleteBook(it) }
                                selectedBooks.clear()
                            },
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.Red),
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
            itemsIndexed(books) { index, title ->
                val bg = colorPalette[index % colorPalette.size]
                BookRowCard(
                    title = title,
                    selected = selectedBooks.contains(title),
                    manageMode = manageMode,
                    backgroundColor = bg,
                    onClick = {
                        if (manageMode) {
                            if (selectedBooks.contains(title))
                                selectedBooks.remove(title)
                            else
                                selectedBooks.add(title)
                        } else {
                            onBookClick(title)
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
                TextButton(onClick = {
                    if (renamePrefix.isNotBlank()) {
                        onBatchRename(selectedBooks.toList(), renamePrefix)
                        renamePrefix = ""
                        showRenameDialog = false
                    }
                }) { Text("确定") }
            },
            dismissButton = {
                TextButton(onClick = { showRenameDialog = false }) { Text("取消") }
            },
            title = { Text("批量重命名") },
            text = {
                OutlinedTextField(
                    value = renamePrefix,
                    onValueChange = { renamePrefix = it },
                    label = { Text("输入前缀") }
                )
            }
        )
    }
}

@Composable
fun BookRowCard(
    title: String,
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
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    fontSize = 18.sp,
                    color = MaterialTheme.colorScheme.onSurface
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
