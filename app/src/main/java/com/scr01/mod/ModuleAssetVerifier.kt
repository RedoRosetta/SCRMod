package com.scr01.mod

import android.content.Context
import android.system.Os
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.security.MessageDigest

data class ModuleAssetStatus(
    val checked: Boolean = false,
    val assetHashValid: Boolean = false,
    val kernelValid: Boolean = false,
    val safetyGatePassed: Boolean = false,
    val kernelRelease: String = "正在读取",
)

object ModuleAssetVerifier {
    const val assetPath = "kernel/scr01_idc36.ko"
    const val expectedSha256 = "A16E544954CAAA406AD36321175E2A49609FFF385C3B11A8E7674C2DA3D67DEE"
    const val supportedKernel = "4.14.186-24165939"
    const val expectedWrapper = "a8424539089100511f310071880000541f05007241000054c0035fd668751e14"
    const val expectedCallsitePatched = "d888e197"
    const val expectedCallsiteOriginal = "47feff97"

    suspend fun verify(context: Context, device: DeviceStatus, onLog: (String) -> Unit): ModuleAssetStatus =
        withContext(Dispatchers.IO) {
            val digest = MessageDigest.getInstance("SHA-256")
            context.assets.open(assetPath).use { input ->
                val buffer = ByteArray(8 * 1024)
                while (true) {
                    val count = input.read(buffer)
                    if (count < 0) break
                    digest.update(buffer, 0, count)
                }
            }
            val actualHash = digest.digest().joinToString("") { "%02X".format(it) }
            val hashValid = actualHash == expectedSha256
            val kernelRelease = runCatching { Os.uname().release }.getOrDefault("未知")
            val kernelValid = kernelRelease == supportedKernel
            val gatePassed = hashValid && kernelValid &&
                device.device == CheckState.Passed && device.firmware == CheckState.Passed

            onLog("冻结模块资产：${if (hashValid) "SHA-256 已验证" else "SHA-256 不匹配"}")
            onLog("内核检测：$kernelRelease — ${if (kernelValid) "已验证" else "不匹配"}")
            onLog("Phase 3 安全门禁：${if (gatePassed) "通过（未加载模块）" else "未通过"}")
            ModuleAssetStatus(true, hashValid, kernelValid, gatePassed, kernelRelease)
        }
}
