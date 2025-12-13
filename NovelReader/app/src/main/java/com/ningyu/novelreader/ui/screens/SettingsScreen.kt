package com.ningyu.novelreader.ui.screens

import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.edit
import androidx.core.net.toUri
import com.google.firebase.auth.FirebaseAuth
import com.ningyu.novelreader.data.BookRepository
import com.ningyu.novelreader.ui.components.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

private fun saveAvatarToInternalStorage(context: Context, uri: Uri): Uri? = try {
    val input = context.contentResolver.openInputStream(uri) ?: return null
    val file = File(context.filesDir, "avatar.jpg")
    file.outputStream().use { output -> input.copyTo(output) }
    context.getSharedPreferences("user_prefs", Context.MODE_PRIVATE).edit {
        putString("avatar_path", file.absolutePath)
    }
    file.toUri()
} catch (e: Exception) {
    e.printStackTrace()
    null
}

private fun loadAvatarBitmap(context: Context, uri: Uri) = try {
    context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it) }
} catch (e: Exception) {
    e.printStackTrace()
    null
}

/**
 * Settings screen – avatar, password reset, progress clearing, logout, account deletion
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onLogout: () -> Unit,
    repository: BookRepository,
    onAccountDeleted: () -> Unit
) {
    val auth = FirebaseAuth.getInstance()
    val user = auth.currentUser
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    var avatarBitmap by remember { mutableStateOf<android.graphics.Bitmap?>(null) }
    var avatarUri by remember { mutableStateOf<Uri?>(null) }
    var isLoadingAvatar by remember { mutableStateOf(false) }

    var showDeleteAllBooksDialog by remember { mutableStateOf(false) }
    var showDeleteAccountDialog by remember { mutableStateOf(false) }
    var snackbarMessage by remember { mutableStateOf<String?>(null) }

    // Load saved avatar on first composition
    LaunchedEffect(Unit) {
        val path = context.getSharedPreferences("user_prefs", 0).getString("avatar_path", null)
        path?.let { File(it).takeIf { it.exists() }?.toUri() }?.let { avatarUri = it }
    }

    LaunchedEffect(avatarUri) {
        avatarUri?.let { uri ->
            isLoadingAvatar = true
            avatarBitmap = withContext(Dispatchers.IO) { loadAvatarBitmap(context, uri) }
            isLoadingAvatar = false
        } ?: run { avatarBitmap = null }
    }

    LaunchedEffect(snackbarMessage) {
        snackbarMessage?.let {
            snackbarHostState.showSnackbar(it)
            snackbarMessage = null
        }
    }

    val imagePicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let {
            scope.launch(Dispatchers.IO) {
                val saved = saveAvatarToInternalStorage(context, uri)
                if (saved != null) {
                    avatarUri = saved
                    snackbarMessage = "Avatar updated successfully"
                } else {
                    snackbarMessage = "Failed to save avatar"
                }
            }
        }
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text("Settings") }) },
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            // Avatar + email row
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(80.dp)
                        .clip(CircleShape)
                        .clickable(enabled = !isLoadingAvatar) { imagePicker.launch("image/*") },
                    contentAlignment = Alignment.Center
                ) {
                    when {
                        isLoadingAvatar -> CircularProgressIndicator(Modifier.fillMaxSize())
                        avatarBitmap != null -> Image(
                            bitmap = avatarBitmap!!.asImageBitmap(),
                            contentDescription = "User avatar",
                            modifier = Modifier.fillMaxSize()
                        )
                        else -> Surface(color = MaterialTheme.colorScheme.primaryContainer) {
                            Icon(Icons.Default.Person, contentDescription = null, modifier = Modifier.padding(16.dp))
                        }
                    }
                }
                Spacer(Modifier.width(16.dp))
                Text(user?.email ?: "Guest", style = MaterialTheme.typography.bodyLarge)
            }

            HorizontalDivider()

            AppButton(onClick = {
                user?.email?.let {
                    auth.sendPasswordResetEmail(it)
                    snackbarMessage = "Password reset email sent"
                } ?: run { snackbarMessage = "No email found" }
            }, Modifier.fillMaxWidth()) { Text("Reset Password (via Email)") }

            AppButton(onClick = {
                context.getSharedPreferences("progress", 0).edit { clear() }
                scope.launch { repository.clearAllProgress() }
                snackbarMessage = "All reading progress cleared"
            }, Modifier.fillMaxWidth()) { Text("Clear Reading Progress") }

            AppDestructiveButton(onClick = { showDeleteAllBooksDialog = true }, Modifier.fillMaxWidth()) {
                Text("Delete All Local Books")
            }

            AppDestructiveButton(onClick = {
                auth.signOut()
                onLogout()
            }, Modifier.fillMaxWidth()) { Text("Log Out") }

            AppDestructiveOutlinedButton(onClick = { showDeleteAccountDialog = true }, Modifier.fillMaxWidth()) {
                Text("Delete Account")
            }

            // Delete all books confirmation dialog
            if (showDeleteAllBooksDialog) {
                AlertDialog(
                    onDismissRequest = { showDeleteAllBooksDialog = false },
                    title = { Text("Delete all books?") },
                    text = { Text("This will remove all books and release file permissions. Cannot be undone.") },
                    confirmButton = {
                        TextButton(onClick = {
                            scope.launch(Dispatchers.IO) {
                                val books = repository.getAllBooks()
                                val prefs = context.getSharedPreferences("progress", 0)
                                prefs.edit { clear() }
                                books.forEach { book ->
                                    try {
                                        context.contentResolver.releasePersistableUriPermission(
                                            book.localPath.toUri(),
                                            Intent.FLAG_GRANT_READ_URI_PERMISSION
                                        )
                                    } catch (_: Exception) {}
                                    repository.deleteBook(book.title)
                                }
                                snackbarMessage = "All books deleted"
                            }
                            showDeleteAllBooksDialog = false
                        }) { Text("Confirm") }
                    },
                    dismissButton = { TextButton(onClick = { showDeleteAllBooksDialog = false }) { Text("Cancel") } }
                )
            }

            // Delete account confirmation dialog
            if (showDeleteAccountDialog) {
                AlertDialog(
                    onDismissRequest = { showDeleteAccountDialog = false },
                    title = { Text("Delete Account?") },
                    text = { Text("This action is permanent and cannot be undone.") },
                    confirmButton = {
                        TextButton(onClick = {
                            user?.delete()?.addOnCompleteListener {
                                if (it.isSuccessful) onAccountDeleted() else snackbarMessage = "Delete failed. Please re-login."
                            }
                            showDeleteAccountDialog = false
                        }) { Text("Confirm Delete") }
                    },
                    dismissButton = { TextButton(onClick = { showDeleteAccountDialog = false }) { Text("Cancel") } }
                )
            }
        }
    }
}