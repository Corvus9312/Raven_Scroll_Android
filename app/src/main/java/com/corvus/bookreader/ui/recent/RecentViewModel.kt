package ravens.scroll.ui.recent

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import ravens.scroll.BookReaderApp
import ravens.scroll.data.model.Book
import kotlinx.coroutines.flow.Flow

class RecentViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = BookReaderApp.instance.bookRepository
    val recentBooks: Flow<List<Book>> = repo.recentBooks
}
