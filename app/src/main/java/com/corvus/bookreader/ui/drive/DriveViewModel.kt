package ravens.scroll.ui.drive

import android.app.Application
import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import ravens.scroll.BookReaderApp
import ravens.scroll.data.model.DownloadedDriveFile
import ravens.scroll.data.model.DriveItem
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class DownloadProgress(
    val total: Int = 1,
    val done: Int = 0,
    val isComplete: Boolean = false,
    val error: String? = null,
) {
    val fraction: Float get() = if (total == 0) 0f else done.toFloat() / total
}

data class DriveUiState(
    val isSignedIn: Boolean = false,
    val items: List<DriveItem> = emptyList(),
    val stack: List<Pair<String, String>> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null,
    val progressMap: Map<String, Pair<Int, Int>> = emptyMap(),
    val fileParentMap: Map<String, String> = emptyMap(),
    val loadedFolderIds: Set<String> = emptySet(),
    val expandedFolderIds: Set<String> = emptySet(),
    val folderFiles: Map<String, List<DriveItem>> = emptyMap(),
    val downloadedFileIds: Set<String> = emptySet(),
    val downloadProgress: Map<String, DownloadProgress> = emptyMap(),
)

class DriveViewModel(app: Application) : AndroidViewModel(app) {

    private val repo = BookReaderApp.instance.driveRepository
    private val bookRepo = BookReaderApp.instance.bookRepository
    private val _state = MutableStateFlow(DriveUiState())
    val state: StateFlow<DriveUiState> = _state.asStateFlow()

    private val connectivityManager =
        app.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

    private val networkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            if (_state.value.isSignedIn) {
                viewModelScope.launch {
                    repo.initClient()
                    syncPendingProgress()
                }
            }
        }
    }

    init {
        connectivityManager.registerDefaultNetworkCallback(networkCallback)

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
            observeDownloadedIds()
        }
    }

    override fun onCleared() {
        connectivityManager.unregisterNetworkCallback(networkCallback)
        super.onCleared()
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
        observeDownloadedIds()
        viewModelScope.launch {
            loadProgressMap()
            syncPendingProgress()
        }
    }

    fun signOut() {
        repo.signOut()
        _state.update { DriveUiState(isSignedIn = false) }
    }

    // ── Folder navigation ─────────────────────────────────────────────────────────

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
                val alreadyLoaded = _state.value.loadedFolderIds
                items.filter { it.isFolder && it.id !in alreadyLoaded }
                    .forEach { folder -> loadFolderContent(folder.id) }
            } catch (e: Exception) {
                _state.update { it.copy(isLoading = false, error = e.message) }
            }
        }
    }

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
            if (repo.hasClient()) syncPendingProgress()
        }
    }

    private suspend fun loadProgressMap() {
        try {
            val map = repo.getProgressMap()
            _state.update { it.copy(progressMap = map) }
        } catch (_: Exception) {}
    }

    private fun observeDownloadedIds() {
        bookRepo.flowDownloadedIds()
            .onEach { ids -> _state.update { it.copy(downloadedFileIds = ids.toSet()) } }
            .launchIn(viewModelScope)
    }

    // ── Download ──────────────────────────────────────────────────────────────────

    fun downloadFile(item: DriveItem, folderName: String = "") {
        viewModelScope.launch {
            val ravensScrollDir = bookRepo.getRavensScrollDir() ?: return@launch
            _state.update {
                it.copy(downloadProgress = it.downloadProgress + (item.id to DownloadProgress(total = 1, done = 0)))
            }
            try {
                val localPath = repo.downloadAndSave(item.id, item.name, folderName, ravensScrollDir)
                val (scrollTop, percent) = repo.loadProgress(item.id)
                val book = ravens.scroll.data.model.Book(
                    uri = localPath,
                    title = ravens.scroll.domain.stripBookExt(item.name),
                    folderUri = if (folderName.isEmpty()) ravensScrollDir.absolutePath
                                else java.io.File(ravensScrollDir, folderName).absolutePath,
                    scrollTop = scrollTop,
                    percent = percent,
                    lastRead = 0L,
                    driveFileId = item.id,
                    pendingSync = false,
                )
                bookRepo.upsertBook(book)
                bookRepo.recordDownload(
                    DownloadedDriveFile(
                        driveFileId = item.id,
                        localPath = localPath,
                        driveFileName = item.name,
                        driveFolderName = folderName,
                    )
                )
                _state.update {
                    it.copy(
                        downloadProgress = it.downloadProgress + (item.id to DownloadProgress(1, 1, isComplete = true)),
                        downloadedFileIds = it.downloadedFileIds + item.id,
                    )
                }
            } catch (e: Exception) {
                _state.update {
                    it.copy(downloadProgress = it.downloadProgress + (item.id to DownloadProgress(error = e.message)))
                }
            }
        }
    }

    fun downloadFolder(folderItem: DriveItem) {
        viewModelScope.launch {
            val ravensScrollDir = bookRepo.getRavensScrollDir() ?: return@launch
            // Recurse into subfolders so a top-level folder (e.g. a series whose
            // books live under a nested 「本編」folder) downloads everything beneath
            // it. Files are flattened into one local folder named after this folder,
            // matching the single-level local library layout.
            val files = try {
                collectBookFilesRecursive(folderItem.id)
            } catch (e: Exception) {
                _state.update {
                    it.copy(downloadProgress = it.downloadProgress +
                        (folderItem.id to DownloadProgress(error = e.message)))
                }
                return@launch
            }
            if (files.isEmpty()) return@launch

            _state.update {
                it.copy(downloadProgress = it.downloadProgress +
                    (folderItem.id to DownloadProgress(total = files.size, done = 0)))
            }

            var done = 0
            val newDownloadedIds = mutableSetOf<String>()
            for (file in files) {
                try {
                    val localPath = repo.downloadAndSave(file.id, file.name, folderItem.name, ravensScrollDir)
                    val (scrollTop, percent) = repo.loadProgress(file.id)
                    bookRepo.upsertBook(
                        ravens.scroll.data.model.Book(
                            uri = localPath,
                            title = ravens.scroll.domain.stripBookExt(file.name),
                            folderUri = java.io.File(ravensScrollDir, folderItem.name).absolutePath,
                            scrollTop = scrollTop,
                            percent = percent,
                            lastRead = 0L,
                            driveFileId = file.id,
                            pendingSync = false,
                        )
                    )
                    bookRepo.recordDownload(
                        DownloadedDriveFile(
                            driveFileId = file.id,
                            localPath = localPath,
                            driveFileName = file.name,
                            driveFolderName = folderItem.name,
                        )
                    )
                    newDownloadedIds.add(file.id)
                } catch (_: Exception) {}
                done++
                _state.update {
                    it.copy(downloadProgress = it.downloadProgress +
                        (folderItem.id to DownloadProgress(files.size, done)))
                }
            }

            val isAllDone = done == files.size
            _state.update {
                it.copy(
                    downloadProgress = it.downloadProgress +
                        (folderItem.id to DownloadProgress(files.size, done, isComplete = isAllDone)),
                    downloadedFileIds = it.downloadedFileIds + newDownloadedIds,
                )
            }
        }
    }

    /** Collect all book files under [folderId], descending into every subfolder. */
    private suspend fun collectBookFilesRecursive(folderId: String): List<DriveItem> {
        val result = mutableListOf<DriveItem>()
        for (child in repo.listFolder(folderId)) {
            if (child.isFolder) result.addAll(collectBookFilesRecursive(child.id))
            else result.add(child)
        }
        return result
    }

    // ── Reset progress ────────────────────────────────────────────────────────────

    fun resetFileProgress(fileId: String) {
        viewModelScope.launch {
            try {
                repo.saveProgress(fileId, 0, 0)
                bookRepo.resetProgressByDriveFileId(fileId)
                _state.update { it.copy(progressMap = it.progressMap + (fileId to Pair(0, 0))) }
            } catch (_: Exception) {}
        }
    }

    fun resetFolderProgress(folderItem: DriveItem) {
        viewModelScope.launch {
            val files = try {
                repo.listFolder(folderItem.id).filter { !it.isFolder }
            } catch (_: Exception) { return@launch }
            for (file in files) {
                try {
                    repo.saveProgress(file.id, 0, 0)
                    bookRepo.resetProgressByDriveFileId(file.id)
                } catch (_: Exception) {}
            }
            val resetEntries = files.associate { it.id to Pair(0, 0) }
            _state.update { it.copy(progressMap = it.progressMap + resetEntries) }
        }
    }

    private suspend fun syncPendingProgress() {
        val allBooks = bookRepo.getAllDownloadedBooks()
        for (book in allBooks) {
            val driveFileId = book.driveFileId ?: continue
            try {
                val driveUpdatedAt = repo.getProgressUpdatedAt(driveFileId)
                if (driveUpdatedAt > book.lastRead) {
                    val (driveScrollTop, drivePercent) = repo.loadProgress(driveFileId)
                    bookRepo.applyDriveProgress(book.uri, driveScrollTop, drivePercent)
                } else if (book.pendingSync) {
                    repo.saveProgress(driveFileId, book.scrollTop, book.percent)
                    bookRepo.clearPendingSync(book.uri)
                }
            } catch (_: Exception) {}
        }
    }
}
