package ravens.scroll.data.repository

import android.content.Context
import ravens.scroll.data.db.BookDao
import ravens.scroll.data.db.DownloadedDriveFileDao
import ravens.scroll.data.model.Book
import ravens.scroll.data.model.DownloadedDriveFile
import ravens.scroll.domain.CharsetDetector
import ravens.scroll.domain.isBookFile
import ravens.scroll.domain.stripBookExt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.io.File

data class LocalSubFolder(
    val name: String,
    val path: String,
    val books: List<Book>,
    val isExpanded: Boolean = false,
)

class BookRepository(
    private val context: Context,
    private val bookDao: BookDao,
    private val downloadedDao: DownloadedDriveFileDao,
    private val driveRepository: DriveRepository,
) {
    val recentBooks: Flow<List<Book>> = bookDao.flowAll()

    fun getRavensScrollDir(): File? {
        val ext = context.getExternalFilesDir(null) ?: return null
        return File(ext, "Raven's Scroll").also { if (!it.exists()) it.mkdirs() }
    }

    suspend fun scanLibrary(): List<LocalSubFolder> = withContext(Dispatchers.IO) {
        val root = getRavensScrollDir() ?: return@withContext emptyList()
        val result = mutableListOf<LocalSubFolder>()
        val rootTxts = mutableListOf<Book>()

        root.listFiles()?.sortedBy { it.name }?.forEach { entry ->
            when {
                entry.isDirectory -> {
                    val books = entry.listFiles()
                        ?.filter { it.isFile && isBookFile(it.name) }
                        ?.sortedBy { it.name }
                        ?.mapNotNull { f -> upsertAndGet(f, entry.absolutePath) }
                        ?: emptyList()
                    if (books.isNotEmpty()) {
                        result.add(LocalSubFolder(entry.name, entry.absolutePath, books))
                    }
                }
                entry.isFile && isBookFile(entry.name) -> {
                    upsertAndGet(entry, root.absolutePath)?.let { rootTxts.add(it) }
                }
            }
        }

        if (rootTxts.isNotEmpty()) {
            result.add(0, LocalSubFolder("Raven's Scroll", root.absolutePath, rootTxts))
        }
        result
    }

    private suspend fun upsertAndGet(file: File, folderPath: String): Book? {
        val uri = file.absolutePath
        val existing = bookDao.get(uri)
        if (existing != null) return existing
        val book = Book(
            uri = uri,
            title = stripBookExt(file.name),
            folderUri = folderPath,
        )
        bookDao.upsert(book)
        return book
    }

    suspend fun readFile(uri: String): String = withContext(Dispatchers.IO) {
        val bytes = if (uri.startsWith("/")) {
            File(uri).takeIf { it.exists() }?.readBytes()
        } else {
            context.contentResolver.openInputStream(android.net.Uri.parse(uri))?.use { it.readBytes() }
        } ?: return@withContext ""
        CharsetDetector.decode(bytes)
    }

    suspend fun readBytes(uri: String): ByteArray = withContext(Dispatchers.IO) {
        if (uri.startsWith("/")) {
            File(uri).takeIf { it.exists() }?.readBytes()
        } else {
            context.contentResolver.openInputStream(android.net.Uri.parse(uri))?.use { it.readBytes() }
        } ?: ByteArray(0)
    }

    suspend fun saveProgress(uri: String, scrollTop: Int, percent: Int) {
        bookDao.updateProgress(uri, scrollTop, percent, System.currentTimeMillis())
        val book = bookDao.get(uri) ?: return
        val driveFileId = book.driveFileId ?: return
        try {
            driveRepository.initClient()
            driveRepository.saveProgress(driveFileId, scrollTop, percent)
            bookDao.setPendingSync(uri, false)
        } catch (_: Exception) {
            bookDao.setPendingSync(uri, true)
        }
    }

    suspend fun getBook(uri: String): Book? = bookDao.get(uri)

    suspend fun upsertBook(book: Book) = bookDao.upsert(book)

    suspend fun resetProgress(uri: String) = bookDao.resetProgress(uri)

    suspend fun resetFolderProgress(folderUri: String) = bookDao.resetFolderProgress(folderUri)

    /** Delete a book: local file, DB row, and any downloaded-from-Drive marker. */
    suspend fun deleteBook(uri: String) = withContext(Dispatchers.IO) {
        val book = bookDao.get(uri)
        try { if (uri.startsWith("/")) File(uri).takeIf { it.exists() }?.delete() } catch (_: Exception) {}
        book?.driveFileId?.let { downloadedDao.delete(it) }
        bookDao.delete(uri)
    }

    /** Delete every book in a folder (files + DB rows + Drive markers), then the now-empty folder. */
    suspend fun deleteFolder(folderUri: String) = withContext(Dispatchers.IO) {
        val books = bookDao.getByFolder(folderUri)
        for (b in books) {
            try { if (b.uri.startsWith("/")) File(b.uri).takeIf { it.exists() }?.delete() } catch (_: Exception) {}
            b.driveFileId?.let { downloadedDao.delete(it) }
        }
        bookDao.deleteByFolder(folderUri)
        try {
            val dir = File(folderUri)
            // never remove the library root itself
            if (dir.isDirectory && dir.absolutePath != getRavensScrollDir()?.absolutePath) dir.delete()
        } catch (_: Exception) {}
    }

    suspend fun resetProgressByDriveFileId(driveFileId: String) {
        val book = bookDao.getByDriveFileId(driveFileId) ?: return
        bookDao.resetProgress(book.uri)
    }

    suspend fun getPendingBooks(): List<Book> = bookDao.getPendingSync()

    suspend fun getAllDownloadedBooks(): List<Book> = bookDao.getAllWithDriveId()

    suspend fun clearPendingSync(uri: String) = bookDao.setPendingSync(uri, false)

    suspend fun applyDriveProgress(uri: String, scrollTop: Int, percent: Int) {
        bookDao.updateProgress(uri, scrollTop, percent, System.currentTimeMillis())
        bookDao.setPendingSync(uri, false)
    }

    suspend fun getNextTxtInFolder(currentUri: String, folderUri: String): Book? {
        val books = bookDao.getByFolder(folderUri).sortedBy { it.title }
        val idx = books.indexOfFirst { it.uri == currentUri }
        return if (idx >= 0 && idx + 1 < books.size) books[idx + 1] else null
    }

    suspend fun recordDownload(entry: DownloadedDriveFile) = downloadedDao.upsert(entry)

    fun flowDownloadedIds(): Flow<List<String>> = downloadedDao.flowAllIds()
}
