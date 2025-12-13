package com.ningyu.novelreader.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Book
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
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

/**
 * Main library screen – displays all imported books.
 * Supports search, selection mode, bulk delete, rename, and import.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BookListScreen(
    books: List<Book>,
    onImportClick: () -> Unit,
    onBookClick: (String) -> Unit,
    onDeleteBook: (String) -> Unit,
    onRenameBook: (oldTitle: String, newTitle: String) -> Unit,
    onSettingsClick: () -> Unit
) {
    var isManageMode by remember { mutableStateOf(false) }
    val selectedTitles = remember { mutableStateListOf<String>() }
    var showRenameDialog by remember { mutableStateOf(false) }
    var newTitleInput by remember { mutableStateOf("") }
    var searchQuery by remember { mutableStateOf("") }

    // Filter books based on search query
    val filteredBooks = remember(books, searchQuery) {
        if (searchQuery.isBlank()) books
        else books.filter { it.title.contains(searchQuery, ignoreCase = true) }
    }

    // Background colors for book cards (alternating light tint)
    val cardColors = listOf(
        Color(0xFFE3F2FD),
        Color(0xFFF1F8E9),
        Color(0xFFFFF3E0),
        Color(0xFFF3E5F5)
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        if (isManageMode) "Manage (${selectedTitles.size} selected)"
                        else "My Library"
                    )
                },
                actions = {
                    if (isManageMode) {
                        AppTextButton(onClick = {
                            isManageMode = false
                            selectedTitles.clear()
                        }) { Text("Cancel") }
                    } else {
                        AppIconButton(onClick = onSettingsClick) {
                            Icon(Icons.Default.Settings, contentDescription = "Settings")
                        }
                        AppIconButton(onClick = { isManageMode = true }) {
                            Icon(Icons.Default.MoreVert, contentDescription = "Manage books")
                        }
                    }
                }
            )
        },
        bottomBar = {
            if (isManageMode) {
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    tonalElevation = 8.dp,
                    shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        AppOutlinedButton(onClick = onImportClick) {
                            Text("Import Book")
                        }
                        AppOutlinedButton(
                            onClick = { showRenameDialog = true },
                            enabled = selectedTitles.size == 1
                        ) {
                            Text("Rename")
                        }
                        AppDestructiveOutlinedButton(
                            onClick = {
                                selectedTitles.forEach { onDeleteBook(it) }
                                selectedTitles.clear()
                                isManageMode = false
                            },
                            enabled = selectedTitles.isNotEmpty()
                        ) {
                            Text("Delete")
                        }
                    }
                }
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // Search bar – only visible when not in manage mode
            if (!isManageMode) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    label = { Text("Search books") },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    singleLine = true
                )
            }

            LazyColumn(
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                itemsIndexed(filteredBooks) { index, book ->
                    val backgroundColor = if (selectedTitles.contains(book.title))
                        MaterialTheme.colorScheme.secondaryContainer
                    else cardColors[index % cardColors.size]

                    BookCard(
                        book = book,
                        backgroundColor = backgroundColor,
                        isSelected = selectedTitles.contains(book.title),
                        isManageMode = isManageMode,
                        onClick = {
                            if (isManageMode) {
                                if (selectedTitles.contains(book.title))
                                    selectedTitles.remove(book.title)
                                else
                                    selectedTitles.add(book.title)
                            } else {
                                onBookClick(book.title)
                            }
                        }
                    )
                }
            }
        }
    }

    // Rename dialog
    if (showRenameDialog && selectedTitles.size == 1) {
        AlertDialog(
            onDismissRequest = { showRenameDialog = false },
            title = { Text("Rename Book") },
            text = {
                OutlinedTextField(
                    value = newTitleInput,
                    onValueChange = { newTitleInput = it },
                    label = { Text("New title") },
                    singleLine = true
                )
            },
            confirmButton = {
                AppTextButton(onClick = {
                    if (newTitleInput.isNotBlank()) {
                        onRenameBook(selectedTitles.first(), newTitleInput.trim())
                        selectedTitles.clear()
                        isManageMode = false
                    }
                    showRenameDialog = false
                }) { Text("Confirm") }
            },
            dismissButton = {
                AppTextButton(onClick = { showRenameDialog = false }) { Text("Cancel") }
            }
        )
    }
}

/**
 * Individual book card used in the library list
 */
@Composable
private fun BookCard(
    book: Book,
    backgroundColor: Color,
    isSelected: Boolean,
    isManageMode: Boolean,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = backgroundColor),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Row(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.Book,
                contentDescription = null,
                modifier = Modifier.size(40.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = book.title,
                    fontSize = 18.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "Progress: Page ${book.progress + 1}",
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (isManageMode) {
                Checkbox(
                    checked = isSelected,
                    onCheckedChange = { onClick() }
                )
            }
        }
    }
}