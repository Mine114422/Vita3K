package org.vita3k.emulator.data

enum class UpdateCheckStatus {
    Failed,
    UpToDate,
    UpdateAvailable,
    CurrentBuildNewerThanLatest,
    CustomBuildCanUpdate
}

data class UpdateInfo(
    val version: String = "",
    val buildNumber: Long = 0L,
    val releaseUrl: String = "",
    val publishedAt: String = "",
    val notes: String = ""
)

data class UpdateCheckResult(
    val status: UpdateCheckStatus,
    val message: String,
    val info: UpdateInfo = UpdateInfo(),
    val currentDisplayVersion: String = ""
)
