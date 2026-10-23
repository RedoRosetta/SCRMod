package com.scr01.mod

import android.content.Context

data class AutoChannelSettings(
    val channel: Int = 36,
    val applyOnBoot: Boolean = false,
    val applyAfterBoot: Boolean = false,
    val bandwidthMhz: Int? = null,
    val restoreOnHotspotStart: Boolean = false,
) {
    val targetBandwidthMhz: Int get() = bandwidthMhz ?: 80
}

object AutoChannelPreferences {
    private const val name = "auto_channel"
    private const val channelKey = "channel"
    private const val bootApplyKey = "apply_on_boot"
    private const val legacyAppStartKey = "apply_on_app_start"
    private const val bootKey = "apply_after_boot"

    fun read(context: Context): AutoChannelSettings {
        val preferences = context.getSharedPreferences(name, Context.MODE_PRIVATE)
        val channel = preferences.getInt(channelKey, 36)
            .takeIf { SupportedChannels.forChannel(it) != null } ?: 36
        val applyAfterBoot = preferences.getBoolean(bootKey, false)
        return AutoChannelSettings(
            channel = channel,
            restoreOnHotspotStart = preferences.getBoolean("restore_on_hotspot_start", false),
            bandwidthMhz = if (preferences.contains("bandwidth_mhz"))
                preferences.getInt("bandwidth_mhz", 80).takeIf { it in listOf(20, 40, 80) } else null,
            // Migrate the old setting once by reading it as the new boot-only
            // setting only when the old boot-start switch was also enabled. The
            // old key is removed on the next save.
            applyOnBoot = if (preferences.contains(bootApplyKey)) {
                preferences.getBoolean(bootApplyKey, false)
            } else {
                preferences.getBoolean(legacyAppStartKey, false) && applyAfterBoot
            },
            applyAfterBoot = applyAfterBoot,
        )
    }

    fun save(context: Context, settings: AutoChannelSettings) {
        requireNotNull(SupportedChannels.forChannel(settings.channel))
        require(settings.bandwidthMhz == null || settings.bandwidthMhz in listOf(20, 40, 80))
        val editor = context.getSharedPreferences(name, Context.MODE_PRIVATE).edit()
            .putInt(channelKey, settings.channel)
            .putBoolean(bootApplyKey, settings.applyOnBoot)
            .putBoolean(bootKey, settings.applyAfterBoot)
            .putBoolean("restore_on_hotspot_start", settings.restoreOnHotspotStart)
            .remove(legacyAppStartKey)
        if (settings.bandwidthMhz == null) editor.remove("bandwidth_mhz")
        else editor.putInt("bandwidth_mhz", settings.bandwidthMhz)
        editor.apply()
    }
}
