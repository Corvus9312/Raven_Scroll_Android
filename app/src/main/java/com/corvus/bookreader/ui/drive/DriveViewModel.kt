package ravens.scroll.ui.drive

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import ravens.scroll.BookReaderApp
import ravens.scroll.data.model.DriveItem
import ravens.scroll.data.repository.DriveRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class DriveUiState(
    val isSignedIn: Boolean = false,
    val items: List<DriveItem> = emptyList(),
    val stack: List<Pair<String, String>> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null,
    val progressMap: Map<String, Pair<Int, Int>> = emptyMap(),
    val fileParentMap: Map<String, String> = emptyMap(),
    val loadedFolderIds: Set<String> = emptySet(),
    /** 使用者展開的資料夾 ID */
    val expandedFolderIds: Set<String> = emptySet(),
    /** folderId → 該資料夾內容（null 表示尚未載入）*/
    val folderFiles: Map<String, List<DriveItem>> = emptyMap(),
)

class DriveViewModel(app: Application) : AndroidViewModel(app) {

    private val repo = BookReaderApp.instance.driveRepository
    private val _state = MutableStateFlow(DriveUiState())
    val state: StateFlow<DriveUiState> = _state.asStateFlow()

    init {
        val signedIn = repo.isSignedIn()
        _state.update { it.copy(isSignedIn = signedIn) }
        if (signedIn) {
            repo.initClient()
            val pinned = loadPinnedFolder()
            if (pinned != null) {
                _state.update { it.copy(stack = listOf("root" to "Google Drive", pinned.first to pinned.second)) }
                loadFolder(pinned.first, pinned.second)
            } else {
                loadFolder("root", "Google Drive")
            }
            viewModelScope.launch { loadProgressMap() }
        }
    }

    fun onSignedIn() {
        repo.initClient()
        _state.update { it.copy(isSignedIn = true) }
        val pinned = loadPinnedFolder()
        if (pinned != null) {
            _state.update { it.copy(stack = listOf("root" to "Google Drive", pinned.first to pinned.second)) }
            loadFolder(pinned.first, pinned.second)
        } else {
            loadFolder("root", "Google Drive")
        }
        viewModelScope.launch { loadProgressMap() }
    }

    fun signOut() {
        repo.signOut()
        _state.update { DriveUiState(isSignedIn = false) }
    }

    /** 展開/收合資料夾；若尚未載入則背景抓取內容 */
    fun toggleFolder(folderId: String) {
        val expanded = _state.value.expandedFolderIds
        if (folderId in expanded) {
            _state.update { it.copy(expandedFolderIds = expanded - folderId) }
        } else {
            _state.update { it.copy(expandedFolderIds = expanded + folderId) }
            if (folderId !in _state.value.folderFiles) {
                loadFolderContent(folderId)
            }
        }
    }

    /** 進入資料夾並設為下次預設起點 */
    fun enterAndPin(id: String, name: String) {
        setPinnedFolder(id, name)
        openFolder(id, name)
    }

    fun setPinnedFolder(id: String, name: String) {
        getApplication<Application>()
            .getSharedPreferences("drive_prefs", Context.MODE_PRIVATE)
            .edit()
            .putString("pinned_folder_id", id)
            .putString("pinned_folder_name", name)
            .apply()
    }

    private fun loadPinnedFolder(): Pair<String, String>? {
        val prefs = getApplication<Application>()
            .getSharedPreferences("drive_prefs", Context.MODE_PRIVATE)
        val id = prefs.getString("pinned_folder_id", null) ?: return null
        val name = prefs.getString("pinned_folder_name", null) ?: return null
        return id to name
    }

    fun openFolder(id: String, name: String) {
        val current = _state.value.stack
        _state.update { it.copy(stack = current + (id to name)) }
        loadFolder(id, name)
    }

    fun navigateUp() {
        val stack = _state.value.stack.toMutableList()
        if (stack.size <= 1) return
        stack.removeLast()
        val (id, name) = stack.last()
        _state.update { it.copy(stack = stack) }
        loadFolder(id, name)
    }

    fun navigateRoot() {
        _state.update { it.copy(stack = emptyList()) }
        loadFolder("root", "Google Drive")
    }

    private fun loadFolder(folderId: String, name: String) {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, error = null, expandedFolderIds = emptySet()) }
            try {
                val items = repo.listFolder(folderId)
                val newMappings = items.filter { !it.isFolder }.associate { it.id to folderId }
                _state.update {
                    it.copy(
                        items = items,
                        isLoading = false,
                        fileParentMap = it.fileParentMap + newMappings,
                        loadedFolderIds = it.loadedFolderIds + folderId,
                    )
                }
                // 背景預載子資料夾，讓進度標籤可以顯示
                val alreadyLoaded = _state.value.loadedFolderIds
                items.filter { it.isFolder && it.id !in alreadyLoaded }
                    .forEach { folder -> loadFolderContent(folder.id) }
            } catch (e: Exception) {
                _state.update { it.copy(isLoading = false, error = e.message) }
            }
        }
    }

    /** 載入資料夾內容（用於展開顯示 + 背景進度統計） */
    private fun loadFolderContent(folderId: String) {
        viewModelScope.launch {
            try {
                val files = repo.listFolder(folderId)
                val newMappings = files.filter { !it.isFolder }.associate { it.id to folderId }
                _state.update {
                    it.copy(
                        folderFiles = it.folderFiles + (folderId to files),
                        fileParentMap = it.fileParentMap + newMappings,
                        loadedFolderIds = it.loadedFolderIds + folderId,
                    )
                }
            } catch (_: Exception) {
                _state.update { it.copy(loadedFolderIds = it.loadedFolderIds + folderId) }
            }
        }
    }

    fun refresh() {
        val id = _state.value.stack.lastOrNull()?.first ?: "root"
        loadFolder(id, "")
        viewModelScope.launch {
            repo.invalidateProgressCache()
            loadProgressMap()
        }
    }

    fun refreshProgress() {
        viewModelScope.launch {
            repo.invalidateProgressCache()
            loadProgressMap()
        }
    }

    private suspend fun loadProgressMap() {
        try {
            val map = repo.getProgressMap()
            _state.update { it.copy(progressMap = map) }
        } catch (_: Exception) {}
    }
}
