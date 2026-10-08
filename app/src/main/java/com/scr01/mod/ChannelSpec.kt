package com.scr01.mod

data class ChannelSpec(
    val channel: Int,
    val frequencyMhz: Int,
    val bandwidthMhz: Int,
    val centerFreq1Mhz: Int? = null,
    val secondaryChannelOffset: Int? = null,
    val availability: ChannelAvailability,
) {
    init {
        require(channel > 0)
        require(frequencyMhz > 0)
        require(bandwidthMhz in setOf(20, 40, 80, 160))
        if (bandwidthMhz == 80) {
            requireNotNull(centerFreq1Mhz)
            require(secondaryChannelOffset == 1 || secondaryChannelOffset == -1)
        } else if (bandwidthMhz == 20) {
            require(centerFreq1Mhz == frequencyMhz)
            require(secondaryChannelOffset == 0)
        }
    }

    fun hostapdCommand(): String {
        val geometry = if (centerFreq1Mhz != null && secondaryChannelOffset != null) {
            "sec_channel_offset=$secondaryChannelOffset center_freq1=$centerFreq1Mhz "
        } else {
            ""
        }
        return "/vendor/bin/hostapd_cli -i swlan0 chan_switch 5 $frequencyMhz " +
            "${geometry}bandwidth=$bandwidthMhz ht vht"
    }
}

enum class ChannelAvailability {
    Scr01LowBand,
    RegulatoryDomainDependent,
}

object SupportedChannels {
    val all: List<ChannelSpec> = listOf(
        ChannelSpec(36, 5180, 80, 5210, 1, ChannelAvailability.Scr01LowBand),
        ChannelSpec(40, 5200, 80, 5210, -1, ChannelAvailability.Scr01LowBand),
        ChannelSpec(44, 5220, 80, 5210, 1, ChannelAvailability.Scr01LowBand),
        ChannelSpec(48, 5240, 80, 5210, -1, ChannelAvailability.Scr01LowBand),
        ChannelSpec(149, 5745, 80, 5775, 1, ChannelAvailability.RegulatoryDomainDependent),
        ChannelSpec(153, 5765, 80, 5775, -1, ChannelAvailability.RegulatoryDomainDependent),
        ChannelSpec(157, 5785, 80, 5775, 1, ChannelAvailability.RegulatoryDomainDependent),
        ChannelSpec(161, 5805, 80, 5775, -1, ChannelAvailability.RegulatoryDomainDependent),
    )

    fun forChannel(channel: Int): ChannelSpec? = all.firstOrNull { it.channel == channel }
}
