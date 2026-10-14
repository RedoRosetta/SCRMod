package com.scr01.mod

import java.io.Closeable
import org.junit.Assert.*
import org.junit.Test

class CellularMonitorTest {
    private class Clock : CellClock {
        var now = 100_000L
        val tasks = mutableListOf<() -> Unit>()
        override fun elapsed() = now
        override fun wall() = 1_700_000_000_000L + now
        override fun schedule(delayMs: Long, action: () -> Unit): Closeable {
            tasks += action; return Closeable { tasks.remove(action) }
        }
        fun expire() { now += 16_000; tasks.toList().forEach { it() } }
    }
    private class Source : CellInfoSource {
        var issue: CellProblem? = null
        var throwPermission = false
        var cache: ((Result<CellBatch>) -> Unit)? = null
        val requests = mutableListOf<(Result<CellBatch>) -> Unit>()
        var notification: ((Result<CellBatch>) -> Unit)? = null
        var listens = 0; var unlistens = 0; var closes = 0
        override fun problem(): CellProblem? { if (throwPermission) throw SecurityException(); return issue }
        override fun readCache(callback: (Result<CellBatch>) -> Unit) { cache = callback }
        override fun requestUpdate(callback: (Result<CellBatch>) -> Unit) { requests += callback }
        override fun listen(callback: (Result<CellBatch>) -> Unit): Closeable {
            notification = callback; listens++; return Closeable { unlistens++ }
        }
        override fun close() { closes++ }
    }
    private fun batch(time: Long = 90_000) = CellBatch(listOf(CellularCells.convert(RawCell(
        CellRat.LTE, arfcn = 100, pci = 12, identity = 123, measuredElapsedMs = time))))

    @Test fun cacheReadDoesNotPretendToBeAnUpdate() {
        val s = Source(); val m = CellularMonitor(s, Clock()); m.start(); s.cache!!(Result.success(batch()))
        assertEquals(CellOrigin.CACHE, m.state.value.origin); assertEquals(90_000L, m.state.value.batch.cells.single().measuredElapsedMs)
        assertTrue(s.requests.isEmpty()); assertEquals(100_000L, m.state.value.receivedElapsedMs)
    }
    @Test fun successfulUpdateCanHaveOldMeasurement() {
        val s = Source(); val m = CellularMonitor(s, Clock()); m.start(); s.cache!!(Result.success(batch()))
        m.refresh(); s.requests.single()(Result.success(batch()))
        assertFalse(m.state.value.refreshing); assertTrue(m.state.value.message.contains("时间戳未更新"))
    }
    @Test fun emptyUpdateIsNotNoNearbyCells() {
        val s = Source(); val m = CellularMonitor(s, Clock()); m.start(); m.refresh()
        s.requests.single()(Result.success(CellBatch(emptyList())))
        assertTrue(m.state.value.batch.cells.isEmpty()); assertTrue(m.state.value.message.contains("不能据此"))
    }
    @Test fun changedSignalWithSameTimestampIsStillNotANewMeasurement() {
        val s = Source(); val m = CellularMonitor(s, Clock()); m.start(); s.cache!!(Result.success(batch()))
        m.refresh()
        s.requests.single()(Result.success(CellBatch(batch().cells.map { it.copy(rsrp = -90) })))
        assertTrue(m.state.value.message.contains("时间戳未更新"))
    }
    @Test fun permissionAndLocationAreSeparateGates() {
        for (issue in listOf(CellProblem.PERMISSION, CellProblem.LOCATION_OFF, CellProblem.UNAVAILABLE)) {
            val s = Source().apply { this.issue = issue }; val m = CellularMonitor(s, Clock()); m.start(); m.refresh()
            assertEquals(issue, m.state.value.problem); assertEquals(0, s.listens); assertTrue(s.requests.isEmpty())
        }
    }
    @Test fun permissionRaceClearsEarlierData() {
        val s = Source(); val m = CellularMonitor(s, Clock()); m.start(); s.cache!!(Result.success(batch())); m.refresh()
        s.requests.single()(Result.failure(SecurityException()))
        assertEquals(CellProblem.PERMISSION, m.state.value.problem); assertTrue(m.state.value.batch.cells.isEmpty())
    }
    @Test fun permissionCheckExceptionDoesNotCrash() {
        val s = Source().apply { throwPermission = true }; val m = CellularMonitor(s, Clock()); m.start()
        assertEquals(CellProblem.PERMISSION, m.state.value.problem)
    }
    @Test fun failedRefreshPreservesEarlierResultAndExposesFailure() {
        val s = Source(); val m = CellularMonitor(s, Clock()); m.start(); s.cache!!(Result.success(batch())); m.refresh()
        s.requests.single()(Result.failure(IllegalStateException()))
        assertEquals(CellProblem.FAILED, m.state.value.problem); assertEquals(1, m.state.value.batch.cells.size)
    }
    @Test fun timeoutAndLateCallbackDoNotInventSuccess() {
        val s = Source(); val c = Clock(); val m = CellularMonitor(s, c); m.start(); m.refresh(); c.expire()
        assertEquals(CellProblem.TIMEOUT, m.state.value.problem); assertFalse(m.state.value.refreshing)
        s.requests.single()(Result.success(batch()))
        assertEquals(CellProblem.TIMEOUT, m.state.value.problem); assertTrue(m.state.value.batch.cells.isEmpty())
    }
    @Test fun onlyOneRequestAndCooldownAreEnforced() {
        val s = Source(); val c = Clock(); val m = CellularMonitor(s, c); m.start(); m.refresh(); m.refresh()
        assertEquals(1, s.requests.size); s.requests.first()(Result.success(batch())); m.refresh()
        assertEquals(CellProblem.THROTTLED, m.state.value.problem); assertEquals(1, s.requests.size)
        c.now += 10_000; m.refresh(); assertEquals(2, s.requests.size)
    }
    @Test fun stopUnregistersAndIgnoresCallbacksUntilRestart() {
        val s = Source(); val m = CellularMonitor(s, Clock()); m.start(); val oldCache = s.cache!!
        m.refresh(); m.stop(); oldCache(Result.success(batch())); s.requests.first()(Result.success(batch()))
        assertFalse(m.state.value.active); assertEquals(1, s.unlistens); assertTrue(m.state.value.batch.cells.isEmpty())
        m.start(); oldCache(Result.success(batch())); assertTrue(m.state.value.batch.cells.isEmpty())
        s.cache!!(Result.success(batch())); assertEquals(1, m.state.value.batch.cells.size)
    }
    @Test fun lateInitialCacheCannotOverwriteNewerNotification() {
        val s = Source(); val m = CellularMonitor(s, Clock()); m.start()
        s.notification!!(Result.success(batch(99_000))); s.cache!!(Result.success(batch(80_000)))
        assertEquals(99_000L, m.state.value.batch.cells.single().measuredElapsedMs)
        assertEquals(CellOrigin.LISTENER, m.state.value.origin)
    }
    @Test fun closeIsFinalAndCancelsTimeout() {
        val s = Source(); val c = Clock(); val m = CellularMonitor(s, c); m.start(); m.refresh(); m.close(); m.close(); m.start()
        assertEquals(1, s.closes); assertEquals(1, s.unlistens); assertTrue(c.tasks.isEmpty())
        assertFalse(m.state.value.active)
    }
}
