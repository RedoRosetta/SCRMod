package com.scr01.mod

import android.content.Context

data class AutoChannelSettings(
    val channel: Int = 36,
    val applyOnBoot: Boolean = false,
    val applyAfterBoot: Boolean = false,
)

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
        context.getSharedPreferences(name, Context.MODE_PRIVATE).edit()
            .putInt(channelKey, settings.channel)
            .putBoolean(bootApplyKey, settings.applyOnBoot)
            .putBoolean(bootKey, settings.applyAfterBoot)
            .remove(legacyAppStartKey)
            .apply()
    }
}
