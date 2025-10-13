package com.ningyu.novelreader

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.*
import androidx.core.content.edit
import androidx.core.net.toUri
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.ningyu.novelreader.ui.screens.BookListScreen
import com.ningyu.novelreader.ui.screens.ReaderScreen
import com.ningyu.novelreader.ui.theme.NovelReaderTheme

class MainActivity : ComponentActivity() {

    private val PREFS_NAME = "books_prefs"
    private val KEY_BOOKS = "books"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            NovelReaderTheme {
                val navController = rememberNavController()
                var books by remember { mutableStateOf(loadBooks()) }

                val filePicker = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.OpenDocument(),
                    onResult = { uri: Uri? -> uri?.let { handleFilePicked(it, books) { newBooks -> books = newBooks } } }
                )

                NavHost(
                    navController = navController,
                    startDestination = "booklist"
                ) {
                    composable("booklist") {
                        BookListScreen(
                            books = books.keys.toList(),
                            onAddClick = { filePicker.launch(arrayOf("text/plain")) },
                            onBookClick = { title ->
                                books[title]?.let { uri ->
                                    val text = readTextFromUri(uri)
                                    ReadingHolder.apply {
                                        this.title = title
                                        this.text = text
                                    }
                                    navController.navigate("reader/$title")
                                }
                            }
                        )
                    }

                    composable("reader/{title}") { backStackEntry ->
                        val title = backStackEntry.arguments?.getString("title").orEmpty()
                        ReaderScreen(
                            title = title,
                            content = ReadingHolder.text,
                            onBack = { navController.popBackStack() }
                        )
                    }
                }
            }
        }
    }

    private fun handleFilePicked(uri: Uri, currentBooks: Map<String, Uri>, updateBooks: (Map<String, Uri>) -> Unit) {
        try {
            contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            )
        } catch (e: SecurityException) {
            e.printStackTrace()
        }

        val fileName = getFileNameFromUri(uri)
        val newBooks = currentBooks + (fileName to uri)
        saveBooks(newBooks)
        updateBooks(newBooks)
    }

    private fun saveBooks(books: Map<String, Uri>) {
        val prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        val data = books.entries.joinToString("\n") { (title, uri) -> "$title|$uri" }
        prefs.edit { putString(KEY_BOOKS, data) }
    }

    private fun loadBooks(): Map<String, Uri> {
        val prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        val data = prefs.getString(KEY_BOOKS, "").orEmpty()
        return data.lineSequence()
            .filter { it.isNotBlank() }
            .mapNotNull {
                val parts = it.split("|", limit = 2)
                if (parts.size == 2) parts[0] to parts[1].toUri() else null
            }
            .toMap()
    }

    private fun getFileNameFromUri(uri: Uri): String {
        val cursor = contentResolver.query(uri, null, null, null, null)
        val nameIndex = cursor?.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME) ?: -1
        cursor?.moveToFirst()
        val name = if (nameIndex >= 0) cursor?.getString(nameIndex) else "未知文件"
        cursor?.close()
        return name ?: "未知文件"
    }

    private fun readTextFromUri(uri: Uri): String = try {
        contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }.orEmpty()
    } catch (e: Exception) {
        e.printStackTrace()
        "无法显示文件信息"
    }
}

object ReadingHolder {
    var title: String = ""
    var text: String = ""
}
