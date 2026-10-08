package com.scr01.mod

/** Shared boot policy; recovery rolls back an interrupted transaction even when auto-apply is off. */
internal object BootSettingsPolicy {
    fun restoreChannel(settings: AutoChannelSettings) = settings.applyAfterBoot && settings.applyOnBoot
    fun restoreData(settings: AutoChannelSettings, config: DataUsageMaxOverrideConfig) =
        config.recoveryPending || (settings.applyAfterBoot && config.enabled)
}