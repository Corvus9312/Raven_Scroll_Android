package ravens.scroll.data.repository

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import androidx.documentfile.provider.DocumentFile
import ravens.scroll.data.db.BookDao
import ravens.scroll.data.db.FolderDao
import ravens.scroll.data.model.Book
import ravens.scroll.data.model.BookFolder
import ravens.scroll.domain.CharsetDetector
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

class BookRepository(
    private val context: Context,
    private val bookDao: BookDao,
    private val folderDao: FolderDao,
) {
    val folders: Flow<List<BookFolder>> = folderDao.flowAll()
    val recentBooks: Flow<List<Book>> = bookDao.flowAll()

    suspend fun addFolder(treeUri: Uri) {
        context.contentResolver.takePersistableUriPermission(
            treeUri,
            android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
        )
        val name = DocumentFile.fromTreeUri(context, treeUri)?.name ?: treeUri.lastPathSegment ?: "書庫"
        val sortOrder = folderDao.count()
        folderDao.insert(BookFolder(treeUri = treeUri.toString(), name = name, sortOrder = sortOrder))
        scanFolder(treeUri.toString())
    }

    suspend fun removeFolder(folder: BookFolder) {
        try {
            context.contentResolver.releasePersistableUriPermission(
                Uri.parse(folder.treeUri),
                android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
        } catch (_: Exception) {}
        bookDao.deleteByFolder(folder.treeUri)
        folderDao.delete(folder)
    }

    suspend fun getBooksInFolder(treeUri: String): List<Book> {
        scanFolder(treeUri)
        return bookDao.getByFolder(treeUri)
    }

    private suspend fun scanFolder(treeUri: String) = withContext(Dispatchers.IO) {
        val docFile = DocumentFile.fromTreeUri(context, Uri.parse(treeUri)) ?: return@withContext
        docFile.listFiles().filter { it.isFile && it.name?.endsWith(".txt", ignoreCase = true) == true }
            .forEach { file ->
                val uri = file.uri.toString()
                if (bookDao.get(uri) == null) {
                    bookDao.upsert(Book(uri = uri, title = file.name?.removeSuffix(".txt") ?: uri, folderUri = treeUri))
                }
            }
    }

    suspend fun readFile(uri: String): String = withContext(Dispatchers.IO) {
        val bytes = context.contentResolver.openInputStream(Uri.parse(uri))?.use { it.readBytes() }
            ?: return@withContext ""
        CharsetDetector.decode(bytes)
    }

    suspend fun saveProgress(uri: String, scrollTop: Int, percent: Int) {
        bookDao.updateProgress(uri, scrollTop, percent, System.currentTimeMillis())
    }

    suspend fun getBook(uri: String): Book? = bookDao.get(uri)

    suspend fun upsertBook(book: Book) = bookDao.upsert(book)

    suspend fun resetProgress(uri: String) = bookDao.resetProgress(uri)

    suspend fun resetFolderProgress(folderUri: String) = bookDao.resetFolderProgress(folderUri)

    suspend fun getNextTxtInFolder(currentUri: String, folderUri: String): Book? {
        val books = bookDao.getByFolder(folderUri).sortedBy { it.title }
        val idx = books.indexOfFirst { it.uri == currentUri }
        return if (idx >= 0 && idx + 1 < books.size) books[idx + 1] else null
    }
}
