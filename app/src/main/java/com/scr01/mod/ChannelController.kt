package com.scr01.mod

import android.content.Context
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File
import java.security.MessageDigest

data class ChannelApplyResult(
    val success: Boolean,
    val message: String,
    val actualStatus: HotspotStatus? = null,
    val kernelVerification: KernelVerificationState? = null,
    val rollback: KernelRollbackResult? = null,
)

class ChannelController(
    private val context: Context,
    private val executor: RootCommandExecutor,
    private val onLog: (String) -> Unit,
) {
    private val protection = KernelProtectionController(executor::execute, PersistentKernelEvidenceStore(context)) { message ->
        android.util.Log.i("SCR01Mod.KernelProtection", message)
        onLog(message)
    }

    companion object {
        private val operationMutex = Mutex()
    }

    suspend fun apply(
        channel: Int?,
        device: DeviceStatus,
        moduleAsset: ModuleAssetStatus,
        hotspot: HotspotStatus,
    ): ChannelApplyResult = operationMutex.withLock { applyLocked(channel, device, moduleAsset, hotspot) }

    private suspend fun applyLocked(
        channel: Int?,
        device: DeviceStatus,
        moduleAsset: ModuleAssetStatus,
        hotspot: HotspotStatus,
    ): ChannelApplyResult {
        onLog("开始应用：${channel?.let { "信道 $it" } ?: "自动模式"}")
        val safetyFailure = safetyFailure(device, moduleAsset)
        if (safetyFailure != null) return fail(safetyFailure)

        if (channel == null) {
            val restored = stopProtectionIfNeeded()
            return if (restored.success) {
                onLog("自动模式：IDC 保护已停止，系统 ACS / IDC 行为已恢复")
                ChannelApplyResult(true, "已恢复系统自动信道行为")
            } else restored
        }

        val target = SupportedChannels.forChannel(channel) ?: return fail("不支持的信道：$channel")
        if (hotspot.state != HotspotState.On) return fail("热点未开启，无法切换运行信道")

        if (channel in 36..48) {
            val protection = ensureProtectionActive()
            if (!protection.success) return protection
        } else {
            val restored = stopProtectionIfNeeded()
            if (!restored.success) return restored
        }

        val switch = executor.execute(target.hostapdCommand(), timeoutSeconds = 12)
        if (!switch.succeeded || switch.stdout.lineSequence().none { it.trim() == "OK" }) {
            return fail("hostapd 未确认信道切换：${switch.describe()}")
        }

        var lastActual: HotspotStatus? = null
        repeat(10) {
            delay(700)
            val actual = HotspotStatusReader.read(true, executor, onLog)
            lastActual = actual
            if (actual.channel == target.channel && actual.frequencyMhz == target.frequencyMhz) {
                onLog("实际信道回读成功：${actual.channel} / ${actual.frequencyMhz} MHz")
                return ChannelApplyResult(true, "已应用，当前信道：${target.channel}", actual)
            }
        }
        val observed = lastActual?.let {
            "最后回读 channel=${it.channel ?: "未知"}, freq=${it.frequencyMhz ?: "未知"}, bandwidth=${it.bandwidthMhz ?: "未知"}"
        } ?: "没有有效回读"
        return fail("hostapd 返回 OK，但未保持目标 channel=${target.channel}, freq=${target.frequencyMhz}；$observed")
    }

    private fun safetyFailure(device: DeviceStatus, moduleAsset: ModuleAssetStatus): String? = when {
        device.root != CheckState.Passed -> "Root 权限不可用"
        device.device != CheckState.Passed -> "设备型号未通过安全门禁"
        device.firmware != CheckState.Passed -> "固件未通过安全门禁"
        !moduleAsset.assetHashValid -> "冻结模块 SHA-256 不匹配"
        !moduleAsset.kernelValid -> "内核版本未通过安全门禁"
        !moduleAsset.safetyGatePassed -> "Phase 3 安全门禁未通过"
        else -> null
    }

    private suspend fun ensureProtectionActive(): ChannelApplyResult {
        return protection.ensureLoaded {
            val module = prepareModuleAsset()
            val ksud = "/data/adb/ksu/bin/ksud"
            if (module == null) {
                RootCommandResult("prepareModuleAsset", null, "", "冻结模块资产准备失败", false)
            } else {
                val paths = executor.execute("test -x ${quote(ksud)} -a -r ${quote(module.absolutePath)}")
                if (!paths.succeeded) paths else executor.execute(
                    "${quote(ksud)} insmod ${quote(module.absolutePath)}", timeoutSeconds = 20,
                )
            }
        }.asChannelResult()
    }

    private suspend fun stopProtectionIfNeeded(): ChannelApplyResult {
        return protection.stop().asChannelResult()
    }

    private fun KernelProtectionResult.asChannelResult(): ChannelApplyResult =
        ChannelApplyResult(success, message, kernelVerification = verification, rollback = rollback)

    private fun prepareModuleAsset(): File? = runCatching {
        val directory = File(context.filesDir, "kernel").apply { mkdirs() }
        val target = File(directory, "scr01_idc36.ko")
        context.assets.open(ModuleAssetVerifier.assetPath).use { input ->
            target.outputStream().use { output -> input.copyTo(output) }
        }
        val digest = MessageDigest.getInstance("SHA-256")
        target.inputStream().use { input ->
            val buffer = ByteArray(8 * 1024)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                digest.update(buffer, 0, count)
            }
        }
        val hash = digest.digest().joinToString("") { "%02X".format(it) }
        check(hash == ModuleAssetVerifier.expectedSha256)
        target.setReadable(true, false)
        target
    }.getOrNull()

    private fun quote(value: String): String = "'${value.replace("'", "'\\''")}'"

    private fun RootCommandResult.describe(): String = buildList {
        add("exit=${exitCode?.toString() ?: if (timedOut) "timeout" else "unknown"}")
        if (stdout.isNotBlank()) add("stdout=$stdout")
        if (stderr.isNotBlank()) add("stderr=$stderr")
        if (stdout.isBlank() && stderr.isBlank()) add("无输出")
    }.joinToString("; ")

    private fun fail(message: String): ChannelApplyResult {
        onLog("错误：$message")
        return ChannelApplyResult(false, message)
    }
}
