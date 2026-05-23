package ravens.scroll.ui.reader

import android.app.Application
import android.util.Log
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import ravens.scroll.BookReaderApp
import ravens.scroll.data.model.Book
import ravens.scroll.data.repository.BookRepository
import ravens.scroll.data.repository.DriveRepository
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

private val android.content.Context.dataStore by preferencesDataStore("reader_prefs")

data class ReaderPrefs(
    val fontSize: Int = 18,
    val lineHeight: Float = 1.6f,
    val fontFamily: String = "lxgw",
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
    val driveFileId: String?,
)

class ReaderViewModel(app: Application) : AndroidViewModel(app) {

    private val repo = BookReaderApp.instance.bookRepository
    private val driveRepo = BookReaderApp.instance.driveRepository

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
            _state.update { it.copy(isLoading = true, error = null, nextBook = null) }
            try {
                var prefs = loadPrefs()
                val result = if (drive) {
                    driveRepo.initClient()
                    val drivePrefs = try { driveRepo.loadPrefs() } catch (_: Exception) { null }
                    if (drivePrefs != null) {
                        prefs = ReaderPrefs(
                            fontSize   = drivePrefs["fontSize"] as? Int ?: prefs.fontSize,
                            lineHeight = (drivePrefs["lineHeight"] as? Double)?.toFloat() ?: prefs.lineHeight,
                            fontFamily = drivePrefs["fontFamily"] as? String ?: prefs.fontFamily,
                            theme      = drivePrefs["theme"] as? String ?: prefs.theme,
                        )
                    }
                    val (s, p) = driveRepo.loadProgress(uri)
                    val text = driveRepo.readFile(uri)
                    Log.d("ReaderVM", "Drive file read: ${text.length} chars")
                    LoadResult(text, uri, s, p, "", null)
                } else {
                    val book = repo.getBook(uri)
                    val text = repo.readFile(uri)
                    Log.d("ReaderVM", "Local file read: ${text.length} chars, uri=$uri")
                    val title = book?.title ?: uri.substringAfterLast('/').removeSuffix(".txt")
                    currentFolderUri = book?.folderUri ?: ""
                    LoadResult(text, title, book?.scrollTop ?: 0, book?.percent ?: 0,
                        book?.folderUri ?: "", book?.driveFileId)
                }

                if (result.text.isEmpty()) {
                    Log.e("ReaderVM", "File content is empty for uri=$uri")
                    _state.update { it.copy(isLoading = false, error = "檔案內容為空或無法讀取。") }
                    return@launch
                }

                val nextBook = if (!drive && result.percent >= 95 && result.folderUri.isNotEmpty()) {
                    repo.getNextTxtInFolder(uri, result.folderUri)
                } else null

                _state.update {
                    it.copy(
                        title = result.title,
                        content = result.text,
                        scrollTop = result.scrollTop,
                        percent = result.percent,
                        prefs = prefs,
                        isLoading = false,
                        nextBook = nextBook,
                    )
                }
                if (!drive) {
                    val existing = repo.getBook(uri)
                    repo.upsertBook(
                        Book(
                            uri = uri,
                            title = result.title,
                            folderUri = result.folderUri,
                            scrollTop = result.scrollTop,
                            percent = result.percent,
                            lastRead = System.currentTimeMillis(),
                            driveFileId = existing?.driveFileId ?: result.driveFileId,
                            pendingSync = existing?.pendingSync ?: false,
                        )
                    )
                }
            } catch (e: SecurityException) {
                _state.update { it.copy(isLoading = false, error = "無法讀取檔案，請確認授權是否有效。") }
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
            if (isDrive) {
                try { driveRepo.savePrefs(prefs.fontSize, prefs.lineHeight, prefs.fontFamily, prefs.theme) }
                catch (_: Exception) {}
            }
        }
    }

    private suspend fun loadPrefs(): ReaderPrefs {
        val p = getApplication<Application>().dataStore.data.first()
        return ReaderPrefs(
            fontSize   = p[KEY_FONT_SIZE]   ?: 17,
            lineHeight = p[KEY_LINE_HEIGHT] ?: 1.6f,
            fontFamily = p[KEY_FONT_FAMILY] ?: "lxgw",
            theme      = p[KEY_THEME]       ?: "dark",
        )
    }
}
