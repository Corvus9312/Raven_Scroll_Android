package com.corvus.bookreader.ui.drive

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.corvus.bookreader.data.model.DriveItem
import com.corvus.bookreader.data.repository.DriveRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class DriveUiState(
    val isSignedIn: Boolean = false,
    val items: List<DriveItem> = emptyList(),
    val stack: List<Pair<String, String>> = emptyList(), // (folderId, folderName)
    val isLoading: Boolean = false,
    val error: String? = null,
)

class DriveViewModel(app: Application) : AndroidViewModel(app) {

    private val repo = DriveRepository(app)
    private val _state = MutableStateFlow(DriveUiState())
    val state: StateFlow<DriveUiState> = _state.asStateFlow()

    init {
        val signedIn = repo.isSignedIn()
        _state.update { it.copy(isSignedIn = signedIn) }
        if (signedIn) {
            repo.initClient()
            loadFolder("root", "Google Drive")
        }
    }

    fun onSignedIn() {
        repo.initClient()
        _state.update { it.copy(isSignedIn = true) }
        loadFolder("root", "Google Drive")
    }

    fun signOut() {
        repo.signOut()
        _state.update { DriveUiState(isSignedIn = false) }
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
            _state.update { it.copy(isLoading = true, error = null) }
            try {
                val items = repo.listFolder(folderId)
                _state.update { it.copy(items = items, isLoading = false) }
            } catch (e: Exception) {
                _state.update { it.copy(isLoading = false, error = e.message) }
            }
        }
    }

    fun refresh() {
        val id = _state.value.stack.lastOrNull()?.first ?: "root"
        loadFolder(id, "")
    }
}
