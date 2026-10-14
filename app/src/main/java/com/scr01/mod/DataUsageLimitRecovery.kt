package com.scr01.mod

import android.content.Context
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.Log
import androidx.work.*
import java.util.concurrent.TimeUnit

internal object DataUsageLimitRecovery {
    fun pendingPeriods(read: (DataUsagePeriod) -> DataUsageMaxOverrideConfig) =
        DataUsagePeriod.entries.filter { period ->
            read(period).let { it.enabled || it.recoveryPending }
        }

    suspend fun reconcile(context: Context, executor: RootCommandExecutor): Map<DataUsagePeriod, DataUsageMaxOperationResult> =
        pendingPeriods { DataUsageMaxOverridePreferences.read(context, it) }.associateWith { period ->
            DataUsageMaxOverrideController(context, executor, period).reconcile()
        }

    fun observe(context: Context) {
        val app = context.applicationContext
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                if (pendingPeriods { DataUsageMaxOverridePreferences.read(app, it) }.isEmpty()) return
                // KEEP avoids cancelling a running settings transaction on its own notification.
                WorkManager.getInstance(app).enqueueUniqueWork("data-limit-reconcile",
                    ExistingWorkPolicy.KEEP,
                    OneTimeWorkRequestBuilder<DataUsageLimitRecoveryWorker>()
                        .setInitialDelay(3, TimeUnit.SECONDS)
                        .setBackoffCriteria(BackoffPolicy.LINEAR, 10, TimeUnit.SECONDS).build())
            }
        }
        for (period in DataUsagePeriod.entries) {
            app.contentResolver.registerContentObserver(Settings.System.getUriFor(period.settingsKey), false, observer)
        }
    }
}

class DataUsageLimitRecoveryWorker(context: Context, parameters: WorkerParameters) : CoroutineWorker(context, parameters) {
    override suspend fun doWork(): Result {
        val results = DataUsageLimitRecovery.reconcile(applicationContext, RootCommandExecutor {})
        results.forEach { (period, result) ->
            Log.i("SCR01Mod.DataLimit", "RECOVER=${period.name}:SUCCESS=${result.success}")
        }
        return if (results.values.all { it.success }) Result.success()
        else if (runAttemptCount < 5) Result.retry() else Result.failure()
    }
}
