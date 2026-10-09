package com.scr01.mod

import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class BootAndDataPeriodsTest {
    private class Store : DataUsageMaxStore {
        var config = DataUsageMaxOverrideConfig()
        override fun read() = config
        override fun prepare(target: String, original: String): Boolean {
            config = config.copy(overrideValue = target, originalValue = original, recoveryPending = true); return true
        }
        override fun completeApply(target: String, original: String): Boolean {
            config = DataUsageMaxOverrideConfig(true, target, original, false); return true
        }
        override fun prepareRestore(): Boolean { config = config.copy(recoveryPending = true); return true }
        override fun completeRestore(): Boolean { config = config.copy(enabled = false, originalValue = null, recoveryPending = false); return true }
    }
    private class Device {
        val values = mutableMapOf("mhs_data_usage_month_max" to "500", "mhs_data_usage_days_max" to "50.0")
        var failedReadKey: String? = null
        val writes = mutableListOf<String>()
        suspend fun execute(command: String, timeout: Long): RootCommandResult {
            if (command == "id") return RootCommandResult(command, 0, "uid=0(root)", "", false)
            val parts = command.split(' '); val key = parts[3]
            if (parts[1] == "put") { writes += key; values[key] = parts[4] }
            val failed = parts[1] == "get" && key == failedReadKey && key in writes
            return RootCommandResult(command, if (failed) 1 else 0, values[key].orEmpty(), "", false)
        }
    }
    @Test fun periodsUseSeparateSettingsAndKeepExistingMonthlyPreferenceName() {
        assertEquals("data_usage_max_override", DataUsagePeriod.Month.preferenceName)
        assertNotEquals(DataUsagePeriod.Month.preferenceName, DataUsagePeriod.ThreeDays.preferenceName)
        assertEquals("mhs_data_usage_days_max", DataUsagePeriod.ThreeDays.settingsKey)
    }
    @Test fun changesAndRestoresStayWithinTheSelectedPeriod() = runBlocking {
        val device = Device(); val monthStore = Store(); val daysStore = Store()
        val month = DataUsageMaxOverrideController(monthStore, device::execute, DataUsagePeriod.Month)
        val days = DataUsageMaxOverrideController(daysStore, device::execute, DataUsagePeriod.ThreeDays)
        assertTrue(days.enableOrUpdate("80").success)
        assertEquals("500", device.values[DataUsagePeriod.Month.settingsKey])
        assertTrue(month.enableOrUpdate("1000").success)
        assertEquals("50.0", daysStore.config.originalValue)
        assertEquals("500", monthStore.config.originalValue)
        assertTrue(days.disableAndRestore().success)
        assertEquals("50.0", device.values[DataUsagePeriod.ThreeDays.settingsKey])
        assertEquals("1000", device.values[DataUsagePeriod.Month.settingsKey])
        assertTrue(monthStore.config.enabled)
    }
    @Test fun failedThreeDayVerificationRollsBackOnlyThreeDays() = runBlocking {
        val device = Device(); val store = Store()
        device.failedReadKey = DataUsagePeriod.ThreeDays.settingsKey
        val controller = DataUsageMaxOverrideController(store, device::execute, DataUsagePeriod.ThreeDays)
        assertFalse(controller.enableOrUpdate("80").success)
        assertTrue(store.config.recoveryPending)
        device.failedReadKey = null
        assertTrue(controller.reconcile().success)
        assertEquals("50.0", device.values[DataUsagePeriod.ThreeDays.settingsKey])
        assertEquals("500", device.values[DataUsagePeriod.Month.settingsKey])
        assertTrue(device.writes.all { it == DataUsagePeriod.ThreeDays.settingsKey })
    }
    @Test fun bootMasterOffBlocksAllNormalRestoresButKeepsRecovery() {
        val settings = AutoChannelSettings(applyOnBoot = true, applyAfterBoot = false)
        assertFalse(BootSettingsPolicy.restoreChannel(settings))
        assertFalse(BootSettingsPolicy.restoreData(settings, DataUsageMaxOverrideConfig(enabled = true)))
        assertTrue(BootSettingsPolicy.restoreData(settings, DataUsageMaxOverrideConfig(recoveryPending = true)))
    }
    @Test fun bootMasterOnRestoresOnlyEnabledSettingsAndChannelOptIn() {
        val settings = AutoChannelSettings(applyAfterBoot = true, applyOnBoot = false)
        assertFalse(BootSettingsPolicy.restoreData(settings, DataUsageMaxOverrideConfig()))
        assertTrue(BootSettingsPolicy.restoreData(settings, DataUsageMaxOverrideConfig(enabled = true)))
        assertFalse(BootSettingsPolicy.restoreChannel(settings))
        assertTrue(BootSettingsPolicy.restoreChannel(settings.copy(applyOnBoot = true)))
    }
}