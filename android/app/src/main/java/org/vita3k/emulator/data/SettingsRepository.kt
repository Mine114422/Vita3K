package org.vita3k.emulator.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.vita3k.emulator.NativeLib

internal data class SettingsSnapshot(
    val config: EmulatorConfig,
    val emulatorStoragePath: String,
    val hasCustomConfig: Boolean,
    val modulesList: List<Pair<String, Boolean>>,
    val installedCustomDrivers: List<String>,
    val showCustomDriverOptions: Boolean,
    val supportedMemoryMappingMask: Int,
    val customDriverLoadStatus: CustomDriverLoadStatus,
    val availableCameras: List<String>,
    val availableAdhocAddresses: List<Pair<String, String>>
)

enum class CustomDriverLoadStatus {
    Default,
    Loaded,
    Fallback;

    companion object {
        fun fromNative(value: Int): CustomDriverLoadStatus = when (value) {
            1 -> Loaded
            2 -> Fallback
            else -> Default
        }
    }
}

data class CustomDriverSupportInfo(
    val supportedMemoryMappingMask: Int,
    val loadStatus: CustomDriverLoadStatus
)

internal object SettingsRepository {

    suspend fun load(titleId: String?): SettingsSnapshot = withContext(Dispatchers.IO) {
        val hasCustomConfig = titleId != null && NativeLib.hasCustomConfig(titleId)
        val loadedConfig = when {
            titleId == null -> NativeLib.getGlobalConfig()
            hasCustomConfig -> requireNotNull(NativeLib.getCustomConfig(titleId))
            else -> NativeLib.getGlobalConfig()
        }
        val driverSupportInfo = getCustomDriverSupportInfoInternal(loadedConfig.customDriverName)

        SettingsSnapshot(
            config = loadedConfig,
            emulatorStoragePath = NativeLib.getCurrentEmulatorPath(),
            hasCustomConfig = hasCustomConfig,
            modulesList = parseModules(NativeLib.getModulesList(loadedConfig.lleModules)),
            installedCustomDrivers = NativeLib.getInstalledCustomDrivers().toList(),
            showCustomDriverOptions = NativeLib.shouldShowCustomDriverOptions(),
            supportedMemoryMappingMask = driverSupportInfo.supportedMemoryMappingMask,
            customDriverLoadStatus = driverSupportInfo.loadStatus,
            availableCameras = NativeLib.getAvailableCameras().toList(),
            availableAdhocAddresses = parsePairs(NativeLib.getAvailableAdhocAddresses())
        )
    }

    suspend fun save(titleId: String?, config: EmulatorConfig): Boolean = withContext(Dispatchers.IO) {
        if (titleId == null) {
            NativeLib.saveGlobalConfig(config)
            false
        } else {
            NativeLib.saveCustomConfig(titleId, config)
            runCatching { NativeLib.applyRuntimeConfig(config) }
            true
        }
    }

    suspend fun loadDefaults(): SettingsSnapshot = withContext(Dispatchers.IO) {
        val defaultConfig = NativeLib.getDefaultConfig()
        val driverSupportInfo = getCustomDriverSupportInfoInternal(defaultConfig.customDriverName)
        SettingsSnapshot(
            config = defaultConfig,
            emulatorStoragePath = NativeLib.getCurrentEmulatorPath(),
            hasCustomConfig = false,
            modulesList = parseModules(NativeLib.getModulesList(defaultConfig.lleModules)),
            installedCustomDrivers = NativeLib.getInstalledCustomDrivers().toList(),
            showCustomDriverOptions = NativeLib.shouldShowCustomDriverOptions(),
            supportedMemoryMappingMask = driverSupportInfo.supportedMemoryMappingMask,
            customDriverLoadStatus = driverSupportInfo.loadStatus,
            availableCameras = NativeLib.getAvailableCameras().toList(),
            availableAdhocAddresses = parsePairs(NativeLib.getAvailableAdhocAddresses())
        )
    }

    suspend fun setCurrentEmulatorPath(context: Context, path: String): Boolean = withContext(Dispatchers.IO) {
        val updated = NativeLib.setCurrentEmulatorPath(path)
        if (updated) {
            AppStorage.setStoragePath(context.applicationContext, path)
        }
        updated
    }

    suspend fun deleteCustomConfig(titleId: String): SettingsSnapshot = withContext(Dispatchers.IO) {
        NativeLib.deleteCustomConfig(titleId)
        val globalConfig = NativeLib.getGlobalConfig()
        runCatching { NativeLib.applyRuntimeConfig(globalConfig) }
        val driverSupportInfo = getCustomDriverSupportInfoInternal(globalConfig.customDriverName)
        SettingsSnapshot(
            config = globalConfig,
            emulatorStoragePath = NativeLib.getCurrentEmulatorPath(),
            hasCustomConfig = false,
            modulesList = parseModules(NativeLib.getModulesList(globalConfig.lleModules)),
            installedCustomDrivers = NativeLib.getInstalledCustomDrivers().toList(),
            showCustomDriverOptions = NativeLib.shouldShowCustomDriverOptions(),
            supportedMemoryMappingMask = driverSupportInfo.supportedMemoryMappingMask,
            customDriverLoadStatus = driverSupportInfo.loadStatus,
            availableCameras = NativeLib.getAvailableCameras().toList(),
            availableAdhocAddresses = parsePairs(NativeLib.getAvailableAdhocAddresses())
        )
    }

    suspend fun clearAllCustomConfigs(): Int = withContext(Dispatchers.IO) {
        NativeLib.clearAllCustomConfigs()
    }

    suspend fun getInstalledCustomDrivers(): List<String> = withContext(Dispatchers.IO) {
        NativeLib.getInstalledCustomDrivers().toList()
    }

    suspend fun installCustomDriver(path: String): String = withContext(Dispatchers.IO) {
        NativeLib.installCustomDriver(path)
    }

    suspend fun removeCustomDriver(driverName: String): Boolean = withContext(Dispatchers.IO) {
        NativeLib.removeCustomDriver(driverName)
    }

    suspend fun getSupportedMemoryMappingMask(customDriverName: String): Int = withContext(Dispatchers.IO) {
        NativeLib.getSupportedMemoryMappingMask(customDriverName)
    }

    suspend fun getCustomDriverSupportInfo(customDriverName: String): CustomDriverSupportInfo = withContext(Dispatchers.IO) {
        getCustomDriverSupportInfoInternal(customDriverName)
    }

    private fun getCustomDriverSupportInfoInternal(customDriverName: String): CustomDriverSupportInfo {
        val raw = NativeLib.getCustomDriverSupportInfo(customDriverName)
        val supportedMask = raw.getOrNull(0) ?: NativeLib.getSupportedMemoryMappingMask(customDriverName)
        val loadStatus = CustomDriverLoadStatus.fromNative(raw.getOrNull(1) ?: 0)
        return CustomDriverSupportInfo(
            supportedMemoryMappingMask = supportedMask,
            loadStatus = loadStatus
        )
    }

    private fun parseModules(rawModules: Array<String>): List<Pair<String, Boolean>> = buildList {
        var index = 0
        while (index + 1 < rawModules.size) {
            add(rawModules[index] to (rawModules[index + 1] == "true"))
            index += 2
        }
    }

    private fun parsePairs(rawPairs: Array<String>): List<Pair<String, String>> = buildList {
        var index = 0
        while (index + 1 < rawPairs.size) {
            add(rawPairs[index] to rawPairs[index + 1])
            index += 2
        }
    }
}
