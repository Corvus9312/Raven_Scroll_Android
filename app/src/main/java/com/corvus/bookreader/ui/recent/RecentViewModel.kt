package com.corvus.bookreader.ui.recent

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import com.corvus.bookreader.BookReaderApp
import com.corvus.bookreader.data.model.Book
import com.corvus.bookreader.data.repository.BookRepository
import kotlinx.coroutines.flow.Flow

class RecentViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = BookRepository(
        app,
        BookReaderApp.instance.database.bookDao(),
        BookReaderApp.instance.database.folderDao(),
    )
    val recentBooks: Flow<List<Book>> = repo.recentBooks
}
