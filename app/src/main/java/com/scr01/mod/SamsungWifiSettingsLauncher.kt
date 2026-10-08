package com.scr01.mod

/**
 * Opens the Samsung Wi-Fi settings page through the already-authorized root shell.
 *
 * The component and command are intentionally constants: this is not a general
 * activity launcher and it accepts no package, class, or extras from the UI.
 */
internal class SamsungWifiSettingsLauncher(
    private val executor: RootCommandExecutor,
) {
    suspend fun open(): SamsungWifiSettingsLaunchResult {
        val result = executor.execute(START_COMMAND, timeoutSeconds = 10)
        if (result.succeeded) {
            return SamsungWifiSettingsLaunchResult(
                success = true,
                message = "已打开 Samsung 原生 Wi-Fi 设置",
            )
        }

        val detail = when {
            result.timedOut -> "Root 命令超时"
            result.stderr.isNotBlank() -> result.stderr.singleLine().take(240)
            result.stdout.isNotBlank() -> result.stdout.singleLine().take(240)
            result.exitCode != null -> "退出码 ${result.exitCode}"
            else -> "Root 命令未返回结果"
        }
        return SamsungWifiSettingsLaunchResult(
            success = false,
            message = "打开 Samsung 原生 Wi-Fi 设置失败：$detail",
        )
    }

    private fun String.singleLine(): String = replace('\n', ' ').replace('\r', ' ').trim()

    private companion object {
        const val COMPONENT =
            "com.samsung.android.mhshome/com.samsung.android.mhshome.wifisettings.wifi.WifiSettingsActivity"
        const val START_COMMAND = "am start -n $COMPONENT"
    }
}

internal data class SamsungWifiSettingsLaunchResult(
    val success: Boolean,
    val message: String,
)
