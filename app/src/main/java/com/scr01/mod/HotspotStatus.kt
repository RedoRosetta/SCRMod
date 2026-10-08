package com.scr01.mod

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.NetworkInterface

enum class HotspotState { On, Off, Unknown }

data class HotspotStatus(
    val state: HotspotState = HotspotState.Unknown,
    val channel: Int? = null,
    val frequencyMhz: Int? = null,
    val bandwidthMhz: Int? = null,
)

object HotspotStatusReader {
    suspend fun read(
        rootAvailable: Boolean,
        executor: RootCommandExecutor,
        onLog: (String) -> Unit,
    ): HotspotStatus = read(rootAvailable, executor, 6, onLog)

    suspend fun read(
        rootAvailable: Boolean,
        executor: RootCommandExecutor,
        commandTimeoutSeconds: Long,
        onLog: (String) -> Unit,
    ): HotspotStatus =
        withContext(Dispatchers.IO) {
            if (!rootAvailable) {
                val localState = runCatching { NetworkInterface.getByName("swlan0")?.isUp }.getOrNull()
                val state = when (localState) {
                    true -> HotspotState.On
                    false -> HotspotState.Off
                    null -> HotspotState.Unknown
                }
                onLog("热点检测：${state.label()}（无 Root，仅接口状态）")
                return@withContext HotspotStatus(state = state)
            }

            val interfaceResult = executor.execute("cat /sys/class/net/swlan0/operstate", commandTimeoutSeconds)
            if (!interfaceResult.succeeded) {
                onLog("热点检测：无法读取 swlan0")
                return@withContext HotspotStatus()
            }
            if (!interfaceResult.stdout.equals("up", ignoreCase = true)) {
                onLog("热点检测：已关闭")
                return@withContext HotspotStatus(state = HotspotState.Off)
            }

            val hostapd = executor.execute("/vendor/bin/hostapd_cli -i swlan0 status", commandTimeoutSeconds)
            if (!hostapd.succeeded) {
                onLog("热点检测：接口已启动，但运行参数读取失败")
                return@withContext HotspotStatus(state = HotspotState.On)
            }
            val values = hostapd.stdout.lineSequence()
                .mapNotNull { line -> line.substringBefore('=', "").takeIf { it.isNotBlank() }?.let { it to line.substringAfter('=', "") } }
                .toMap()
            val frequency = values["freq"]?.toIntOrNull()
            val channel = values["channel"]?.toIntOrNull() ?: frequency?.let(::frequencyToChannel)
            val width = bandwidth(values["vht_oper_chwidth"]?.toIntOrNull(), values["secondary_channel"]?.toIntOrNull())
            onLog("热点检测：已开启，信道 ${channel ?: "未知"}，频率 ${frequency ?: "未知"} MHz，带宽 ${width ?: "未知"} MHz")
            HotspotStatus(HotspotState.On, channel, frequency, width)
        }

    private fun bandwidth(vhtWidth: Int?, secondaryChannel: Int?): Int? = when (vhtWidth) {
        1 -> 80
        2 -> 160
        3 -> 80
        0 -> if (secondaryChannel == null) null else if (secondaryChannel == 0) 20 else 40
        else -> null
    }

    private fun frequencyToChannel(frequency: Int): Int? = when {
        frequency == 2484 -> 14
        frequency in 2412..2472 -> (frequency - 2407) / 5
        frequency in 5000..5900 -> (frequency - 5000) / 5
        else -> null
    }
}

fun HotspotState.label(): String = when (this) {
    HotspotState.On -> "已开启"
    HotspotState.Off -> "已关闭"
    HotspotState.Unknown -> "未知"
}
