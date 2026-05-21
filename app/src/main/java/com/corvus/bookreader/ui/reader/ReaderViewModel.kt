package com.corvus.bookreader.ui.reader

import android.app.Application
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.corvus.bookreader.BookReaderApp
import com.corvus.bookreader.data.model.Book
import com.corvus.bookreader.data.repository.BookRepository
import com.corvus.bookreader.data.repository.DriveRepository
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

private val android.content.Context.dataStore by preferencesDataStore("reader_prefs")

data class ReaderPrefs(
    val fontSize: Int = 18,
    val lineHeight: Float = 2.1f,
    val fontFamily: String = "serif",
    val theme: String = "dark",
)

data class ReaderUiState(
    val title: String = "",
    val content: String = "",
    val scrollTop: Int = 0,
    val percent: Int = 0,
    val prefs: ReaderPrefs = ReaderPrefs(),
    val nextBook: Book? = null,
    val isLoading: Boolean = true,
    val error: String? = null,
)

private data class LoadResult(
    val text: String,
    val title: String,
    val scrollTop: Int,
    val percent: Int,
    val folderUri: String,
)

class ReaderViewModel(app: Application) : AndroidViewModel(app) {

    private val repo = BookRepository(
        app,
        BookReaderApp.instance.database.bookDao(),
        BookReaderApp.instance.database.folderDao(),
    )
    private val driveRepo = DriveRepository(app)

    private val _state = MutableStateFlow(ReaderUiState())
    val state: StateFlow<ReaderUiState> = _state.asStateFlow()

    private var currentUri = ""
    private var currentFolderUri = ""
    private var isDrive = false

    private companion object {
        val KEY_FONT_SIZE   = intPreferencesKey("font_size")
        val KEY_LINE_HEIGHT = floatPreferencesKey("line_height")
        val KEY_FONT_FAMILY = stringPreferencesKey("font_family")
        val KEY_THEME       = stringPreferencesKey("theme")
    }

    fun load(uri: String, drive: Boolean) {
        currentUri = uri
        isDrive = drive
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, error = null) }
            try {
                val prefs = loadPrefs()
                val result = if (drive) {
                    driveRepo.initClient()
                    val (s, p) = driveRepo.loadProgress(uri)
                    val text = driveRepo.readFile(uri)
                    LoadResult(text, uri, s, p, "")
                } else {
                    val book = repo.getBook(uri)
                    val text = repo.readFile(uri)
                    val title = book?.title ?: uri.substringAfterLast('/').removeSuffix(".txt")
                    currentFolderUri = book?.folderUri ?: ""
                    LoadResult(text, title, book?.scrollTop ?: 0, book?.percent ?: 0, book?.folderUri ?: "")
                }
                _state.update {
                    it.copy(
                        title = result.title,
                        content = result.text,
                        scrollTop = result.scrollTop,
                        percent = result.percent,
                        prefs = prefs,
                        isLoading = false,
                    )
                }
                if (!drive) {
                    repo.upsertBook(
                        Book(uri = uri, title = result.title, folderUri = result.folderUri,
                            scrollTop = result.scrollTop, percent = result.percent,
                            lastRead = System.currentTimeMillis())
                    )
                }
            } catch (e: Exception) {
                _state.update { it.copy(isLoading = false, error = e.message) }
            }
        }
    }

    fun saveProgress(scrollTop: Int, percent: Int) {
        viewModelScope.launch {
            if (isDrive) {
                driveRepo.saveProgress(currentUri, scrollTop, percent)
            } else {
                repo.saveProgress(currentUri, scrollTop, percent)
            }
            _state.update { it.copy(percent = percent) }

            if (percent >= 95 && currentFolderUri.isNotEmpty()) {
                val next = repo.getNextTxtInFolder(currentUri, currentFolderUri)
                _state.update { it.copy(nextBook = next) }
            }
        }
    }

    fun savePrefs(prefs: ReaderPrefs) {
        _state.update { it.copy(prefs = prefs) }
        viewModelScope.launch {
            getApplication<Application>().dataStore.edit { p ->
                p[KEY_FONT_SIZE]   = prefs.fontSize
                p[KEY_LINE_HEIGHT] = prefs.lineHeight
                p[KEY_FONT_FAMILY] = prefs.fontFamily
                p[KEY_THEME]       = prefs.theme
            }
        }
    }

    private suspend fun loadPrefs(): ReaderPrefs {
        val p = getApplication<Application>().dataStore.data.first()
        return ReaderPrefs(
            fontSize   = p[KEY_FONT_SIZE]   ?: 18,
            lineHeight = p[KEY_LINE_HEIGHT] ?: 2.1f,
            fontFamily = p[KEY_FONT_FAMILY] ?: "serif",
            theme      = p[KEY_THEME]       ?: "dark",
        )
    }
}
