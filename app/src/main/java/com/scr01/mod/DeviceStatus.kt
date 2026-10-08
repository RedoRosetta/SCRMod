package com.scr01.mod

import android.os.Build
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class DeviceStatus(
    val root: CheckState = CheckState.Checking,
    val device: CheckState = CheckState.Checking,
    val firmware: CheckState = CheckState.Checking,
    val model: String = "正在读取",
    val firmwareName: String = "正在读取",
)

enum class CheckState { Checking, Passed, Failed }

object DeviceStatusReader {
    const val supportedModel = "SM-H412J"
    const val supportedFirmware = "SCR01KDU1AVK2"

    suspend fun read(executor: RootCommandExecutor, onLog: (String) -> Unit): DeviceStatus = withContext(Dispatchers.IO) {
        val model = Build.MODEL.orEmpty().ifBlank { Build.DEVICE.orEmpty().ifBlank { "未知" } }
        val firmware = sequenceOf(Build.DISPLAY, Build.VERSION.INCREMENTAL, Build.BOOTLOADER)
            .filterNotNull().firstOrNull { it.contains(supportedFirmware, ignoreCase = true) }
            ?: Build.DISPLAY.orEmpty().ifBlank { "未知" }
        val devicePassed = model.equals(supportedModel, true) ||
            Build.DEVICE.equals("SCR01", true) || Build.PRODUCT.contains("SCR01", true)
        val firmwarePassed = listOf(Build.DISPLAY, Build.VERSION.INCREMENTAL, Build.BOOTLOADER)
            .filterNotNull().any { it.contains(supportedFirmware, true) }

        onLog("设备检测：$model — ${if (devicePassed) "已验证" else "不匹配"}")
        onLog("固件检测：$firmware — ${if (firmwarePassed) "已验证" else "不匹配"}")

        val rootResult = executor.execute("id", timeoutSeconds = 8, forceRootProbe = true)
        val rootPassed = rootResult.succeeded && rootResult.stdout.contains("uid=0")
        onLog("Root 检测：${if (rootPassed) "已获取" else "不可用"}")

        DeviceStatus(
            root = if (rootPassed) CheckState.Passed else CheckState.Failed,
            device = if (devicePassed) CheckState.Passed else CheckState.Failed,
            firmware = if (firmwarePassed) CheckState.Passed else CheckState.Failed,
            model = model,
            firmwareName = firmware,
        )
    }
}
