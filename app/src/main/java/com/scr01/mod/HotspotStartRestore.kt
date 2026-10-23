package com.scr01.mod

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import androidx.work.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Only an observed off/enabling -> enabled transition may request a restore. */
internal class HotspotStartEpoch(private val processId:String=java.util.UUID.randomUUID().toString()) {
    private var enabled:Boolean?=null
    private var generation=0L
    @Synchronized fun observe(state:Int,optedIn:Boolean):String? {
        if(state!=13) {
            enabled=if(state in setOf(10,11,12,14)) false else null
            generation++
            return null
        }
        if(enabled==true) return null
        val known=enabled!=null
        enabled=true;generation++
        return if(known && optedIn) "$processId:$generation" else null
    }
    @Synchronized fun accepts(token:String)=enabled==true && token=="$processId:$generation"
}

internal object HotspotStartRestore {
    private val epoch=HotspotStartEpoch()
    private var registered=false
    private val mutable=MutableStateFlow("")
    val message=mutable.asStateFlow()
    fun accepts(token:String)=epoch.accepts(token)
    fun report(message:String) { mutable.value=message }
    @Synchronized fun observe(context:Context) {
        if(registered || Build.MODEL!="SCR01" || Build.VERSION.SDK_INT!=30 || !Build.DISPLAY.endsWith("SCR01KDU1AVK2")) return
        val app=context.applicationContext
        val receiver=object:BroadcastReceiver() {
            override fun onReceive(context:Context,intent:Intent) {
                if(intent.action!="android.net.wifi.WIFI_AP_STATE_CHANGED") return
                val settings=AutoChannelPreferences.read(app)
                val token=epoch.observe(intent.getIntExtra("wifi_state",-1),settings.restoreOnHotspotStart) ?: return
                report("已排队，等待确认热点状态")
                val request=OneTimeWorkRequestBuilder<HotspotStartRestoreWorker>()
                    .setInputData(workDataOf("token" to token)).build()
                // Never cancel an in-flight kernel protection transaction. A stale queued
                // request exits via its token; the next observed start is appended.
                runCatching { WorkManager.getInstance(app).enqueueUniqueWork("scr01-hotspot-start-restore",ExistingWorkPolicy.APPEND_OR_REPLACE,request) }
                    .onFailure { report("热点恢复任务未能排队，未执行切换") }
            }
        }
        runCatching {
            @Suppress("DEPRECATION")
            app.registerReceiver(receiver,IntentFilter("android.net.wifi.WIFI_AP_STATE_CHANGED"))
            registered=true
        }.onFailure { report("热点启动监听不可用，未启用自动恢复") }
    }
}

class HotspotStartRestoreWorker(context:Context,parameters:WorkerParameters):CoroutineWorker(context,parameters) {
    override suspend fun doWork():Result {
        val token=inputData.getString("token") ?: return Result.success()
        val settings=AutoChannelPreferences.read(applicationContext)
        fun current()=HotspotStartRestore.accepts(token) && settings.restoreOnHotspotStart && AutoChannelPreferences.read(applicationContext)==settings
        if(!current()) return Result.success()
        try {
            val root=RootCommandExecutor.silent()
            val device=DeviceStatusReader.read(root) {}
            val actual=HotspotStatusReader.read(device.root==CheckState.Passed,root) {}
            if(!current()) return Result.success()
            if(actual.state!=HotspotState.On || actual.frequencyMhz?.let { it in 5000..5900 }!=true) {
                HotspotStartRestore.report("未确认5 GHz热点，未恢复目标设置")
                return Result.success()
            }
            val module=ModuleAssetVerifier.verify(applicationContext,device) {}
            if(!current()) return Result.success()
            val result=ChannelController(applicationContext,root) {}.apply(settings.channel,device,module,actual,settings.targetBandwidthMhz,::current,skipIfAlreadyMatched=true)
            if(current()) HotspotStartRestore.report(if(result.success) "已恢复：信道 ${settings.channel} · ${settings.targetBandwidthMhz} MHz"
                else "恢复未确认：${result.actualStatus?.bandwidthMhz?.let { "实际 $it MHz" } ?: result.message}")
            // A failed application never schedules retries or another channel switch.
            return Result.success()
        } catch(cancel:CancellationException) { throw cancel }
        catch(_:Exception) {
            if(current()) HotspotStartRestore.report("恢复失败，未自动重试")
            return Result.failure()
        }
    }
}
