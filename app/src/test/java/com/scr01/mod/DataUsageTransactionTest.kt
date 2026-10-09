package com.scr01.mod

import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test

class DataUsageTransactionTest {
    @Test fun explicitDefaultResetDisablesOldOverrideAndCannotReapplyIt() = runBlocking {
        for (period in DataUsagePeriod.entries) {
            val store = Store(); val device = Device()
            val controller = DataUsageMaxOverrideController(store, device::execute, period)
            assertTrue(controller.enableOrUpdate("500").success)
            assertTrue(controller.restoreFactoryDefault().success)
            assertEquals("15", device.value)
            assertFalse(store.config.enabled)
            assertFalse(store.config.recoveryPending)
            assertNull(store.config.originalValue)
            assertTrue(controller.reconcile().success)
            assertEquals("15", device.value)
        }
    }
    @Test fun uncertainDefaultResetRetains15AsRecoveryTarget() = runBlocking {
        val store = Store(); val device = Device(); device.failReadAfterWrite = true
        assertFalse(DataUsageMaxOverrideController(store, device::execute).restoreFactoryDefault().success)
        assertTrue(store.config.recoveryPending)
        assertEquals("15", store.config.originalValue)
        device.failReadAfterWrite = false
        assertTrue(DataUsageMaxOverrideController(store, device::execute).reconcile().success)
        assertEquals("15", device.value)
        assertFalse(store.config.enabled)
    }
    @Test fun defaultResetDoesNotWriteWithoutDurableRecord() = runBlocking {
        val store = Store(); store.failPrepare = true; val device = Device()
        assertFalse(DataUsageMaxOverrideController(store, device::execute).restoreFactoryDefault().success)
        assertEquals(0, device.writes)
    }
    @Test fun bothDefaultResetPeriodsUseTheirOwnSystemKey() = runBlocking {
        val writes = mutableListOf<String>()
        for (period in DataUsagePeriod.entries) {
            val device = Device()
            val controller = DataUsageMaxOverrideController(Store(), { command, timeout ->
                if (command.startsWith("settings put")) writes += command
                device.execute(command, timeout)
            }, period)
            assertTrue(controller.restoreFactoryDefault().success)
        }
        assertEquals(DataUsagePeriod.entries.map { "settings put system ${it.settingsKey} 15" }, writes)
    }
    private class Store : DataUsageMaxStore {
        var config = DataUsageMaxOverrideConfig()
        var failPrepare = false
        var failComplete = false
        override fun read() = config
        override fun prepare(target: String, original: String): Boolean {
            if (failPrepare) return false
            config = config.copy(overrideValue = target, originalValue = original, recoveryPending = true)
            return true
        }
        override fun completeApply(target: String, original: String): Boolean {
            if (failComplete) return false
            config = DataUsageMaxOverrideConfig(true, target, original, false)
            return true
        }
        override fun prepareRestore(): Boolean { config = config.copy(recoveryPending = true); return true }
        override fun completeRestore(): Boolean { config = config.copy(enabled = false, originalValue = null, recoveryPending = false); return true }
    }
    private class Device {
        var value = "150.0"
        var writes = 0
        var failReadAfterWrite = false
        var failWriteAfterMutation = false
        suspend fun execute(command: String, timeout: Long): RootCommandResult {
            if (command == "id") return RootCommandResult(command, 0, "uid=0(root)", "", false)
            if (command.startsWith("settings put")) {
                writes++; value = command.substringAfterLast(' ')
                return RootCommandResult(command, if (failWriteAfterMutation) 1 else 0, "", "", false)
            }
            return RootCommandResult(command, if (failReadAfterWrite && writes > 0) 1 else 0, value, "", false)
        }
    }
    @Test fun successfulUpdateKeepsOriginalBaselineAndRestoreClearsItOnlyAfterReadback() = runBlocking {
        val store = Store(); val device = Device(); val controller = DataUsageMaxOverrideController(store, device::execute)
        assertTrue(controller.enableOrUpdate("320").success)
        assertTrue(controller.enableOrUpdate("500").success)
        assertEquals("150.0", store.config.originalValue)
        assertTrue(controller.disableAndRestore().success)
        assertEquals("150.0", device.value)
        assertNull(store.config.originalValue)
        assertFalse(store.config.enabled)
    }
    @Test fun failedReadbackRetainsBaselineAndNewControllerRecoversInsteadOfReapplying() = runBlocking {
        val store = Store(); val device = Device(); device.failReadAfterWrite = true
        assertFalse(DataUsageMaxOverrideController(store, device::execute).enableOrUpdate("320").success)
        assertEquals("150.0", store.config.originalValue)
        assertTrue(store.config.recoveryPending)
        device.failReadAfterWrite = false
        assertTrue(DataUsageMaxOverrideController(store, device::execute).reconcile().success)
        assertEquals("150.0", device.value)
        assertFalse(store.config.enabled)
    }
    @Test fun uncertainWriteNeverDiscardsRecoveryRecord() = runBlocking {
        val store = Store(); val device = Device(); device.failWriteAfterMutation = true
        val result = DataUsageMaxOverrideController(store, device::execute).enableOrUpdate("320")
        assertFalse(result.success)
        assertEquals("320", device.value)
        assertEquals("150.0", store.config.originalValue)
        assertTrue(store.config.recoveryPending)
    }
    @Test fun failedDurablePrepareNeverWritesDevice() = runBlocking {
        val store = Store(); store.failPrepare = true; val device = Device()
        assertFalse(DataUsageMaxOverrideController(store, device::execute).enableOrUpdate("320").success)
        assertEquals(0, device.writes)
    }
    @Test fun failedCompletionRetainsRecoveryIntent() = runBlocking {
        val store = Store(); store.failComplete = true; val device = Device()
        assertFalse(DataUsageMaxOverrideController(store, device::execute).enableOrUpdate("320").success)
        assertTrue(store.config.recoveryPending)
        assertEquals("150.0", store.config.originalValue)
    }
    @Test fun failedRestoreKeepsOriginalForAnotherAttempt() = runBlocking {
        val store = Store(); val device = Device(); val controller = DataUsageMaxOverrideController(store, device::execute)
        assertTrue(controller.enableOrUpdate("320").success)
        device.failReadAfterWrite = true
        assertFalse(controller.disableAndRestore().success)
        assertEquals("150.0", store.config.originalValue)
        assertTrue(store.config.recoveryPending)
    }
    @Test fun concurrentControllersCannotReplaceTheOriginalBaseline() = runBlocking {
        val store = Store(); val device = Device()
        val runner: suspend (String, Long) -> RootCommandResult = { command, timeout -> delay(10); device.execute(command, timeout) }
        val a = async { DataUsageMaxOverrideController(store, runner).enableOrUpdate("320") }
        val b = async { DataUsageMaxOverrideController(store, runner).enableOrUpdate("500") }
        assertTrue(a.await().success); assertTrue(b.await().success)
        assertEquals("150.0", store.config.originalValue)
        assertFalse(store.config.recoveryPending)
    }
}
