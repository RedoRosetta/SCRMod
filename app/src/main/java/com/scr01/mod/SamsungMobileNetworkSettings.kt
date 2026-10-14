package com.scr01.mod

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager

/** Same public activity used by Samsung Band Selection 1.4.0. No root fallback. */
internal object SamsungMobileNetworkSettings {
    private val component = ComponentName(
        "com.samsung.android.app.telephonyui",
        "com.samsung.android.app.telephonyui.hiddennetworksetting.MainActivity",
    )

    @Suppress("DEPRECATION")
    fun unavailableReason(context: Context): String? = try {
        val pm = context.packageManager
        val info = pm.getActivityInfo(component, 0)
        when {
            !info.enabled || !info.applicationInfo.enabled -> "系统频段选择入口已停用"
            !info.exported -> "系统不允许外部应用打开频段选择"
            info.permission != null && pm.checkPermission(info.permission, context.packageName) !=
                PackageManager.PERMISSION_GRANTED -> "系统频段选择需要额外权限"
            else -> null
        }
    } catch (_: PackageManager.NameNotFoundException) {
        "此固件未提供可用的系统频段选择入口"
    } catch (_: SecurityException) {
        "系统限制访问频段选择入口"
    }

    fun open(context: Context): Result<Unit> = runCatching {
        check(unavailableReason(context) == null) { unavailableReason(context).orEmpty() }
        context.startActivity(Intent(Intent.ACTION_MAIN).setComponent(component))
    }
}
