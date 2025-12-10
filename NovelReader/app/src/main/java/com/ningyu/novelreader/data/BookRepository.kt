package com.ningyu.novelreader.data

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.tasks.await

data class Book(
    val title: String = "",
    val localPath: String = "",
    val timestamp: Long = System.currentTimeMillis(),
    val progress: Int = 0
)

class BookRepository {

    private val auth = FirebaseAuth.getInstance()
    private val db = FirebaseFirestore.getInstance()

    private fun getUserId(): String {
        return auth.currentUser?.uid ?: throw IllegalStateException("用户未登录")
    }

    private fun getBooksRef() = db.collection("users").document(getUserId()).collection("books")

    suspend fun addBook(titleRaw: String, localPath: String) {
        val title = sanitizeTitle(titleRaw)
        val docRef = getBooksRef().document(title)
        val existing = docRef.get().await()
        val finalTitle = if (existing.exists()) {
            "$title-${System.currentTimeMillis()}"
        } else title

        val book = Book(
            title = finalTitle,
            localPath = localPath,
            timestamp = System.currentTimeMillis()
        )
        getBooksRef().document(finalTitle).set(book).await()
    }

    suspend fun deleteBook(titleRaw: String) {
        val title = sanitizeTitle(titleRaw)
        getBooksRef().document(title).delete().await()
    }

    suspend fun renameBook(oldTitleRaw: String, newTitleRaw: String) {
        val oldTitle = sanitizeTitle(oldTitleRaw)
        val newTitleCandidate = sanitizeTitle(newTitleRaw)
        if (oldTitle == newTitleCandidate) return

        val oldDoc = getBooksRef().document(oldTitle).get().await().toObject(Book::class.java) ?: return

        val newDocRef = getBooksRef().document(newTitleCandidate)
        val newExists = newDocRef.get().await().exists()
        val finalNewTitle =
            if (newExists) "${newTitleCandidate}-${System.currentTimeMillis()}" else newTitleCandidate

        val newBook = oldDoc.copy(title = finalNewTitle, timestamp = System.currentTimeMillis())
        getBooksRef().document(finalNewTitle).set(newBook).await()
        getBooksRef().document(oldTitle).delete().await()
    }

    suspend fun saveProgress(titleRaw: String, page: Int) {
        val title = sanitizeTitle(titleRaw)
        val data = mapOf("progress" to page)
        getBooksRef().document(title).set(data, SetOptions.merge()).await()
    }

    suspend fun getProgress(titleRaw: String): Int {
        val title = sanitizeTitle(titleRaw)
        val doc = getBooksRef().document(title).get().await()
        return doc.getLong("progress")?.toInt() ?: 0
    }

    suspend fun getBookByTitle(titleRaw: String): Book? {
        val title = sanitizeTitle(titleRaw)
        val doc = getBooksRef().document(title).get().await()
        return doc.toObject(Book::class.java)
    }

    fun listenToBooks(onChange: (List<Book>) -> Unit) {
        getBooksRef().addSnapshotListener { snapshot, error ->
            if (error != null) return@addSnapshotListener
            val books = snapshot?.documents?.mapNotNull { it.toObject(Book::class.java) }
                ?.sortedByDescending { it.timestamp } ?: emptyList()
            onChange(books)
        }
    }

    private fun sanitizeTitle(raw: String): String {
        return raw.trim()
            .replace(Regex("[\\r\\n]+"), " ")
            .replace(Regex("[/\\\\#?\\[\\]]"), "_")
    }

    suspend fun getAllBooks(): List<Book> {
        return getBooksRef().get().await().documents.mapNotNull { it.toObject(Book::class.java) }
    }

    suspend fun clearAllProgress() {
        val books = getAllBooks()
        for (book in books) {
            saveProgress(book.title, 0)
        }
    }

}