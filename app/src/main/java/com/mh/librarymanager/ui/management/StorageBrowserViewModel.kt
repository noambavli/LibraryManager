package com.mh.librarymanager.ui.management

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.mh.librarymanager.R
import com.mh.librarymanager.data.storage.SandboxStorage
import com.mh.librarymanager.data.storage.StorageEntry
import com.mh.librarymanager.data.storage.StorageZone
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class StorageBrowserLocation(
    val zone: StorageZone,
    val relativePath: String = "",
)

class StorageBrowserViewModel(app: Application) : AndroidViewModel(app) {

    private val sandbox = SandboxStorage(app)

    private val _location = MutableStateFlow<StorageBrowserLocation?>(null)
    val location: StateFlow<StorageBrowserLocation?> = _location.asStateFlow()

    private val _entries = MutableStateFlow<List<StorageEntry>>(emptyList())
    val entries: StateFlow<List<StorageEntry>> = _entries.asStateFlow()

    private val _zones = MutableStateFlow(sandbox.listZones())
    val zones: StateFlow<List<com.mh.librarymanager.data.storage.ZoneInfo>> = _zones.asStateFlow()

    private val _feedback = MutableStateFlow<String?>(null)
    val feedback: StateFlow<String?> = _feedback.asStateFlow()

    private val _isWorking = MutableStateFlow(false)
    val isWorking: StateFlow<Boolean> = _isWorking.asStateFlow()

    private val _selected = MutableStateFlow<Set<String>>(emptySet())
    val selected: StateFlow<Set<String>> = _selected.asStateFlow()

    init {
        refreshZones()
    }

    val deletableEntries: List<StorageEntry>
        get() = _entries.value.filter { it.deletable && !it.isDirectory }

    fun toggleSelection(name: String) {
        _selected.value = _selected.value.toMutableSet().also { set ->
            if (name in set) set.remove(name) else set.add(name)
        }
    }

    fun selectAllDeletable() {
        _selected.value = deletableEntries.map { it.name }.toSet()
    }

    fun clearSelection() {
        _selected.value = emptySet()
    }

    fun selectedTotalBytes(): Long =
        deletableEntries.filter { it.name in _selected.value }.sumOf { it.sizeBytes }

    fun refreshZones() {
        _zones.value = sandbox.listZones()
    }

    fun openZone(zone: StorageZone) {
        clearSelection()
        _location.value = StorageBrowserLocation(zone = zone)
        refreshEntries()
    }

    fun openFolder(name: String) {
        val current = _location.value ?: return
        val nextPath = sandbox.resolveChildDirectory(current.zone, current.relativePath, name) ?: return
        clearSelection()
        _location.value = current.copy(relativePath = nextPath)
        refreshEntries()
    }

    /** @return true if navigation was handled inside the browser; false = exit screen */
    fun navigateUp(): Boolean {
        val current = _location.value ?: return false
        clearSelection()
        if (current.relativePath.isBlank()) {
            _location.value = null
            _entries.value = emptyList()
            return true
        }
        val parent = current.relativePath.substringBeforeLast('/', "")
        _location.value = current.copy(relativePath = parent)
        refreshEntries()
        return true
    }

    fun refreshEntries() {
        val current = _location.value ?: return
        viewModelScope.launch {
            val next = withContext(Dispatchers.IO) {
                sandbox.listEntries(current.zone, current.relativePath)
            }
            _entries.value = next
            _selected.value = _selected.value.filterTo(mutableSetOf()) { name ->
                next.any { it.name == name && it.deletable && !it.isDirectory }
            }
        }
    }

    fun deleteSelected() {
        val current = _location.value ?: return
        val names = _selected.value.toList()
        if (names.isEmpty() || _isWorking.value) return
        _isWorking.value = true
        viewModelScope.launch {
            var deleted = 0
            var failed = 0
            var lastError = ""
            withContext(Dispatchers.IO) {
                for (name in names) {
                    when (val result = sandbox.deleteEntry(current.zone, current.relativePath, name)) {
                        SandboxStorage.DeleteResult.Ok -> deleted++
                        is SandboxStorage.DeleteResult.Error -> {
                            failed++
                            lastError = result.message
                        }
                    }
                }
            }
            clearSelection()
            _feedback.value = when {
                deleted > 0 && failed == 0 ->
                    getApplication<Application>().getString(R.string.storage_browser_bulk_done, deleted)
                deleted > 0 && failed > 0 ->
                    getApplication<Application>().getString(
                        R.string.storage_browser_bulk_partial,
                        deleted,
                        failed,
                        lastError,
                    )
                failed > 0 -> lastError
                else -> getApplication<Application>().getString(R.string.storage_browser_bulk_none)
            }
            refreshEntries()
            _isWorking.value = false
        }
    }

    fun dismissFeedback() {
        _feedback.value = null
    }

    fun breadcrumb(): String {
        val current = _location.value ?: return ""
        return sandbox.breadcrumb(current.zone, current.relativePath)
    }
}
