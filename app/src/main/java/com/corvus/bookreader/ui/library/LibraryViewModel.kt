package com.corvus.bookreader.ui.library

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.corvus.bookreader.BookReaderApp
import com.corvus.bookreader.data.model.Book
import com.corvus.bookreader.data.model.BookFolder
import com.corvus.bookreader.data.repository.BookRepository
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class FolderWithBooks(
    val folder: BookFolder,
    val books: List<Book> = emptyList(),
    val isExpanded: Boolean = false,
)

data class LibraryUiState(
    val folders: List<FolderWithBooks> = emptyList(),
    val isLoading: Boolean = false,
)

class LibraryViewModel(app: Application) : AndroidViewModel(app) {

    private val repo = BookRepository(
        app,
        BookReaderApp.instance.database.bookDao(),
        BookReaderApp.instance.database.folderDao(),
    )

    private val expandedSet = MutableStateFlow(setOf<String>())

    val state: StateFlow<LibraryUiState> = combine(
        repo.folders, expandedSet
    ) { folders, expanded ->
        LibraryUiState(folders = folders.map { FolderWithBooks(it, isExpanded = it.treeUri in expanded) })
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), LibraryUiState())

    fun addFolder(treeUri: Uri) {
        viewModelScope.launch { repo.addFolder(treeUri) }
    }

    fun removeFolder(folder: BookFolder) {
        viewModelScope.launch { repo.removeFolder(folder) }
    }

    fun toggleFolder(folder: BookFolder) {
        val uri = folder.treeUri
        if (uri in expandedSet.value) {
            expandedSet.update { it - uri }
        } else {
            expandedSet.update { it + uri }
            viewModelScope.launch {
                val books = repo.getBooksInFolder(uri)
                // Force recompose by touching expandedSet again
                expandedSet.update { it.toSet() }
                _folderBooks.update { map -> map + (uri to books) }
            }
        }
    }

    private val _folderBooks = MutableStateFlow<Map<String, List<Book>>>(emptyMap())
    val folderBooks: StateFlow<Map<String, List<Book>>> = _folderBooks.asStateFlow()

    fun resetFileProgress(uri: String) {
        viewModelScope.launch { repo.resetProgress(uri) }
    }

    fun resetFolderProgress(folderUri: String) {
        viewModelScope.launch { repo.resetFolderProgress(folderUri) }
    }

    fun refreshFolder(treeUri: String) {
        viewModelScope.launch {
            val books = repo.getBooksInFolder(treeUri)
            _folderBooks.update { it + (treeUri to books) }
        }
    }
}
