package com.scr01.mod

import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext

enum class KernelVerificationState {
    VERIFIED_LOADED, VERIFIED_NOT_LOADED, VERIFICATION_UNAVAILABLE, VERIFICATION_FAILED,
}

enum class KernelRollbackState { ROLLBACK_SUCCESS, ROLLBACK_FAILED }

data class KernelRollbackResult(
    val state: KernelRollbackState,
    val commandResult: RootCommandResult,
    val finalState: KernelVerificationState,
    val detail: String,
)

internal data class KernelProtectionResult(
    val success: Boolean,
    val message: String,
    val verification: KernelVerificationState,
    val rollback: KernelRollbackResult? = null,
)

/** Shared by normal stop and failed-load rollback, with injectable I/O for regression tests. */
internal class KernelProtectionController(
    private val execute: suspend (String, Long) -> RootCommandResult,
    private val evidence: KernelEvidenceStore = MemoryKernelEvidenceStore(),
    private val onLog: (String) -> Unit,
) {
    suspend fun verify(): KernelVerificationState {
        val presence = presence()
        if (presence != KernelVerificationState.VERIFIED_LOADED) {
            evidence.clear()
            return presence
        }
        val identity = identity() ?: return KernelVerificationState.VERIFICATION_UNAVAILABLE
        val cached = evidence.read()
        // A sysfs directory gets a new kernfs inode on every module reload. The boot ID
        // prevents an inode reused after reboot from reviving a previous verification.
        if (cached != null && cached != identity) evidence.clear()
        val result = PatchLogVerifier.verifyActive(latest("active"))
        if (identity() != identity) {
            evidence.clear()
            return KernelVerificationState.VERIFICATION_UNAVAILABLE
        }
        return when {
            result == PatchLogVerification.Mismatched -> {
                evidence.clear()
                KernelVerificationState.VERIFICATION_FAILED
            }
            cached == identity -> {
                onLog("IDC 验证通过：本次开机和模块加载身份匹配已保存的严格回读证据")
                KernelVerificationState.VERIFIED_LOADED
            }
            else -> KernelVerificationState.VERIFICATION_UNAVAILABLE
        }
    }

    suspend fun ensureLoaded(load: suspend () -> RootCommandResult): KernelProtectionResult {
        val initial = verify()
        if (initial == KernelVerificationState.VERIFIED_LOADED) {
            return KernelProtectionResult(true, "IDC 保护已启用", initial)
        }
        if (initial != KernelVerificationState.VERIFIED_NOT_LOADED) {
            // Adopt an old/unmanaged load only by a checked unload + fresh strict reload.
            // A historical active line alone cannot establish its load identity.
            if (initial == KernelVerificationState.VERIFICATION_UNAVAILABLE &&
                presence() == KernelVerificationState.VERIFIED_LOADED && identity() != null && count("active") != null
            ) {
                return withContext(NonCancellable) {
                    onLog("IDC 当前加载缺少有效证据，开始受控卸载/重载以重新建立严格验证")
                    val restored = rollback()
                    if (restored.state != KernelRollbackState.ROLLBACK_SUCCESS) {
                        KernelProtectionResult(false, "重新建立验证失败；${restored.detail}", restored.finalState, restored)
                    } else {
                        val before = count("active")
                        if (before == null) failure("重载前无法读取验证记录", KernelVerificationState.VERIFICATION_UNAVAILABLE)
                        else loadAndVerify(load, before)
                    }
                }
            }
            return failure("IDC 验证未通过：$initial；未执行信道切换", initial)
        }
        val before = count("active") ?: return failure(
            "加载前无法读取 patch 验证计数，未加载模块", KernelVerificationState.VERIFICATION_UNAVAILABLE,
        )
        // Once a mutation starts, UI/worker cancellation must not abandon verification or rollback.
        return withContext(NonCancellable) { loadAndVerify(load, before) }
    }

    private suspend fun loadAndVerify(load: suspend () -> RootCommandResult, before: Int): KernelProtectionResult {
        val loaded = load()
        if (!loaded.succeeded) return failure("冻结模块加载失败：${loaded.describe()}", KernelVerificationState.VERIFICATION_UNAVAILABLE)
        val loadIdentity = identity()
        val present = presence()
        val active = PatchLogVerifier.verifyActive(latest("active"))
        val after = count("active")
        val verified = when {
            present != KernelVerificationState.VERIFIED_LOADED -> present
            active == PatchLogVerification.Mismatched -> KernelVerificationState.VERIFICATION_FAILED
            active == PatchLogVerification.Matched && loadIdentity != null && identity() == loadIdentity && after != null && after > before ->
                KernelVerificationState.VERIFIED_LOADED
            else -> KernelVerificationState.VERIFICATION_UNAVAILABLE
        }
        if (verified == KernelVerificationState.VERIFIED_LOADED) {
            // If durable storage fails, this operation still has fresh strict proof;
            // later operations will safely refresh instead of claiming a cached load.
            runCatching { evidence.save(loadIdentity!!) }.onFailure { onLog("IDC 验证证据保存失败：${it.message}") }
            onLog("IDC patch 严格回读通过")
            return KernelProtectionResult(true, "IDC 保护已启用", verified)
        }
        val reason = "加载后 patch 验证失败：state=$verified, count=$before->$after；未执行信道切换"
        onLog(reason)
        val rollback = rollback()
        return KernelProtectionResult(false, "$reason；${rollback.detail}", rollback.finalState, rollback)
    }

    suspend fun stop(): KernelProtectionResult {
        val initial = presence()
        if (initial == KernelVerificationState.VERIFIED_NOT_LOADED) {
            return KernelProtectionResult(true, "IDC 保护未加载", initial)
        }
        if (initial != KernelVerificationState.VERIFIED_LOADED) {
            return failure("无法确认模块状态，未报告已停止", initial)
        }
        val rollback = withContext(NonCancellable) { rollback() }
        return KernelProtectionResult(
            rollback.state == KernelRollbackState.ROLLBACK_SUCCESS,
            rollback.detail, rollback.finalState, rollback,
        )
    }

    private suspend fun rollback(): KernelRollbackResult {
        evidence.clear()
        onLog("ROLLBACK_ATTEMPTED=YES")
        val before = count("inactive")
        val command = execute("rmmod scr01_idc36", 12)
        val presence = presence()
        val line = latest("inactive")
        val after = count("inactive")
        val restored = presence == KernelVerificationState.VERIFIED_NOT_LOADED &&
            before != null && after != null && after > before &&
            line.contains("wrapper=${"00".repeat(32)}") &&
            line.contains("callsite=${ModuleAssetVerifier.expectedCallsiteOriginal}")
        val finalState = when {
            restored -> KernelVerificationState.VERIFIED_NOT_LOADED
            presence == KernelVerificationState.VERIFIED_LOADED -> KernelVerificationState.VERIFICATION_FAILED
            presence == KernelVerificationState.VERIFICATION_UNAVAILABLE || before == null || after == null || line.isBlank() ->
                KernelVerificationState.VERIFICATION_UNAVAILABLE
            else -> KernelVerificationState.VERIFICATION_FAILED
        }
        val state = if (command.succeeded && restored) KernelRollbackState.ROLLBACK_SUCCESS else KernelRollbackState.ROLLBACK_FAILED
        val detail = "$state：unload=${command.describe()}；postVerification=$finalState；inactiveCount=$before->$after" +
            if (state == KernelRollbackState.ROLLBACK_SUCCESS) "；IDC 保护已停止，恢复验证通过" else "；未确认恢复到安全状态"
        onLog(detail)
        return KernelRollbackResult(state, command, finalState, detail)
    }

    private suspend fun presence(): KernelVerificationState {
        val result = execute("cat /proc/modules", 6)
        if (!result.succeeded) return KernelVerificationState.VERIFICATION_UNAVAILABLE
        return if (Regex("(?m)^scr01_idc36\\s").containsMatchIn(result.stdout)) {
            KernelVerificationState.VERIFIED_LOADED
        } else KernelVerificationState.VERIFIED_NOT_LOADED
    }

    private suspend fun identity(): KernelLoadIdentity? {
        val result = execute("cat /proc/sys/kernel/random/boot_id && stat -c %i /sys/module/scr01_idc36", 6)
        if (!result.succeeded) return null
        val lines = result.stdout.trim().lines()
        return if (lines.size == 2) KernelLoadIdentity.parse("${lines[0]}|${lines[1]}") else null
    }

    private suspend fun latest(state: String): String {
        val result = execute("set -o pipefail; dmesg | grep 'scr01_idc36: patch $state' | tail -n 1", 6)
        return if (result.succeeded) result.stdout.lowercase() else ""
    }

    private suspend fun count(state: String): Int? {
        val result = execute("set -o pipefail; dmesg | awk '/scr01_idc36: patch $state/{n++} END{print n+0}'", 6)
        return if (result.succeeded) result.stdout.trim().toIntOrNull()?.takeIf { it >= 0 } else null
    }

    private fun failure(message: String, state: KernelVerificationState): KernelProtectionResult {
        onLog(message)
        return KernelProtectionResult(false, message, state)
    }

    private fun RootCommandResult.describe(): String =
        "exit=${exitCode ?: "unknown"}, timedOut=$timedOut, stdout=${stdout.take(200)}, stderr=${stderr.take(200)}"
}
