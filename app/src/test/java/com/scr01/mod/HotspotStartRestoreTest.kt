package com.scr01.mod

import org.junit.Assert.*
import org.junit.Test

class HotspotStartRestoreTest {
    @Test fun existingEnabledAtProcessStartNeverChangesLiveHotspot() {
        val e=HotspotStartEpoch("a")
        assertNull(e.observe(13,true));assertNull(e.observe(13,true))
    }
    @Test fun onlyOptedInObservedRestartProducesOneRequest() {
        val e=HotspotStartEpoch("a")
        assertNull(e.observe(11,true));assertNull(e.observe(12,true))
        val t=e.observe(13,true)!!;assertTrue(e.accepts(t))
        repeat(10){assertNull(e.observe(13,true));assertTrue(e.accepts(t))}
    }
    @Test fun disabledUnknownAndFailedEventsCannotKeepOldRequestAlive() {
        for(state in listOf(10,11,12,14,-1)) {
            val e=HotspotStartEpoch("a");e.observe(11,true);val t=e.observe(13,true)!!
            assertNull(e.observe(state,true));assertFalse(e.accepts(t))
        }
    }
    @Test fun nextGenerationAndNewProcessRejectOldRequest() {
        val e=HotspotStartEpoch("a");e.observe(11,true);val old=e.observe(13,true)!!
        e.observe(11,true);val fresh=e.observe(13,true)!!
        assertFalse(e.accepts(old));assertTrue(e.accepts(fresh))
        val next=HotspotStartEpoch("b");next.observe(11,true);next.observe(13,true)
        assertFalse(next.accepts(fresh))
    }
    @Test fun optOutDoesNotRequestRestoreOrRetryOnDuplicate() {
        val e=HotspotStartEpoch("a");e.observe(11,false)
        assertNull(e.observe(13,false));assertNull(e.observe(13,true))
    }
    @Test fun legacyWidthHasExplicitEightyTargetAndOptInIsOff() {
        assertEquals(80,AutoChannelSettings().targetBandwidthMhz)
        assertFalse(AutoChannelSettings().restoreOnHotspotStart)
        assertEquals(40,AutoChannelSettings(bandwidthMhz=40).targetBandwidthMhz)
        assertFalse(SupportedChannels.forChannel(40,AutoChannelSettings().targetBandwidthMhz)!!.matchesReadback(HotspotStatus(HotspotState.On,40,5200,20),true))
    }
}
