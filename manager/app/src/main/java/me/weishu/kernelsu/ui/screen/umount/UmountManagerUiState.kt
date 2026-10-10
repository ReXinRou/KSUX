package me.weishu.kernelsu.ui.screen.umount

import androidx.compose.runtime.Immutable
import me.weishu.kernelsu.data.model.UmountPath

@Immutable
data class UmountManagerUiState(
    val umountPaths: List<UmountPath> = emptyList(),
    val isLoading: Boolean = true,
    val isRefreshing: Boolean = false,
)

@Immutable
data class UmountManagerActions(
    val onBack: () -> Unit,
    val onRefresh: () -> Unit,
    val onRemove: (UmountPath) -> Unit,
    val onAdd: (String, Int) -> Unit,
)
