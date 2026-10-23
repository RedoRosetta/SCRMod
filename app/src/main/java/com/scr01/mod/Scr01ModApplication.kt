package com.scr01.mod

import android.app.Application

class Scr01ModApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        DataUsageLimitRecovery.observe(this)
        HotspotStartRestore.observe(this)
    }
}
