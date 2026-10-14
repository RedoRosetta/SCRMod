package com.scr01.mod

import org.junit.Assert.*
import org.junit.Test

class DataUsageLimitRecoveryTest {
    @Test fun recoveryIncludesBothPeriodsRegardlessOfVisibleTab() {
        assertEquals(DataUsagePeriod.entries, DataUsageLimitRecovery.pendingPeriods {
            DataUsageMaxOverrideConfig(enabled = true, overrideValue = if (it == DataUsagePeriod.Month) "500" else "50")
        })
    }
    @Test fun disabledPeriodIsNotReenabled() {
        assertEquals(listOf(DataUsagePeriod.ThreeDays), DataUsageLimitRecovery.pendingPeriods {
            DataUsageMaxOverrideConfig(enabled = it == DataUsagePeriod.ThreeDays)
        })
    }
    @Test fun interruptedTransactionIsRecoveredEvenWhenDisabled() {
        assertEquals(listOf(DataUsagePeriod.Month), DataUsageLimitRecovery.pendingPeriods {
            DataUsageMaxOverrideConfig(recoveryPending = it == DataUsagePeriod.Month)
        })
    }
}
