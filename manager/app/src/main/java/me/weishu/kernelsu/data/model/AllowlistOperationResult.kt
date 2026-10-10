package me.weishu.kernelsu.data.model

sealed interface AllowlistOperationResult {
    data object Success : AllowlistOperationResult
    data object InvalidFile : AllowlistOperationResult
    data object UnsupportedVersion : AllowlistOperationResult
    data class ProfileUpdateFailed(val uid: Int) : AllowlistOperationResult
    data class Failed(val cause: Throwable? = null) : AllowlistOperationResult
}
