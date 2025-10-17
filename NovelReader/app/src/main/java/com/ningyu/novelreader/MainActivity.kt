package com.ningyu.novelreader

import android.content.ContentResolver
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.*
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.ningyu.novelreader.data.Book
import com.ningyu.novelreader.data.BookRepository
import com.ningyu.novelreader.ui.screens.BookListScreen
import com.ningyu.novelreader.ui.screens.ReaderScreen
import com.ningyu.novelreader.ui.theme.NovelReaderTheme
import kotlinx.coroutines.launch
import androidx.core.net.toUri

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            NovelReaderTheme {
                val navController = rememberNavController()
                val repository = remember { BookRepository() }
                var books by remember { mutableStateOf<List<Book>>(emptyList()) }
                val scope = rememberCoroutineScope()

                LaunchedEffect(Unit) {
                    repository.listenToBooks { updated -> books = updated }
                }

                val filePicker = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.OpenDocument(),
                    onResult = { uri: Uri? ->
                        uri?.let {
                            contentResolver.takePersistableUriPermission(
                                it, Intent.FLAG_GRANT_READ_URI_PERMISSION
                            )
                            scope.launch {
                                val name = getFileNameFromUri(contentResolver, it) ?: "未命名小说"
                                repository.addBook(name, it.toString())
                            }
                        }
                    }
                )

                NavHost(navController, startDestination = "booklist") {
                    composable("booklist") {
                        BookListScreen(
                            books = books.map { it.title },
                            onImportClick = { filePicker.launch(arrayOf("text/*", "text/plain")) },
                            onBookClick = { title ->
                                val book = books.find { it.title == title }
                                if (book != null) {
                                    val text = readTextFromUri(book.localPath.toUri())
                                    ReadingHolder.apply {
                                        this.title = title
                                        this.text = text
                                    }
                                    navController.navigate("reader/$title")
                                }
                            },
                            onDeleteBook = { title ->
                                scope.launch { repository.deleteBook(title) }
                            },
                            onRenameBook = { oldTitle, newTitle ->
                                scope.launch { repository.renameBook(oldTitle, newTitle) }
                            },
                            onBatchRename = { selected, prefix ->
                                scope.launch {
                                    selected.forEachIndexed { index, oldTitle ->
                                        val newTitle = "$prefix${index + 1}"
                                        repository.renameBook(oldTitle, newTitle)
                                    }
                                }
                            }
                        )
                    }

                    composable("reader/{title}") { backStackEntry ->
                        val title = backStackEntry.arguments?.getString("title").orEmpty()
                        ReaderScreen(
                            title = title,
                            content = ReadingHolder.text,
                            repository = repository,
                            onBack = { navController.popBackStack() }
                        )
                    }
                }
            }
        }
    }

    private fun readTextFromUri(uri: Uri): String = try {
        contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }.orEmpty()
    } catch (e: Exception) {
        e.printStackTrace()
        "无法读取文件内容"
    }

    private fun getFileNameFromUri(resolver: ContentResolver, uri: Uri): String? {
        return try {
            resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (idx >= 0) {
                        var name = cursor.getString(idx)
                        name = name.substringBeforeLast('.', name)
                        return name
                    }
                }
                null
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
}

object ReadingHolder {
    var title: String = ""
    var text: String = ""
}
