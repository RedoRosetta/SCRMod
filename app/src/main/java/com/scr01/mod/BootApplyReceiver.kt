package com.scr01.mod

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.work.BackoffPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

class BootApplyReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        val workManager = WorkManager.getInstance(context)
        val autoSettings = AutoChannelPreferences.read(context)
        if (BootSettingsPolicy.restoreChannel(autoSettings)) {
            val request = OneTimeWorkRequestBuilder<BootChannelWorker>()
                .setInitialDelay(90, TimeUnit.SECONDS)
                .setBackoffCriteria(BackoffPolicy.LINEAR, 30, TimeUnit.SECONDS)
                .build()
            workManager.enqueueUniqueWork(
                BootChannelWorker.workName,
                ExistingWorkPolicy.REPLACE,
                request,
            )
        }

        if (DataUsagePeriod.entries.any { BootSettingsPolicy.restoreData(autoSettings, DataUsageMaxOverridePreferences.read(context, it)) }) {
            val request = OneTimeWorkRequestBuilder<DataUsageMaxBootWorker>()
                .setInitialDelay(15, TimeUnit.SECONDS)
                .setBackoffCriteria(BackoffPolicy.LINEAR, 30, TimeUnit.SECONDS)
                .build()
            workManager.enqueueUniqueWork(
                DataUsageMaxBootWorker.workName,
                ExistingWorkPolicy.REPLACE,
                request,
            )
        }

    }
}
