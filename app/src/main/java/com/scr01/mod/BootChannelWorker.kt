package com.scr01.mod

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters

class BootChannelWorker(context: Context, parameters: WorkerParameters) :
    CoroutineWorker(context, parameters) {
    override suspend fun doWork(): Result {
        val settings = AutoChannelPreferences.read(applicationContext)
        if (!BootSettingsPolicy.restoreChannel(settings)) return Result.success()
        if (runAttemptCount >= 6) return Result.failure()

        val executor = RootCommandExecutor {}
        val device = DeviceStatusReader.read(executor) {}
        if (device.root != CheckState.Passed) return Result.retry()
        val hotspot = HotspotStatusReader.read(true, executor) {}
        if (hotspot.state != HotspotState.On) return Result.retry()
        val module = ModuleAssetVerifier.verify(applicationContext, device) {}
        if (!module.safetyGatePassed) return Result.failure()

        val result = ChannelController(applicationContext, executor) {}
            .apply(settings.channel, device, module, hotspot)
        return if (result.success) Result.success() else Result.failure()
    }

    companion object {
        const val workName = "scr01-boot-channel-apply"
    }
}
