package com.scr01.mod

enum class PatchState { Active, ActiveUnverified, Inactive, Invalid, Unknown }

data class PatchRuntimeStatus(
    val state: PatchState = PatchState.Unknown,
    val detail: String = "正在检测",
)

object PatchRuntimeStatusReader {
    suspend fun read(rootAvailable: Boolean, executor: RootCommandExecutor, onLog: (String) -> Unit): PatchRuntimeStatus {
        if (!rootAvailable) return PatchRuntimeStatus(PatchState.Unknown, "Root 不可用")
        val loaded = executor.execute("grep -q '^scr01_idc36 ' /proc/modules")
        if (!loaded.succeeded) {
            onLog("IDC patch 状态：未加载")
            return PatchRuntimeStatus(PatchState.Inactive, "未加载")
        }
        val log = executor.execute("dmesg | grep 'scr01_idc36: patch active' | tail -n 1")
        return when (PatchLogVerifier.verifyActive(log.stdout)) {
            PatchLogVerification.Matched -> {
                onLog("IDC patch 状态：运行中，严格回读通过")
                PatchRuntimeStatus(PatchState.Active, "严格回读通过")
            }
            PatchLogVerification.Unavailable -> {
                onLog("IDC patch 状态：模块已加载，历史严格回读日志已不可用")
                PatchRuntimeStatus(PatchState.ActiveUnverified, "已加载 · 本次未直接验证")
            }
            PatchLogVerification.Mismatched -> {
                onLog("IDC patch 状态：模块已加载，但现有严格回读内容不匹配")
                PatchRuntimeStatus(PatchState.Invalid, "严格回读内容不匹配")
            }
        }
    }
}
