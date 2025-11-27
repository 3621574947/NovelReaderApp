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
import com.ningyu.novelreader.ui.screens.LoginScreen
import com.ningyu.novelreader.ui.screens.RegisterScreen
import com.ningyu.novelreader.ui.screens.SettingsScreen
import com.google.firebase.auth.FirebaseAuth
import androidx.compose.material3.SnackbarHostState
import com.ningyu.novelreader.ui.screens.SplashScreen

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            NovelReaderTheme {
                val navController = rememberNavController()
                val repository = remember { BookRepository() }
                val scope = rememberCoroutineScope()
                val auth = FirebaseAuth.getInstance()

                var books by remember { mutableStateOf(emptyList<Book>()) }
                LaunchedEffect(repository) {
                    repository.listenToBooks { updatedBooks ->
                        books = updatedBooks
                    }
                }

                val startDestination = "splash"

                val filePicker = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.OpenDocument(),
                    onResult = { uri: Uri? ->
                        if (uri != null) {
                            contentResolver.takePersistableUriPermission(
                                uri,
                                Intent.FLAG_GRANT_READ_URI_PERMISSION
                            )

                            val fileName = getFileNameFromUri(contentResolver, uri)
                            if (fileName != null) {
                                scope.launch {
                                    repository.addBook(fileName, uri.toString())
                                }
                            }
                        }
                    }
                )

                NavHost(navController, startDestination = startDestination) {
                    composable("splash") {
                        SplashScreen(
                            onFinish = { finalRoute ->
                                navController.navigate(finalRoute) {
                                    popUpTo("splash") { inclusive = true }
                                }
                            }
                        )
                    }

                    composable("login") {
                        LoginScreen(
                            onLoginSuccess = { navController.navigate("booklist") {
                                popUpTo("login") { inclusive = true }
                            } },
                            onGoRegister = { navController.navigate("register") }
                        )
                    }

                    composable("register") {
                        RegisterScreen(
                            onRegisterSuccess = { navController.navigate("booklist") {
                                popUpTo("register") { inclusive = true }
                            } },
                            onGoLogin = { navController.popBackStack() }
                        )
                    }

                    composable("settings") {
                        SettingsScreen(
                            onLogout = { navController.navigate("login") {
                                popUpTo("booklist") { inclusive = true }
                            }},
                            onAccountDeleted = { navController.navigate("login") {
                                popUpTo("booklist") { inclusive = true }
                            }}
                        )
                    }

                    composable("booklist") {
                        BookListScreen(
                            books = books.map { it.title },
                            onImportClick = { filePicker.launch(arrayOf("text/*", "text/plain")) },
                            onBookClick = { title ->
                                navController.navigate("reader/$title")
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
                            },
                            onSettingsClick = {
                                navController.navigate("settings")
                            }
                        )
                    }

                    composable("reader/{title}") { backStackEntry ->
                        val title = backStackEntry.arguments?.getString("title").orEmpty()
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