package com.scr01.mod

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters

class DataUsageMaxBootWorker(context: Context, parameters: WorkerParameters) : CoroutineWorker(context, parameters) {
    override suspend fun doWork(): Result {
        val settings = AutoChannelPreferences.read(applicationContext)
        val periods = DataUsagePeriod.entries.filter {
            BootSettingsPolicy.restoreData(settings, DataUsageMaxOverridePreferences.read(applicationContext, it))
        }
        if (periods.isEmpty()) return Result.success()
        if (runAttemptCount >= 6) return Result.failure()
        var succeeded = true
        for (period in periods) {
            val result = DataUsageMaxOverrideController(applicationContext, RootCommandExecutor {}, period).reconcile()
            succeeded = result.success && succeeded
        }
        return when {
            succeeded -> Result.success()
            runAttemptCount < 5 -> Result.retry()
            else -> Result.failure()
        }
    }
    companion object { const val workName = "scr01-data-usage-max-apply" }
}