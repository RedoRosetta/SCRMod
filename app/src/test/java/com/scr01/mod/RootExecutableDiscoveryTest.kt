package com.scr01.mod

import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RootExecutableDiscoveryTest {
    @Test fun revokedRootIsNotCachedForTheAppLifetime() {
        var now = 0L
        var allowed = true
        val discovery = RootExecutableDiscovery(candidates = listOf("su"), monotonicMs = { now }, probeRunner = { _, _ ->
            RootProbeResult(true, true, if (allowed) 0 else 1, if (allowed) "uid=0(root)" else "", "", false)
        })
        assertEquals("su", discovery.resolve().executable)
        allowed = false
        now = 5000
        assertNull(discovery.resolve().executable)
    }
    @Test fun explicitRefreshRechecksAuthorizationWithoutWaitingForCacheExpiry() {
        var allowed = true
        val discovery = RootExecutableDiscovery(candidates = listOf("su"), probeRunner = { _, _ ->
            RootProbeResult(true, true, if (allowed) 0 else 1, if (allowed) "uid=0(root)" else "", "", false)
        })
        assertEquals("su", discovery.resolve().executable)
        allowed = false
        assertNull(discovery.resolve(forceRefresh = true).executable)
        allowed = true
        assertEquals("su", discovery.resolve(forceRefresh = true).executable)
    }
    @Test
    fun rootBecomesAvailableAfterFailedDiscoveryWithoutAppRestart() {
        var now = 0L
        var available = false
        val calls = AtomicInteger(0)
        val discovery = RootExecutableDiscovery(
            candidates = listOf("su"),
            monotonicMs = { now },
            probeRunner = { _, _ ->
                calls.incrementAndGet()
                if (available) RootProbeResult(true, true, 0, "uid=0(root)", "", false)
                else RootProbeResult(false, false, null, "", "not found", false)
            },
        )
        assertNull(discovery.resolve().executable)
        available = true
        now = 14_999L
        assertNull(discovery.resolve().executable)
        assertEquals(1, calls.get())
        now = 15_000L
        assertEquals("su", discovery.resolve().executable)
        assertEquals(2, calls.get())
    }

    @Test
    fun successfulCandidateIsSelectedAndCached() {
        val calls = AtomicInteger(0)
        val discovery = RootExecutableDiscovery(
            candidates = listOf("su", "/data/adb/ksu/bin/su"),
            probeRunner = { candidate, _ ->
                calls.incrementAndGet()
                if (candidate == "su") {
                    RootProbeResult(true, true, 0, "uid=0(root) gid=0(root)", "", false)
                } else {
                    RootProbeResult(false, false, null, "", "not found", false)
                }
            },
        )

        val first = discovery.resolve()
        val second = discovery.resolve()

        assertEquals("su", first.executable)
        assertEquals("su", second.executable)
        assertEquals(1, calls.get())
        assertTrue(first.successfulProbe?.stdout?.contains("uid=0") == true)
    }

    @Test
    fun nonRootCandidateFallsThroughAndFailureIsCached() {
        val calls = AtomicInteger(0)
        val discovery = RootExecutableDiscovery(
            candidates = listOf("su", "/data/adb/ksu/bin/su"),
            probeRunner = { candidate, _ ->
                calls.incrementAndGet()
                if (candidate == "su") {
                    RootProbeResult(true, true, 0, "uid=2000(shell)", "", false)
                } else {
                    RootProbeResult(false, false, null, "", "not found", false)
                }
            },
        )

        val first = discovery.resolve()
        val second = discovery.resolve()

        assertNull(first.executable)
        assertNull(second.executable)
        assertEquals(2, calls.get())
        assertEquals("stdout 未包含 uid=0", first.failures.first().reason)
    }

    @Test
    fun concurrentFirstCallersShareOneDiscovery() {
        val started = CountDownLatch(1)
        val release = CountDownLatch(1)
        val calls = AtomicInteger(0)
        val discovery = RootExecutableDiscovery(
            candidates = listOf("su", "/data/adb/ksu/bin/su"),
            probeRunner = { _, _ ->
                calls.incrementAndGet()
                started.countDown()
                assertTrue(release.await(2, TimeUnit.SECONDS))
                RootProbeResult(true, true, 0, "uid=0(root)", "", false)
            },
        )
        val pool = Executors.newFixedThreadPool(8)
        try {
            val futures = (1..8).map { pool.submit<RootDiscoveryResult> { discovery.resolve() } }
            assertTrue(started.await(2, TimeUnit.SECONDS))
            release.countDown()
            futures.forEach { assertEquals("su", it.get(2, TimeUnit.SECONDS).executable) }
            assertEquals(1, calls.get())
        } finally {
            release.countDown()
            pool.shutdownNow()
        }
    }
}
