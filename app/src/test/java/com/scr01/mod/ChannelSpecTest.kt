package com.scr01.mod

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ChannelSpecTest {
    @Test
    fun `supported channel table has correct geometry`() {
        val expected = listOf(
            listOf(36, 5180, 5210, 1, 80),
            listOf(40, 5200, 5210, -1, 80),
            listOf(44, 5220, 5210, 1, 80),
            listOf(48, 5240, 5210, -1, 80),
            listOf(149, 5745, 5775, 1, 80),
            listOf(153, 5765, 5775, -1, 80),
            listOf(157, 5785, 5775, 1, 80),
            listOf(161, 5805, 5775, -1, 80),
        )

        assertEquals(expected, SupportedChannels.all.map {
            listOf(it.channel, it.frequencyMhz, it.centerFreq1Mhz, it.secondaryChannelOffset, it.bandwidthMhz)
        })
    }

    @Test
    fun `commands are generated from each channel spec`() {
        SupportedChannels.all.filter { it.bandwidthMhz == 80 }.forEach { spec ->
            assertEquals(
                "/vendor/bin/hostapd_cli -i swlan0 chan_switch 5 ${spec.frequencyMhz} " +
                    "sec_channel_offset=${spec.secondaryChannelOffset} center_freq1=${spec.centerFreq1Mhz} " +
                    "bandwidth=80 ht vht",
                spec.hostapdCommand(),
            )
        }
        assertNull(SupportedChannels.forChannel(165))
    }

    @Test
    fun `unsupported channel is rejected`() {
        assertNull(SupportedChannels.forChannel(52))
    }
}
