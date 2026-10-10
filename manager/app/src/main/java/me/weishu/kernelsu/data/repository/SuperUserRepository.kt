package me.weishu.kernelsu.data.repository

import me.weishu.kernelsu.data.model.AllowlistOperationResult
import me.weishu.kernelsu.data.model.AppInfo

interface SuperUserRepository {
    suspend fun getAppList(): Result<Pair<List<AppInfo>, List<Int>>>
    suspend fun refreshProfiles(currentApps: List<AppInfo>): Result<List<AppInfo>>
    suspend fun backupAllowlist(uri: String): AllowlistOperationResult
    suspend fun restoreAllowlist(uri: String): AllowlistOperationResult
}
