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
import ravens.scroll.domain.CharsetDetector
import ravens.scroll.domain.EpubChapter
import ravens.scroll.domain.EpubParser
import ravens.scroll.domain.looksLikeZip
import ravens.scroll.domain.stripBookExt
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
    val mode: String = "txt",            // "txt" | "epub"
    val content: String = "",            // txt body (empty for epub)
    val html: String = "",               // epub body HTML (empty for txt)
    val chapters: List<EpubChapter> = emptyList(),
    val scrollTop: Int = 0,
    val percent: Int = 0,
    val prefs: ReaderPrefs = ReaderPrefs(),
    val nextBook: Book? = null,
    val isLoading: Boolean = true,
    val error: String? = null,
    val loadToken: Long = 0L,            // bumped each successful load to trigger injection
)

private data class LoadResult(
    val mode: String,
    val text: String,
    val html: String,
    val chapters: List<EpubChapter>,
    val title: String,
    val scrollTop: Int,
    val percent: Int,
    val folderUri: String,
    val driveFileId: String?,
)

private data class Parsed(
    val mode: String,
    val text: String,
    val html: String,
    val chapters: List<EpubChapter>,
    val epubTitle: String,
)

class ReaderViewModel(app: Application) : AndroidViewModel(app) {

    private val repo = BookReaderApp.instance.bookRepository
    private val driveRepo = BookReaderApp.instance.driveRepository

    private val _state = MutableStateFlow(ReaderUiState())
    val state: StateFlow<ReaderUiState> = _state.asStateFlow()

    private var currentUri = ""
    private var currentFolderUri = ""
    private var isDrive = false
    private var loadCounter = 0L

    /** Decide format from the bytes themselves (EPUB/ZIP magic vs plain text). */
    private fun parseContent(bytes: ByteArray): Parsed {
        if (looksLikeZip(bytes)) {
            return try {
                val book = EpubParser.parse(bytes)
                Parsed("epub", "", book.html, book.chapters, book.title)
            } catch (e: Exception) {
                Parsed("txt", "無法開啟此 EPUB：${e.message}", "", emptyList(), "")
            }
        }
        return Parsed("txt", CharsetDetector.decode(bytes), "", emptyList(), "")
    }

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
                    val bytes = driveRepo.downloadFileBytes(uri)
                    val parsed = parseContent(bytes)
                    Log.d("ReaderVM", "Drive file read: ${bytes.size} bytes, mode=${parsed.mode}")
                    val title = if (parsed.mode == "epub" && parsed.epubTitle.isNotEmpty()) parsed.epubTitle else uri
                    LoadResult(parsed.mode, parsed.text, parsed.html, parsed.chapters, title, s, p, "", null)
                } else {
                    val book = repo.getBook(uri)
                    val bytes = repo.readBytes(uri)
                    val parsed = parseContent(bytes)
                    Log.d("ReaderVM", "Local file read: ${bytes.size} bytes, mode=${parsed.mode}, uri=$uri")
                    val title = book?.title ?: stripBookExt(uri.substringAfterLast('/'))
                    currentFolderUri = book?.folderUri ?: ""
                    LoadResult(parsed.mode, parsed.text, parsed.html, parsed.chapters, title,
                        book?.scrollTop ?: 0, book?.percent ?: 0, book?.folderUri ?: "", book?.driveFileId)
                }

                val hasContent = if (result.mode == "epub") result.html.isNotEmpty() else result.text.isNotEmpty()
                if (!hasContent) {
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
                        mode = result.mode,
                        content = result.text,
                        html = result.html,
                        chapters = result.chapters,
                        scrollTop = result.scrollTop,
                        percent = result.percent,
                        prefs = prefs,
                        isLoading = false,
                        nextBook = nextBook,
                        loadToken = ++loadCounter,
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
