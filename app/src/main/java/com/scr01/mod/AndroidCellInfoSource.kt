package com.scr01.mod

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.BroadcastReceiver
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.location.LocationManager
import android.os.Handler
import android.os.Build
import android.os.Looper
import android.os.SystemClock
import android.telephony.*
import java.io.Closeable
import java.util.concurrent.Executors
import java.util.concurrent.Executor

internal class AndroidCellClock : CellClock {
    private val handler = Handler(Looper.getMainLooper())
    override fun elapsed() = SystemClock.elapsedRealtime()
    override fun wall() = System.currentTimeMillis()
    override fun schedule(delayMs: Long, action: () -> Unit): Closeable {
        val runnable = Runnable(action)
        handler.postDelayed(runnable, delayMs)
        return Closeable { handler.removeCallbacks(runnable) }
    }
}

/** Public Android API only. This adapter never uses the project's Root executor or SMS services. */
@Suppress("DEPRECATION")
@SuppressLint("MissingPermission") // Every operation checks runtime permission; races are caught.
internal class AndroidCellInfoSource(context: Context) : CellInfoSource {
    private val app = context.applicationContext
    private val main = Handler(Looper.getMainLooper())
    private val worker = Executors.newSingleThreadExecutor()
    @Volatile private var closed = false
    private var registration: Closeable? = null
    private val callbackExecutor = Executor { action -> main.post { if (!closed) action.run() } }

    override fun problem(): CellProblem? = when {
        !app.packageManager.hasSystemFeature(PackageManager.FEATURE_TELEPHONY) -> CellProblem.UNAVAILABLE
        app.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED -> CellProblem.PERMISSION
        app.getSystemService(LocationManager::class.java)?.isLocationEnabled != true -> CellProblem.LOCATION_OFF
        app.getSystemService(TelephonyManager::class.java) == null -> CellProblem.UNAVAILABLE
        else -> null
    }

    private fun manager(): Pair<TelephonyManager, Int?> {
        val base = app.getSystemService(TelephonyManager::class.java)
            ?: throw UnsupportedOperationException("Telephony unavailable")
        val sub = SubscriptionManager.getDefaultDataSubscriptionId().takeIf { SubscriptionManager.isValidSubscriptionId(it) }
        return (sub?.let { base.createForSubscriptionId(it) } ?: base) to sub
    }
    private fun dispatch(callback: (Result<CellBatch>) -> Unit, action: () -> CellBatch) {
        if (closed) return
        worker.execute {
            val result = runCatching(action)
            main.post { if (!closed) callback(result) }
        }
    }
    private fun requireAccess() {
        when (problem()) {
            CellProblem.PERMISSION -> throw SecurityException("Location permission required")
            CellProblem.LOCATION_OFF -> throw SecurityException("Location disabled")
            null -> Unit
            else -> throw UnsupportedOperationException("Cell info unavailable")
        }
    }
    override fun readCache(callback: (Result<CellBatch>) -> Unit) = dispatch(callback) {
        requireAccess()
        val (phone, sub) = manager()
        batch(phone.allCellInfo.orEmpty(), phone, sub)
    }

    override fun requestUpdate(callback: (Result<CellBatch>) -> Unit) {
        requireAccess()
        val (phone, sub) = manager()
        phone.requestCellInfoUpdate(callbackExecutor, object : TelephonyManager.CellInfoCallback() {
            override fun onCellInfo(cellInfo: MutableList<CellInfo>) {
                dispatch(callback) { requireAccess(); batch(cellInfo, phone, sub) }
            }
            override fun onError(errorCode: Int, detail: Throwable?) {
                callback(Result.failure(detail ?: IllegalStateException("CellInfo error $errorCode")))
            }
        })
    }

    override fun listen(callback: (Result<CellBatch>) -> Unit): Closeable {
        registration?.close()
        val (phone, sub) = manager()
        val listener = object : PhoneStateListener() {
            override fun onCellInfoChanged(cellInfo: MutableList<CellInfo>?) {
                dispatch(callback) { requireAccess(); batch(cellInfo.orEmpty(), phone, sub) }
            }
        }
        phone.listen(listener, PhoneStateListener.LISTEN_CELL_INFO)
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                if (closed) return
                try {
                    if (problem() == null) readCache(callback)
                    else callback(Result.failure(SecurityException("Location access changed")))
                } catch (error: RuntimeException) { callback(Result.failure(error)) }
            }
        }
        try {
            val filter = IntentFilter(LocationManager.MODE_CHANGED_ACTION)
            if (Build.VERSION.SDK_INT >= 33) app.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
            else app.registerReceiver(receiver, filter)
        } catch (e: RuntimeException) {
            runCatching { phone.listen(listener, PhoneStateListener.LISTEN_NONE) }
            throw e
        }
        var released = false
        val close = Closeable {
            if (!released) {
                released = true
                runCatching { phone.listen(listener, PhoneStateListener.LISTEN_NONE) }
                runCatching { app.unregisterReceiver(receiver) }
            }
        }
        registration = close
        return close
    }

    private fun batch(info: List<CellInfo>, phone: TelephonyManager, sub: Int?): CellBatch {
        var skipped = 0
        var cells = info.mapNotNull { cell ->
            try { convert(cell).also { if (it == null) skipped++ } }
            catch (_: RuntimeException) { skipped++; null }
        }
        // Registration identity is independent evidence for vendor CellInfo flag disagreements.
        // Match RAT + exact cell identity + PLMN; never infer serving from PCI or strongest RSRP.
        val identities=runCatching {
            phone.serviceState?.networkRegistrationInfoList.orEmpty().filter {
                it.transportType==1 && (it.domain and 2)!=0 && it.isRegistered
            }.mapNotNull { registration -> when(val id=registration.cellIdentity) {
                is CellIdentityNr -> ServingCellRecord(CellRat.NR,id.nci,id.mccString.orEmpty()+id.mncString.orEmpty(),id.bands.toList())
                is CellIdentityLte -> ServingCellRecord(CellRat.LTE,id.ci.toLong(),id.mccString.orEmpty()+id.mncString.orEmpty(),id.bands.toList())
                else -> null
            } }
        }.getOrDefault(emptyList())
        if(sub!=null && SubscriptionManager.getDefaultDataSubscriptionId()!=sub)
            throw IllegalStateException("Subscription changed during cell observation")
        cells=cells.map { cell -> CellServingEvidence.enrich(cell,identities) }
        // State metadata is optional: lack of READ_PHONE_STATE must not discard CellInfo.
        val dataRat = runCatching { when (phone.dataNetworkType) {
            TelephonyManager.NETWORK_TYPE_LTE -> CellRat.LTE
            TelephonyManager.NETWORK_TYPE_NR -> CellRat.NR
            else -> null
        } }.getOrNull()
        val currentSub = SubscriptionManager.getDefaultDataSubscriptionId()
        val sameSub = sub != null && sub == currentSub
        val plmn = if (sameSub) runCatching { phone.networkOperator }.getOrNull()?.takeIf {
            it.length in 5..6 && it.all { c -> c in '0'..'9' }
        } else null
        return CellBatch(cells, if (sameSub) dataRat else null, plmn,
            if (sameSub) runCatching { phone.networkOperatorName }.getOrNull() else null,
            skipped, if (sameSub) sub else null)
    }

    private fun convert(info: CellInfo): CellularCell? {
        val raw = when (info) {
            is CellInfoLte -> {
                val id = info.cellIdentity; val signal = info.cellSignalStrength
                RawCell(CellRat.LTE, id.bands.toList(), id.earfcn, id.pci,
                    if (id.ci == CellInfo.UNAVAILABLE) Long.MAX_VALUE else id.ci.toLong(), id.tac,
                    id.mccString, id.mncString, id.operatorAlphaLong?.toString() ?: id.operatorAlphaShort?.toString(),
                    signal.rsrp, signal.rsrq, signal.rssnr,
                    registered = info.isRegistered, connection = info.cellConnectionStatus,
                    measuredElapsedMs = info.timestampMillis)
            }
            is CellInfoNr -> {
                val id = info.cellIdentity as CellIdentityNr
                val signal = info.cellSignalStrength as CellSignalStrengthNr
                RawCell(CellRat.NR, id.bands.toList(), id.nrarfcn, id.pci, id.nci, id.tac,
                    id.mccString, id.mncString, id.operatorAlphaLong?.toString() ?: id.operatorAlphaShort?.toString(),
                    signal.ssRsrp, signal.ssRsrq, signal.ssSinr,
                    signal.csiRsrp, signal.csiRsrq, signal.csiSinr,
                    info.isRegistered, info.cellConnectionStatus, info.timestampMillis)
            }
            else -> return null
        }
        return CellularCells.convert(raw)
    }

    override fun close() {
        if (closed) return
        closed = true; registration?.close(); registration = null
        worker.shutdownNow()
    }
}
