package ravens.scroll.ui.library

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import ravens.scroll.BookReaderApp
import ravens.scroll.data.model.Book
import ravens.scroll.data.repository.LocalSubFolder
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class LibraryUiState(
    val subFolders: List<LocalSubFolderUi> = emptyList(),
    val isLoading: Boolean = false,
)

data class LocalSubFolderUi(
    val name: String,
    val path: String,
    val books: List<Book>,
    val isExpanded: Boolean = false,
)

class LibraryViewModel(app: Application) : AndroidViewModel(app) {

    private val repo = BookReaderApp.instance.bookRepository
    private val expandedSet = MutableStateFlow(setOf<String>())

    private val _rawFolders = MutableStateFlow<List<LocalSubFolder>>(emptyList())

    val state: StateFlow<LibraryUiState> = combine(
        _rawFolders, expandedSet
    ) { folders, expanded ->
        LibraryUiState(
            subFolders = folders.map {
                LocalSubFolderUi(it.name, it.path, it.books, isExpanded = it.path in expanded)
            }
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), LibraryUiState(isLoading = true))

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            val folders = repo.scanLibrary()
            _rawFolders.value = folders
            // Auto-expand single folder
            if (folders.size == 1) expandedSet.update { it + folders[0].path }
        }
    }

    fun toggleFolder(path: String) {
        if (path in expandedSet.value) {
            expandedSet.update { it - path }
        } else {
            expandedSet.update { it + path }
        }
    }

    fun resetFileProgress(uri: String) {
        viewModelScope.launch { repo.resetProgress(uri) }
    }

    fun resetFolderProgress(folderPath: String) {
        viewModelScope.launch { repo.resetFolderProgress(folderPath) }
    }

    fun deleteBook(uri: String) {
        viewModelScope.launch { repo.deleteBook(uri); refresh() }
    }

    fun deleteFolder(folderPath: String) {
        viewModelScope.launch { repo.deleteFolder(folderPath); refresh() }
    }
}
