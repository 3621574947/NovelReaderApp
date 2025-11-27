package com.ningyu.novelreader.data

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

    private val db = FirebaseFirestore.getInstance()
    private val booksRef = db.collection("books")

    suspend fun addBook(titleRaw: String, localPath: String) {
        val title = sanitizeTitle(titleRaw)
        val docRef = booksRef.document(title)
        val existing = docRef.get().await()
        val finalTitle = if (existing.exists()) {
            "$title-${System.currentTimeMillis()}"
        } else title

        val book = Book(
            title = finalTitle,
            localPath = localPath,
            timestamp = System.currentTimeMillis()
        )
        booksRef.document(finalTitle).set(book).await()
    }

    suspend fun deleteBook(titleRaw: String) {
        val title = sanitizeTitle(titleRaw)
        booksRef.document(title).delete().await()
    }

    suspend fun renameBook(oldTitleRaw: String, newTitleRaw: String) {
        val oldTitle = sanitizeTitle(oldTitleRaw)
        val newTitleCandidate = sanitizeTitle(newTitleRaw)
        if (oldTitle == newTitleCandidate) return

        val oldDoc = booksRef.document(oldTitle).get().await().toObject(Book::class.java) ?: return

        val newDocRef = booksRef.document(newTitleCandidate)
        val newExists = newDocRef.get().await().exists()
        val finalNewTitle =
            if (newExists) "${newTitleCandidate}-${System.currentTimeMillis()}" else newTitleCandidate

        val newBook = oldDoc.copy(title = finalNewTitle, timestamp = System.currentTimeMillis())
        booksRef.document(finalNewTitle).set(newBook).await()
        booksRef.document(oldTitle).delete().await()
    }

    suspend fun saveProgress(titleRaw: String, page: Int) {
        val title = sanitizeTitle(titleRaw)
        val data = mapOf("progress" to page)
        booksRef.document(title).set(data, SetOptions.merge()).await()
    }

    suspend fun getProgress(titleRaw: String): Int {
        val title = sanitizeTitle(titleRaw)
        val doc = booksRef.document(title).get().await()
        return doc.getLong("progress")?.toInt() ?: 0
    }

    suspend fun getBookByTitle(titleRaw: String): Book? {
        val title = sanitizeTitle(titleRaw)
        val doc = booksRef.document(title).get().await()
        return doc.toObject(Book::class.java)
    }

    fun listenToBooks(onChange: (List<Book>) -> Unit) {
        booksRef.addSnapshotListener { snapshot, error ->
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
}
