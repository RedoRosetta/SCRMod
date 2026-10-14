package com.scr01.mod

import java.io.Closeable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

internal enum class CellProblem(val message: String) {
    PERMISSION("需要位置权限才能读取小区信息"), LOCATION_OFF("系统位置服务已关闭，请手动开启后重试"),
    UNAVAILABLE("Telephony 服务不可用或设备不支持小区读取"),
    FAILED("读取失败；可能受系统限制，请稍后重试"), TIMEOUT("更新超时，未确认新测量"),
    THROTTLED("刷新过于频繁，请等待几秒后重试"),
}

/** Callbacks and controller methods run on the UI thread. No OEM commands are exposed. */
internal interface CellInfoSource : Closeable {
    fun problem(): CellProblem?
    fun readCache(callback: (Result<CellBatch>) -> Unit)
    fun requestUpdate(callback: (Result<CellBatch>) -> Unit)
    fun listen(callback: (Result<CellBatch>) -> Unit): Closeable
}
internal interface CellClock {
    fun elapsed(): Long
    fun wall(): Long
    fun schedule(delayMs: Long, action: () -> Unit): Closeable
}

internal data class CellularState(
    val batch: CellBatch = CellBatch(emptyList()), val origin: CellOrigin? = null,
    val receivedElapsedMs: Long? = null, val receivedWallMs: Long? = null,
    val requestElapsedMs: Long? = null, val refreshing: Boolean = false, val active: Boolean = false,
    val problem: CellProblem? = null, val message: String = "等待读取系统小区信息",
)

internal class CellularMonitor(private val source: CellInfoSource, private val clock: CellClock) : Closeable {
    private val mutable = MutableStateFlow(CellularState())
    val state: StateFlow<CellularState> = mutable
    private var generation = 0L
    private var request = 0L
    private var revision = 0L
    private var listener: Closeable? = null
    private var timeout: Closeable? = null
    private var lastRequest: Long? = null
    private var closed = false

    fun start() {
        if (closed || mutable.value.active) return
        generation++
        mutable.value = mutable.value.copy(active = true, problem = null)
        if (!permitted()) return
        val epoch = generation
        listener = try {
            source.listen { result ->
                if (valid(epoch)) accept(result, CellOrigin.LISTENER)
            }
        } catch (e: Exception) { null } // Manual reading remains usable if listening is unsupported.
        val initialRevision = revision
        try {
            source.readCache { result ->
                if (valid(epoch) && revision == initialRevision) accept(result, CellOrigin.CACHE)
            }
        } catch (e: Exception) { fail(e) }
    }

    fun stop() {
        generation++; request++
        listener?.close(); listener = null
        timeout?.close(); timeout = null
        mutable.value = mutable.value.copy(active = false, refreshing = false,
            message = "监测已暂停；保留上次结果")
    }

    fun refresh() {
        if (closed || !mutable.value.active || mutable.value.refreshing) return
        if (!permitted()) return
        val now = clock.elapsed()
        if (lastRequest?.let { now - it < 10_000 } == true) {
            mutable.value = mutable.value.copy(problem = CellProblem.THROTTLED); return
        }
        lastRequest = now
        val epoch = generation
        val token = ++request
        val before = mutable.value.batch.cells
        mutable.value = mutable.value.copy(refreshing = true, requestElapsedMs = now,
            problem = null, message = "正在请求系统更新…")
        timeout = clock.schedule(15_000) {
            if (valid(epoch) && request == token) {
                request++
                mutable.value = mutable.value.copy(refreshing = false, problem = CellProblem.TIMEOUT)
            }
        }
        try {
            source.requestUpdate { result ->
                if (!valid(epoch) || request != token) return@requestUpdate
                timeout?.close(); timeout = null
                request++
                mutable.value = mutable.value.copy(refreshing = false)
                accept(result, CellOrigin.UPDATE, before)
            }
        } catch (e: Exception) {
            timeout?.close(); timeout = null; request++
            mutable.value = mutable.value.copy(refreshing = false); fail(e)
        }
    }

    private fun valid(epoch: Long) = !closed && mutable.value.active && generation == epoch
    private fun permitted(): Boolean {
        val problem = try { source.problem() } catch (e: SecurityException) {
            CellProblem.PERMISSION
        } catch (e: Exception) { CellProblem.UNAVAILABLE }
        if (problem != null) {
            // Do not expose earlier location data after access has been revoked/disabled.
            mutable.value = mutable.value.copy(batch = CellBatch(emptyList()), origin = null,
                receivedElapsedMs = null, receivedWallMs = null, problem = problem)
            return false
        }
        return true
    }
    private fun accept(result: Result<CellBatch>, origin: CellOrigin, before: List<CellularCell> = emptyList()) {
        if (!permitted()) return
        result.fold(onSuccess = { batch ->
            revision++
            val cells = CellularCells.deduplicate(batch.cells)
            val unchanged = origin == CellOrigin.UPDATE && cells.isNotEmpty() &&
                cells.all { cell -> before.any { it.measurementKey == cell.measurementKey &&
                    it.measuredElapsedMs == cell.measuredElapsedMs } }
            val message = when {
                cells.isEmpty() -> "系统未报告 LTE/NR 小区；不能据此判断附近没有小区"
                unchanged -> "已收到系统回调，测量时间戳未更新"
                origin == CellOrigin.UPDATE -> "已收到系统回调，请以每条测量时间判断新鲜度"
                origin == CellOrigin.CACHE -> "读取系统缓存，请手动请求更新"
                else -> "收到系统小区通知，请以测量时间判断新鲜度"
            }
            mutable.value = mutable.value.copy(batch = batch.copy(cells = cells), origin = origin,
                receivedElapsedMs = clock.elapsed(), receivedWallMs = clock.wall(), problem = null, message = message)
        }, onFailure = ::fail)
    }
    private fun fail(error: Throwable) {
        val problem = when (error) {
            is SecurityException -> CellProblem.PERMISSION
            is UnsupportedOperationException -> CellProblem.UNAVAILABLE
            else -> CellProblem.FAILED
        }
        mutable.value = if (problem == CellProblem.PERMISSION) mutable.value.copy(
            batch = CellBatch(emptyList()), origin = null, receivedElapsedMs = null, receivedWallMs = null, problem = problem)
        else mutable.value.copy(problem = problem, message = "本次读取未成功；保留上次结果")
    }
    override fun close() {
        if (closed) return
        stop(); closed = true; source.close()
    }
}
