package com.scr01.mod
import org.junit.Assert.*
import org.junit.Test
class HotspotBandwidthTest {
    @Test fun allSupportedChannelsHaveValidTwentyAndFortyGeometry() {
        for(base in SupportedChannels.all) {
            val narrow=SupportedChannels.forChannel(base.channel,20)!!
            assertEquals(base.frequencyMhz,narrow.centerFreq1Mhz);assertEquals(0,narrow.secondaryChannelOffset)
            val forty=SupportedChannels.forChannel(base.channel,40)!!
            assertEquals(base.frequencyMhz+base.secondaryChannelOffset!!*10,forty.centerFreq1Mhz)
            assertEquals(base.secondaryChannelOffset,forty.secondaryChannelOffset)
            assertEquals(base,SupportedChannels.forChannel(base.channel,80))
            assertEquals(base.availability,forty.availability)
        }
    }
    @Test fun commandsCarryExactWidthAndOffsets() {
        assertEquals("/vendor/bin/hostapd_cli -i swlan0 chan_switch 5 5180 sec_channel_offset=0 center_freq1=5180 bandwidth=20 ht vht",SupportedChannels.forChannel(36,20)!!.hostapdCommand())
        assertEquals("/vendor/bin/hostapd_cli -i swlan0 chan_switch 5 5200 sec_channel_offset=-1 center_freq1=5190 bandwidth=40 ht vht",SupportedChannels.forChannel(40,40)!!.hostapdCommand())
    }
    @Test fun readbackMustConfirmWidthWhenExplicitlyRequested() {
        val target=SupportedChannels.forChannel(48,80)!!
        val actual=HotspotStatus(HotspotState.On,48,5240,20)
        assertFalse(target.matchesReadback(actual,true))
        assertTrue(target.matchesReadback(actual,false)) // Legacy callers retain their original gate.
        assertTrue(target.matchesReadback(actual.copy(bandwidthMhz=80),true))
        assertFalse(target.matchesReadback(actual.copy(bandwidthMhz=null),true))
        assertFalse(target.matchesReadback(actual.copy(state=HotspotState.Off,bandwidthMhz=80),true))
        assertFalse(target.matchesReadback(actual.copy(channel=44,bandwidthMhz=80),true))
    }
    @Test fun unknownAndEightyPlusEightyAreNotContiguousEighty() {
        assertEquals(20,HotspotStatusReader.bandwidth(0,0));assertEquals(40,HotspotStatusReader.bandwidth(0,1))
        assertEquals(40,HotspotStatusReader.bandwidth(0,-1));assertEquals(80,HotspotStatusReader.bandwidth(1,1))
        assertNull(HotspotStatusReader.bandwidth(null,null));assertNull(HotspotStatusReader.bandwidth(0,null))
        assertNull(HotspotStatusReader.bandwidth(3,1))
    }
    @Test fun unsupportedWidthsAndChannelsAreNotExpanded() {
        assertNull(SupportedChannels.forChannel(36,160));assertNull(SupportedChannels.forChannel(52,80));assertNull(SupportedChannels.forChannel(165,20))
        try { SupportedChannels.forChannel(36,40)!!.copy(centerFreq1Mhz=5210);fail("Bad HT40 geometry") } catch(_:IllegalArgumentException) {}
        assertNull(AutoChannelSettings().bandwidthMhz)
        assertTrue(BootSettingsPolicy.restoreChannel(AutoChannelSettings(applyAfterBoot=true,bandwidthMhz=40)))
        assertFalse(BootSettingsPolicy.restoreChannel(AutoChannelSettings(bandwidthMhz=40)))
    }
}
