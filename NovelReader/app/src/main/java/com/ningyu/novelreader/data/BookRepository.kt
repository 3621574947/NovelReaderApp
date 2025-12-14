package com.ningyu.novelreader.data

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.tasks.await

/**
 * Represents a book in the user's personal library
 */
data class Book(
    val title: String = "",
    val localPath: String = "",        // Content URI as string (e.g., content://...)
    val timestamp: Long = System.currentTimeMillis(),
    val progress: Int = 0              // Current reading page (0-based index)
)

/**
 * Repository responsible for all book-related Firestore operations.
 * All data is scoped to the currently authenticated user.
 */
class BookRepository {

    private val auth = FirebaseAuth.getInstance()
    private val db = FirebaseFirestore.getInstance()

    /** Returns current user's UID or throws if not authenticated */
    private fun getUserId(): String {
        return auth.currentUser?.uid ?: throw IllegalStateException("User not logged in")
    }

    /** Reference to the user's private books collection */
    private fun getBooksRef() = db.collection("users").document(getUserId()).collection("books")

    /**
     * Adds a new book. If a book with the same title exists,
     * appends a timestamp to avoid document ID collision.
     */
    suspend fun addBook(rawTitle: String, localPath: String) {
        if (auth.currentUser == null) return

        val title = sanitizeTitle(rawTitle)
        val docRef = getBooksRef().document(title)

        val exists = docRef.get().await().exists()
        val finalTitle = if (exists) "$title-${System.currentTimeMillis()}" else title

        val book = Book(
            title = finalTitle,
            localPath = localPath,
            timestamp = System.currentTimeMillis()
        )
        getBooksRef().document(finalTitle).set(book).await()
    }

    /** Deletes a book document from Firestore */
    suspend fun deleteBook(rawTitle: String) {
        if (auth.currentUser == null) return
        val title = sanitizeTitle(rawTitle)
        getBooksRef().document(title).delete().await()
    }

    /**
     * Renames a book by creating a new document and deleting the old one atomically.
     * Uses WriteBatch to ensure the old book is deleted if and only if the new one is created.
     * Returns the final new title so the UI/Local storage can update references.
     */
    suspend fun renameBook(oldTitleRaw: String, newTitleRaw: String): String {
        if (auth.currentUser == null) return oldTitleRaw

        val oldTitle = sanitizeTitle(oldTitleRaw)
        val candidateTitle = sanitizeTitle(newTitleRaw)

        if (oldTitle == candidateTitle) return oldTitle

        val oldBookRef = getBooksRef().document(oldTitle)
        val oldBookSnapshot = oldBookRef.get().await()
        val oldBook = oldBookSnapshot.toObject(Book::class.java) ?: return oldTitle

        val finalNewTitle = if (getBooksRef().document(candidateTitle).get().await().exists()) {
            "$candidateTitle-${System.currentTimeMillis()}"
        } else candidateTitle

        val newBook = oldBook.copy(title = finalNewTitle, timestamp = System.currentTimeMillis())

        val batch = db.batch()
        batch.set(getBooksRef().document(finalNewTitle), newBook)
        batch.delete(oldBookRef)
        batch.commit().await()

        return finalNewTitle
    }

    /** Saves reading progress (page number) using merge to preserve other fields */
    suspend fun saveProgress(rawTitle: String, page: Int) {
        if (auth.currentUser == null) return
        val title = sanitizeTitle(rawTitle)
        getBooksRef().document(title).set(mapOf("progress" to page), SetOptions.merge()).await()
    }

    /** Retrieves saved progress from cloud, defaults to 0 */
    suspend fun getProgress(rawTitle: String): Int {
        if (auth.currentUser == null) return 0
        val title = sanitizeTitle(rawTitle)
        val doc = getBooksRef().document(title).get().await()
        return doc.getLong("progress")?.toInt() ?: 0
    }

    /** Fetches full Book object by sanitized title */
    suspend fun getBookByTitle(rawTitle: String): Book? {
        if (auth.currentUser == null) return null
        val title = sanitizeTitle(rawTitle)
        return getBooksRef().document(title).get().await().toObject(Book::class.java)
    }

    /**
     * Sets up real-time listener for the entire book list.
     * Returns a ListenerRegistration? so it can be removed manually.
     * Safe to call even if user is not logged in (returns null).
     */
    fun listenToBooks(onChange: (List<Book>) -> Unit): ListenerRegistration? {
        if (auth.currentUser == null) {
            onChange(emptyList())
            return null
        }

        return getBooksRef().addSnapshotListener { snapshot, error ->
            if (error != null) return@addSnapshotListener
            val books = snapshot?.documents
                ?.mapNotNull { it.toObject(Book::class.java) }
                ?.sortedByDescending { it.timestamp } ?: emptyList()
            onChange(books)
        }
    }

    /** Makes string safe for use as Firestore document ID */
    private fun sanitizeTitle(raw: String): String = raw.trim()
        .replace(Regex("[\\r\\n]+"), " ")
        .replace(Regex("[/\\\\#?\\[\\]]"), "_")

    /** Fetches all books once (used for batch operations) */
    suspend fun getAllBooks(): List<Book> {
        if (auth.currentUser == null) return emptyList()
        return getBooksRef().get().await().documents
            .mapNotNull { it.toObject(Book::class.java) }
    }

    /** Resets progress for all books to page 0 */
    suspend fun clearAllProgress() {
        getAllBooks().forEach { saveProgress(it.title, 0) }
    }
}