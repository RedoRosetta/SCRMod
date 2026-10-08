package com.scr01.mod

import android.util.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit

data class RootCommandResult(
    val command: String,
    val exitCode: Int?,
    val stdout: String,
    val stderr: String,
    val timedOut: Boolean,
) {
    val succeeded: Boolean get() = !timedOut && exitCode == 0
}

internal data class RootProbeResult(
    val started: Boolean,
    val exitedNormally: Boolean,
    val exitCode: Int?,
    val stdout: String,
    val stderr: String,
    val timedOut: Boolean,
)

internal data class RootProbeFailure(
    val candidate: String,
    val reason: String,
)

internal data class RootDiscoveryResult(
    val executable: String?,
    val successfulProbe: RootProbeResult?,
    val failures: List<RootProbeFailure>,
)

internal class RootExecutableDiscovery(
    private val candidates: List<String> = defaultRootExecutableCandidates(),
    private val probeRunner: (String, Long) -> RootProbeResult = ::probeRootExecutable,
    private val probeTimeoutMs: Long = 3_000L,
    private val monotonicMs: () -> Long = { System.nanoTime() / 1_000_000L },
) {
    private val lock = Any()
    @Volatile
    private var cached: RootDiscoveryResult? = null
    private var lastProbeMs = 0L

    /**
     * Revalidates successful discovery after 5 seconds; retries unavailable root after 15 seconds.
     * The synchronized section intentionally
     * covers the blocking probes: concurrent first callers wait for this exact result
     * instead of starting parallel `su -c id` processes.
     */
    fun resolve(forceRefresh: Boolean = false): RootDiscoveryResult = synchronized(lock) {
        val previous = cached
        val age = monotonicMs() - lastProbeMs
        if (!forceRefresh && previous != null && age >= 0L &&
            age < if (previous.executable != null) 5_000L else 15_000L
        ) previous else discover().also {
            cached = it
            lastProbeMs = monotonicMs()
        }
    }

    private fun discover(): RootDiscoveryResult {
        val failures = mutableListOf<RootProbeFailure>()
        for (candidate in candidates.distinct()) {
            val probe = runCatching { probeRunner(candidate, probeTimeoutMs) }.getOrElse { error ->
                RootProbeResult(
                    started = false,
                    exitedNormally = false,
                    exitCode = null,
                    stdout = "",
                    stderr = error.message.orEmpty(),
                    timedOut = false,
                )
            }
            if (probe.started && probe.exitedNormally && probe.exitCode == 0 && containsRootUid(probe.stdout)) {
                return RootDiscoveryResult(candidate, probe, failures)
            }
            failures += RootProbeFailure(candidate, probeFailureReason(probe))
        }
        return RootDiscoveryResult(null, null, failures)
    }

    private fun containsRootUid(stdout: String): Boolean = rootUidPattern.containsMatchIn(stdout)

    private fun probeFailureReason(probe: RootProbeResult): String = when {
        !probe.started -> probe.stderr.ifBlank { "启动失败" }
        probe.timedOut -> "超时"
        !probe.exitedNormally -> "未正常结束"
        probe.exitCode != 0 -> "退出码 ${probe.exitCode ?: "未知"}"
        else -> "stdout 未包含 uid=0"
    }

    companion object {
        private val rootUidPattern = Regex("(?m)(^|\\s)uid=0(?:\\D|$)")

        /**
         * `su` is deliberately first so Android resolves it using this App process's
         * own PATH. The explicit KernelSU entry is only a fallback; ksud itself is
         * intentionally absent because it is a CLI/loader, not a generic su path.
         */
        internal fun defaultRootExecutableCandidates(
            path: String = System.getenv("PATH").orEmpty(),
        ): List<String> {
            val pathCandidates = path
                .split(File.pathSeparator)
                .map(String::trim)
                .filter(String::isNotEmpty)
                .map { File(it, "su").path }
            return (listOf("su") +
                pathCandidates +
                listOf(
                    "/data/adb/ksu/bin/su",
                    "/system/bin/su",
                    "/system/xbin/su",
                )).distinct()
        }

        private fun probeRootExecutable(candidate: String, timeoutMs: Long): RootProbeResult {
            val process = try {
                ProcessBuilder(candidate, "-c", "id").start()
            } catch (error: IOException) {
                return RootProbeResult(false, false, null, "", error.message.orEmpty(), false)
            } catch (error: Throwable) {
                return RootProbeResult(false, false, null, "", error.message.orEmpty(), false)
            }

            return try {
                val exited = process.waitFor(timeoutMs, TimeUnit.MILLISECONDS)
                if (!exited) {
                    process.destroyForcibly()
                    RootProbeResult(true, false, null, "", "", true)
                } else {
                    RootProbeResult(
                        started = true,
                        exitedNormally = true,
                        exitCode = process.exitValue(),
                        stdout = process.inputStream.bufferedReader().use { it.readText().trim() },
                        stderr = process.errorStream.bufferedReader().use { it.readText().trim() },
                        timedOut = false,
                    )
                }
            } finally {
                process.inputStream.close()
                process.errorStream.close()
                process.outputStream.close()
            }
        }
    }
}

private object RootExecutableRegistry {
    val discovery = RootExecutableDiscovery()
}

class RootCommandExecutor private constructor(
    private val onLog: (String) -> Unit,
    private val discovery: RootExecutableDiscovery,
    @Suppress("UNUSED_PARAMETER") marker: Unit,
) {
    constructor(onLog: (String) -> Unit) : this(onLog, RootExecutableRegistry.discovery, Unit)

    internal constructor(
        onLog: (String) -> Unit,
        discovery: RootExecutableDiscovery,
    ) : this(onLog, discovery, Unit)

    suspend fun execute(command: String, timeoutSeconds: Long = 6, forceRootProbe: Boolean = false): RootCommandResult = withContext(Dispatchers.IO) {
        onLog("命令：${displayCommand(command)}")
        val root = discovery.resolve(forceRefresh = forceRootProbe)
        val executable = root.executable
        if (executable == null) {
            val detail = root.failures.joinToString("；") { "${it.candidate}: ${it.reason}" }
            val result = RootCommandResult(
                command = command,
                exitCode = null,
                stdout = "",
                stderr = "未找到可用的 Root shell 入口${detail.takeIf { it.isNotBlank() }?.let { "；$it" } ?: ""}",
                timedOut = false,
            )
            if (command == "id") Log.e("SCR01Mod.Root", result.stderr)
            logResult(result)
            return@withContext result
        }

        if (command == "id") {
            val message = "Root 入口：$executable（App 进程内缓存）"
            onLog(message)
            Log.i("SCR01Mod.Root", message)
        }

        // A root detection request can reuse the successful discovery probe, so the
        // App does not launch a second `su -c id` process for the same result.
        if (command == "id" && root.successfulProbe != null) {
            val probe = root.successfulProbe
            val result = RootCommandResult(
                command = command,
                exitCode = probe.exitCode,
                stdout = probe.stdout,
                stderr = probe.stderr,
                timedOut = probe.timedOut,
            )
            logResult(result)
            return@withContext result
        }

        // Already on Dispatchers.IO: no extra worker thread per command.
        val result = try {
            currentCoroutineContext().ensureActive()
            val process = ProcessBuilder(executable, "-c", command).start()
            val output = ProcessOutputCapture(process)
            try {
                val deadline = System.nanoTime() + timeoutSeconds * 1_000_000_000L
                var exited = false
                while (!exited && System.nanoTime() < deadline) {
                    currentCoroutineContext().ensureActive()
                    exited = process.waitFor(50, TimeUnit.MILLISECONDS)
                }
                currentCoroutineContext().ensureActive()
                RootCommandResult(
                    command, if (exited) process.exitValue() else null,
                    if (exited) output.stdout() else "",
                    if (exited) output.stderr() else "",
                    timedOut = !exited,
                )
            } finally {
                if (process.isAlive) process.destroyForcibly()
                output.close()
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            RootCommandResult(command, null, "", error.message.orEmpty(), timedOut = false)
        }
        logResult(result)
        result
    }

    suspend fun executeUntilExit(command: String): RootCommandResult = withContext(Dispatchers.IO) {
        val root = discovery.resolve()
        val executable = root.executable ?: return@withContext RootCommandResult(
            command = command,
            exitCode = null,
            stdout = "",
            stderr = "未找到可用的 Root shell 入口",
            timedOut = false,
        )
        val process = try {
            ProcessBuilder(executable, "-c", command).start()
        } catch (error: IOException) {
            return@withContext RootCommandResult(command, null, "", error.message.orEmpty(), false)
        }
        val output = ProcessOutputCapture(process)
        try {
            while (!process.waitFor(1, TimeUnit.SECONDS)) {
                currentCoroutineContext().ensureActive()
            }
            RootCommandResult(
                command = command,
                exitCode = process.exitValue(),
                stdout = output.stdout(),
                stderr = output.stderr(),
                timedOut = false,
            )
        } finally {
            if (process.isAlive) {
                process.destroy()
                if (!process.waitFor(1, TimeUnit.SECONDS)) process.destroyForcibly()
            }
            output.close()
        }
    }

    private fun logResult(result: RootCommandResult) {
        onLog("退出码：${result.exitCode?.toString() ?: if (result.timedOut) "超时" else "不可用"}")
        if (result.stdout.isNotBlank()) onLog("stdout：${safeOutput(result.stdout)}")
        if (result.stderr.isNotBlank()) onLog("stderr：${safeOutput(result.stderr)}")
    }

    private fun displayCommand(command: String): String = when {
        command == "id" -> "id"
        command.contains("settings get system $samsungDataUsageMaxKey") -> "读取 Samsung 数据使用量上限"
        command.contains("settings put system $samsungDataUsageMaxKey") -> "写入 Samsung 数据使用量上限"
        command.contains("operstate") -> "读取热点接口状态"
        command.contains("hostapd_cli") -> "读取热点运行参数"
        command.contains("ksud") && command.contains("insmod") -> "加载冻结 IDC 保护模块"
        command.contains("rmmod scr01_idc36") -> "卸载 IDC 保护模块"
        command.contains("/proc/modules") -> "检查 IDC 模块状态"
        command.contains("dmesg") -> "严格回读 IDC patch 状态"
        else -> "只读系统命令"
    }

    private fun safeOutput(value: String): String = value
        .replace(Regex("(?i)(psk|password|passphrase)=[^\\s]+"), "$1=<已隐藏>")
        .replace('\n', ' ')
        .take(400)
}
