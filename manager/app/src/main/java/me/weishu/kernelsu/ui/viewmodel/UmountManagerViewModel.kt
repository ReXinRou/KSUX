package me.weishu.kernelsu.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.weishu.kernelsu.data.model.UmountPath
import me.weishu.kernelsu.ui.screen.umount.UmountManagerUiState
import me.weishu.kernelsu.ui.util.addKernelUmountPath
import me.weishu.kernelsu.ui.util.addUmountConfigUmountPath
import me.weishu.kernelsu.ui.util.listKernelUmountPaths
import me.weishu.kernelsu.ui.util.listUmountConfigUmountPaths
import me.weishu.kernelsu.ui.util.removeKernelUmountPath
import me.weishu.kernelsu.ui.util.removeUmountConfigUmountPath
import org.json.JSONArray

sealed interface UmountManagerEvent {
    data class Message(val message: String) : UmountManagerEvent
}

class UmountManagerViewModel : ViewModel() {
    private val _uiState = MutableStateFlow(UmountManagerUiState())
    val uiState: StateFlow<UmountManagerUiState> = _uiState.asStateFlow()

    private val _events = MutableSharedFlow<UmountManagerEvent>(extraBufferCapacity = 1)
    val events: SharedFlow<UmountManagerEvent> = _events.asSharedFlow()

    fun refresh() {
        viewModelScope.launch(Dispatchers.IO) {
            _uiState.update { state ->
                state.copy(isRefreshing = !state.isLoading)
            }

            runCatching {
                coroutineScope {
                    val kernel = async { parse(listKernelUmountPaths(), persistent = false) }
                    val config = async { parse(listUmountConfigUmountPaths(), persistent = true) }
                    (kernel.await() + config.await())
                        .groupBy(UmountPath::path)
                        .map { (path, entries) ->
                            UmountPath(
                                path = path,
                                flags = entries.first().flags,
                                persistent = entries.any(UmountPath::persistent),
                            )
                        }
                        .sortedBy(UmountPath::path)
                }
            }.onSuccess { paths ->
                _uiState.value = UmountManagerUiState(
                    umountPaths = paths,
                    isLoading = false,
                    isRefreshing = false,
                )
            }.onFailure {
                emitMessage("Failed to load umount paths")
                _uiState.update { state ->
                    state.copy(isLoading = false, isRefreshing = false)
                }
            }
        }
    }

    fun add(path: String, flags: Int) {
        viewModelScope.launch(Dispatchers.IO) {
            val ok = addUmountConfigUmountPath(path, flags) && addKernelUmountPath(path, flags)
            if (ok) {
                _uiState.update { state ->
                    state.copy(
                        umountPaths = (state.umountPaths.filterNot { it.path == path } +
                                UmountPath(path, flags, persistent = true)).sortedBy(UmountPath::path),
                    )
                }
                emitMessage("Umount path added")
            } else {
                emitMessage("Failed to add umount path")
            }
        }
    }

    fun remove(entry: UmountPath) {
        viewModelScope.launch(Dispatchers.IO) {
            val ok = (!entry.persistent || removeUmountConfigUmountPath(entry.path)) &&
                    removeKernelUmountPath(entry.path)
            if (ok) {
                _uiState.update { state ->
                    state.copy(umountPaths = state.umountPaths.filterNot { it.path == entry.path })
                }
                emitMessage("Umount path removed")
            } else {
                emitMessage("Failed to remove umount path")
            }
        }
    }

    private fun emitMessage(message: String) {
        viewModelScope.launch { _events.emit(UmountManagerEvent.Message(message)) }
    }

    private fun parse(raw: String, persistent: Boolean): List<UmountPath> {
        if (raw.isBlank()) return emptyList()
        return runCatching {
            val array = JSONArray(raw)
            (0 until array.length()).map { index ->
                val value = array.getJSONObject(index)
                UmountPath(
                    path = value.getString("path"),
                    flags = value.getInt("flags"),
                    persistent = persistent,
                )
            }
        }.getOrDefault(emptyList())
    }
}
