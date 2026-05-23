package ravens.scroll.ui.recent

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import ravens.scroll.BookReaderApp
import ravens.scroll.data.model.Book
import ravens.scroll.data.repository.BookRepository
import kotlinx.coroutines.flow.Flow

class RecentViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = BookRepository(
        app,
        BookReaderApp.instance.database.bookDao(),
        BookReaderApp.instance.database.folderDao(),
    )
    val recentBooks: Flow<List<Book>> = repo.recentBooks
}
