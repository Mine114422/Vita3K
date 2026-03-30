package org.vita3k.emulator.ui.viewmodel

import android.app.Application
import androidx.annotation.StringRes
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch
import org.vita3k.emulator.R
import org.vita3k.emulator.data.InstallRepository
import java.io.File
import kotlin.math.roundToInt

enum class InstallType {
    FIRMWARE, PKG, ARCHIVE, LICENSE
}

enum class InstallResultStatus {
    SUCCESS, PARTIAL, ERROR
}

data class DeleteSourceOption(
    val label: String,
    val paths: List<String>
)

data class InstallResult(
    val status: InstallResultStatus,
    val message: String,
    val deleteOptions: List<DeleteSourceOption> = emptyList()
)

class InstallViewModel(application: Application) : AndroidViewModel(application) {

    private fun str(@StringRes id: Int, vararg args: Any): String =
        getApplication<Application>().getString(id, *args)

    var showInstallSheet by mutableStateOf(false)
        private set
    var installing by mutableStateOf(false)
        private set
    var progress by mutableStateOf(0)
        private set
    var statusMessage by mutableStateOf("")
        private set
    var installResult by mutableStateOf<InstallResult?>(null)
        private set
    var showPkgLicenseDialog by mutableStateOf(false)
        private set

    private var pendingPkgPath: String? = null
    private var pendingPkgLicensePath: String? = null
    private var pendingPkgOnComplete: (() -> Unit)? = null

    fun showSheet() {
        showInstallSheet = true
    }

    fun hideSheet() {
        showInstallSheet = false
    }

    fun confirmInstallResult(selectedDeleteOptions: List<DeleteSourceOption>) {
        val paths = selectedDeleteOptions
            .flatMap(DeleteSourceOption::paths)
            .filter(String::isNotBlank)
            .distinct()

        installResult = null
        if (paths.isEmpty()) {
            return
        }

        viewModelScope.launch {
            paths.forEach { path ->
                runCatching { File(path).delete() }
            }
        }
    }

    fun installFirmware(path: String, onComplete: () -> Unit) {
        beginInstall(R.string.install_status_firmware)
        val deleteOptions = sourceDeleteOptions(
            deleteOption(R.string.install_delete_firmware_file, path)
        )
        viewModelScope.launch {
            try {
                val version = InstallRepository.installFirmware(path) { pct, status ->
                    updateProgress(pct, status)
                }
                installResult = if (version.isNotEmpty()) {
                    InstallResult(
                        status = InstallResultStatus.SUCCESS,
                        message = str(R.string.install_success_firmware, version),
                        deleteOptions = deleteOptions
                    )
                } else {
                    InstallResult(
                        status = InstallResultStatus.ERROR,
                        message = str(R.string.install_failed_firmware),
                        deleteOptions = deleteOptions
                    )
                }
            } catch (e: Exception) {
                installResult = InstallResult(
                    status = InstallResultStatus.ERROR,
                    message = str(R.string.install_error_generic, e.message ?: ""),
                    deleteOptions = deleteOptions
                )
            } finally {
                installing = false
                onComplete()
            }
        }
    }

    fun onPkgPicked(path: String, onComplete: () -> Unit) {
        pendingPkgOnComplete = onComplete
        pendingPkgLicensePath = null
        beginInstall(R.string.install_status_preparing)
        viewModelScope.launch {
            try {
                pendingPkgPath = path
                val autoZrif = InstallRepository.findPkgZrif(path)
                installing = false
                if (autoZrif.isNotEmpty()) {
                    confirmPkgInstall(autoZrif)
                } else {
                    showPkgLicenseDialog = true
                }
            } catch (e: Exception) {
                pendingPkgPath = null
                pendingPkgOnComplete = null
                pendingPkgLicensePath = null
                installing = false
                installResult = InstallResult(
                    status = InstallResultStatus.ERROR,
                    message = str(R.string.install_error_generic, e.message ?: ""),
                    deleteOptions = sourceDeleteOptions(
                        deleteOption(R.string.install_delete_package_file, path)
                    )
                )
            }
        }
    }

    fun onPkgLicenseFilePicked(path: String) {
        pendingPkgLicensePath = path
        viewModelScope.launch {
            try {
                val zrif = InstallRepository.convertRifToZrif(path)
                if (zrif.isEmpty()) {
                    installResult = InstallResult(
                        status = InstallResultStatus.ERROR,
                        message = str(R.string.install_error_read_license),
                        deleteOptions = sourceDeleteOptions(
                            deleteOption(R.string.install_delete_package_file, pendingPkgPath),
                            deleteOption(R.string.install_delete_license_file, path)
                        )
                    )
                    cancelPkgInstall()
                    return@launch
                }

                confirmPkgInstall(zrif)
            } catch (e: Exception) {
                installResult = InstallResult(
                    status = InstallResultStatus.ERROR,
                    message = str(R.string.install_error_generic, e.message ?: ""),
                    deleteOptions = sourceDeleteOptions(
                        deleteOption(R.string.install_delete_package_file, pendingPkgPath),
                        deleteOption(R.string.install_delete_license_file, path)
                    )
                )
                cancelPkgInstall()
            }
        }
    }

    fun confirmPkgInstall(zrif: String) {
        showPkgLicenseDialog = false
        val onComplete = pendingPkgOnComplete ?: return
        val path = pendingPkgPath ?: run {
            installResult = InstallResult(
                status = InstallResultStatus.ERROR,
                message = str(R.string.install_error_pkg_path_lost)
            )
            pendingPkgOnComplete = null
            pendingPkgLicensePath = null
            return
        }
        val licensePath = pendingPkgLicensePath
        pendingPkgOnComplete = null
        pendingPkgPath = null
        pendingPkgLicensePath = null
        installPkgFromPath(path, zrif, licensePath, onComplete)
    }

    fun cancelPkgInstall() {
        showPkgLicenseDialog = false
        pendingPkgPath = null
        pendingPkgLicensePath = null
        pendingPkgOnComplete = null
    }

    fun installArchive(path: String, onComplete: () -> Unit) {
        beginInstall(R.string.install_status_archive)
        val deleteOptions = sourceDeleteOptions(
            deleteOption(R.string.install_delete_archive_files, path)
        )
        viewModelScope.launch {
            try {
                val success = InstallRepository.installArchive(
                    path,
                    forceReinstall = true
                ) { pct, status ->
                    updateProgress(pct, status)
                }
                installResult = if (success) {
                    InstallResult(
                        status = InstallResultStatus.SUCCESS,
                        message = str(R.string.install_success_archive),
                        deleteOptions = deleteOptions
                    )
                } else {
                    InstallResult(
                        status = InstallResultStatus.ERROR,
                        message = str(R.string.install_failed_archive),
                        deleteOptions = deleteOptions
                    )
                }
            } catch (e: Exception) {
                installResult = InstallResult(
                    status = InstallResultStatus.ERROR,
                    message = str(R.string.install_error_generic, e.message ?: ""),
                    deleteOptions = deleteOptions
                )
            } finally {
                installing = false
                onComplete()
            }
        }
    }

    fun installArchiveFolder(paths: List<String>, onComplete: () -> Unit) {
        beginInstall(R.string.install_status_archive)
        viewModelScope.launch {
            if (paths.isEmpty()) {
                installResult = InstallResult(
                    status = InstallResultStatus.ERROR,
                    message = str(R.string.install_error_archive_folder_empty)
                )
                installing = false
                onComplete()
                return@launch
            }

            val deleteOptions = sourceDeleteOptions(
                deleteOption(R.string.install_delete_archive_files, paths)
            )

            try {
                val total = paths.size
                var successCount = 0
                paths.forEachIndexed { index, path ->
                    val archiveName = File(path).name.ifBlank { path }
                    statusMessage = str(
                        R.string.install_status_archive_batch,
                        index + 1,
                        total,
                        archiveName
                    )
                    val success = InstallRepository.installArchive(
                        path,
                        forceReinstall = true
                    ) { pct, _ ->
                        val overall = ((index + (pct / 100f)) / total.toFloat()) * 100f
                        progress = overall.roundToInt().coerceIn(0, 100)
                        statusMessage = str(
                            R.string.install_status_archive_batch,
                            index + 1,
                            total,
                            archiveName
                        )
                    }
                    if (success) {
                        successCount++
                    }
                }

                val failureCount = total - successCount
                installResult = when {
                    successCount == total -> InstallResult(
                        status = InstallResultStatus.SUCCESS,
                        message = str(R.string.install_success_archive_batch, successCount),
                        deleteOptions = deleteOptions
                    )

                    successCount > 0 -> InstallResult(
                        status = InstallResultStatus.PARTIAL,
                        message = str(R.string.install_partial_archive_batch, successCount, failureCount),
                        deleteOptions = deleteOptions
                    )

                    else -> InstallResult(
                        status = InstallResultStatus.ERROR,
                        message = str(R.string.install_failed_archive_batch, total),
                        deleteOptions = deleteOptions
                    )
                }
            } catch (e: Exception) {
                installResult = InstallResult(
                    status = InstallResultStatus.ERROR,
                    message = str(R.string.install_error_generic, e.message ?: ""),
                    deleteOptions = deleteOptions
                )
            } finally {
                installing = false
                onComplete()
            }
        }
    }

    fun installLicense(path: String, onComplete: () -> Unit) {
        beginInstall(R.string.install_status_license)
        val deleteOptions = sourceDeleteOptions(
            deleteOption(R.string.install_delete_license_file, path)
        )
        viewModelScope.launch {
            try {
                val success = InstallRepository.copyLicense(path)
                installResult = if (success) {
                    InstallResult(
                        status = InstallResultStatus.SUCCESS,
                        message = str(R.string.install_success_license),
                        deleteOptions = deleteOptions
                    )
                } else {
                    InstallResult(
                        status = InstallResultStatus.ERROR,
                        message = str(R.string.install_failed_license),
                        deleteOptions = deleteOptions
                    )
                }
            } catch (e: Exception) {
                installResult = InstallResult(
                    status = InstallResultStatus.ERROR,
                    message = str(R.string.install_error_generic, e.message ?: ""),
                    deleteOptions = deleteOptions
                )
            } finally {
                installing = false
                onComplete()
            }
        }
    }

    fun installLicenseFromZrif(zrif: String, onComplete: () -> Unit) {
        beginInstall(R.string.install_status_license)
        viewModelScope.launch {
            try {
                val success = InstallRepository.createLicense(zrif)
                installResult = if (success) {
                    InstallResult(
                        status = InstallResultStatus.SUCCESS,
                        message = str(R.string.install_success_license)
                    )
                } else {
                    InstallResult(
                        status = InstallResultStatus.ERROR,
                        message = str(R.string.install_failed_license_zrif)
                    )
                }
            } catch (e: Exception) {
                installResult = InstallResult(
                    status = InstallResultStatus.ERROR,
                    message = str(R.string.install_error_generic, e.message ?: "")
                )
            } finally {
                installing = false
                onComplete()
            }
        }
    }

    private fun installPkgFromPath(
        path: String,
        zrif: String,
        licensePath: String?,
        onComplete: () -> Unit
    ) {
        beginInstall(R.string.install_status_package)
        val deleteOptions = sourceDeleteOptions(
            deleteOption(R.string.install_delete_package_file, path),
            deleteOption(R.string.install_delete_license_file, licensePath)
        )
        viewModelScope.launch {
            try {
                val success = InstallRepository.installPkg(path, zrif) { pct, status ->
                    updateProgress(pct, status)
                }
                installResult = if (success) {
                    InstallResult(
                        status = InstallResultStatus.SUCCESS,
                        message = str(R.string.install_success_package),
                        deleteOptions = deleteOptions
                    )
                } else {
                    InstallResult(
                        status = InstallResultStatus.ERROR,
                        message = str(R.string.install_failed_package),
                        deleteOptions = deleteOptions
                    )
                }
            } catch (e: Exception) {
                installResult = InstallResult(
                    status = InstallResultStatus.ERROR,
                    message = str(R.string.install_error_generic, e.message ?: ""),
                    deleteOptions = deleteOptions
                )
            } finally {
                installing = false
                onComplete()
            }
        }
    }

    private fun updateProgress(percent: Int, status: String) {
        progress = percent
        statusMessage = status
    }

    private fun beginInstall(@StringRes statusResId: Int) {
        installResult = null
        installing = true
        progress = 0
        statusMessage = str(statusResId)
    }

    private fun deleteOption(@StringRes labelResId: Int, path: String?): DeleteSourceOption? =
        deleteOption(labelResId, listOfNotNull(path))

    private fun deleteOption(
        @StringRes labelResId: Int,
        paths: List<String>
    ): DeleteSourceOption? {
        val normalized = paths
            .filter(String::isNotBlank)
            .distinct()
        if (normalized.isEmpty()) {
            return null
        }

        return DeleteSourceOption(
            label = str(labelResId),
            paths = normalized
        )
    }

    private fun sourceDeleteOptions(vararg options: DeleteSourceOption?): List<DeleteSourceOption> =
        options.filterNotNull()
}
