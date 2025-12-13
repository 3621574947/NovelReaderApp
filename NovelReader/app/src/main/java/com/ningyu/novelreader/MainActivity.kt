package com.ningyu.novelreader

import android.content.ContentResolver
import android.content.Context
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
import com.ningyu.novelreader.ui.screens.*
import com.ningyu.novelreader.ui.theme.NovelReaderTheme
import kotlinx.coroutines.launch

/**
 * Main entry point of the application.
 * Sets up navigation, Firebase listeners, and file import functionality.
 */
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            NovelReaderTheme {
                val navController = rememberNavController()
                val repository = remember { BookRepository() }
                val scope = rememberCoroutineScope()

                // Live list of books from Firestore – updated in real time
                var books by remember { mutableStateOf(emptyList<Book>()) }

                // Listen to Firestore changes and keep local state in sync
                LaunchedEffect(repository) {
                    repository.listenToBooks { updatedBooks ->
                        books = updatedBooks
                    }
                }

                // File picker for importing .txt novels using Storage Access Framework (SAF)
                val fileImportLauncher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.OpenDocument()
                ) { uri: Uri? ->
                    uri?.let { selectedUri ->
                        // Request persistent permission so we can read the file later
                        contentResolver.takePersistableUriPermission(
                            selectedUri,
                            Intent.FLAG_GRANT_READ_URI_PERMISSION
                        )

                        val fileName = getFileNameFromUri(contentResolver, selectedUri)
                        if (fileName != null) {
                            scope.launch {
                                repository.addBook(fileName, selectedUri.toString())
                            }
                        }
                    }
                }

                // Navigation graph
                NavHost(
                    navController = navController,
                    startDestination = "splash"
                ) {
                    composable("splash") {
                        SplashScreen { destination ->
                            navController.navigate(destination) {
                                popUpTo("splash") { inclusive = true }
                            }
                        }
                    }

                    composable("login") {
                        LoginScreen(
                            onLoginSuccess = {
                                navController.navigate("booklist") {
                                    popUpTo("login") { inclusive = true }
                                }
                            },
                            onGoToRegister = { navController.navigate("register") }
                        )
                    }

                    composable("register") {
                        RegisterScreen(
                            onRegisterSuccess = {
                                navController.navigate("booklist") {
                                    popUpTo("register") { inclusive = true }
                                }
                            },
                            onGoToLogin = { navController.popBackStack() }
                        )
                    }

                    composable("settings") {
                        SettingsScreen(
                            onLogout = {
                                navController.navigate("login") {
                                    popUpTo("booklist") { inclusive = true }
                                }
                            },
                            repository = repository,
                            onAccountDeleted = {
                                navController.navigate("login") {
                                    popUpTo(0) { inclusive = true }
                                }
                            }
                        )
                    }

                    composable("booklist") {
                        BookListScreen(
                            books = books,
                            onImportClick = {
                                fileImportLauncher.launch(arrayOf("text/*"))
                            },
                            onBookClick = { title ->
                                navController.navigate("reader/$title")
                            },
                            onDeleteBook = { title ->
                                scope.launch { repository.deleteBook(title) }
                            },
                            onRenameBook = { oldTitle, newTitle ->
                                scope.launch {
                                    // 1. 获取重命名后的最终标题（Firestore 中已原子更新）
                                    val finalNewTitle = repository.renameBook(oldTitle, newTitle)

                                    // 2. 迁移本地 SharedPreferences 中的阅读进度
                                    // 这可以防止打开新书时进度为0，从而导致自动保存覆盖了云端的正确进度
                                    val prefs = getSharedPreferences("progress", Context.MODE_PRIVATE)
                                    val oldProgress = prefs.getInt(oldTitle, 0)
                                    if (oldProgress > 0) {
                                        prefs.edit()
                                            .putInt(finalNewTitle, oldProgress)
                                            .remove(oldTitle)
                                            .apply()
                                    }
                                }
                            },
                            onSettingsClick = { navController.navigate("settings") }
                        )
                    }

                    composable("reader/{title}") { backStackEntry ->
                        val title = backStackEntry.arguments?.getString("title") ?: ""
                        ReaderScreen(
                            title = title,
                            repository = repository,
                            onBack = { navController.popBackStack() }
                        )
                    }
                }
            }
        }
    }

    /**
     * Extracts display name from a content URI.
     * Used to show a friendly title when importing a book.
     */
    private fun getFileNameFromUri(resolver: ContentResolver, uri: Uri): String? {
        return try {
            resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val columnIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (columnIndex != -1) {
                        var name = cursor.getString(columnIndex)
                        // Remove file extension for cleaner display
                        name = name.substringBeforeLast(".")
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