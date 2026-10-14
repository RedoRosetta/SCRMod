package com.scr01.mod

import org.junit.Assert.*
import org.junit.Test

class CellularCellsTest {
    private fun cell(rat: CellRat = CellRat.LTE, pci: Int = 12, arfcn: Int = 100,
        identity: Long = 123L, plmn: String = "00101", power: Int = -100,
        sinr: Int = 4, registered: Boolean = false, connection: Int = 0, time: Long = 90_000) =
        CellularCells.convert(RawCell(rat, listOf(if (rat == CellRat.NR) 77 else 1), arfcn, pci, identity, 1,
            plmn.take(3), plmn.drop(3), rsrp = power, rsrq = -10, sinr = sinr,
            registered = registered, connection = connection, measuredElapsedMs = time))

    @Test fun lteConvertsIdentityAndPublicDbUnits() {
        val c = cell(registered = true, connection = 1)
        assertEquals(CellRole.PRIMARY, c.role); assertEquals("00101", c.plmn)
        assertEquals("B1", c.bandLabel); assertEquals(4, c.sinr); assertEquals("RSSNR", c.sinrLabel)
        assertEquals(100, c.arfcn); assertEquals(123L, c.identity)
    }
    @Test fun nrRetainsLargeIdentityPciAndFrequency() {
        val c = cell(CellRat.NR, 1007, 2_100_000, 68_719_476_735L)
        assertEquals(1007, c.pci); assertEquals(2_100_000, c.arfcn)
        assertEquals(68_719_476_735L, c.identity); assertEquals("SS-SINR", c.sinrLabel)
        assertEquals(2_147_483_647L, cell(CellRat.NR, identity = 2_147_483_647L).identity)
    }
    @Test fun unavailableIsNullAndZeroIsNotMissing() {
        val missing = CellularCells.convert(RawCell(CellRat.NR))
        assertNull(missing.pci); assertNull(missing.arfcn); assertNull(missing.identity)
        assertNull(missing.rsrp); assertNull(missing.rsrq); assertNull(missing.sinr)
        assertEquals("未知", missing.bandLabel); assertNull(missing.measuredElapsedMs)
        val zero = CellularCells.convert(RawCell(CellRat.NR, pci = 0, arfcn = 0, identity = 0, tac = 0, sinr = 0))
        assertEquals(0, zero.pci); assertEquals(0, zero.arfcn); assertEquals(0L, zero.identity)
        assertEquals(0, zero.tac); assertEquals(0, zero.sinr)
    }
    @Test fun ratSpecificRangesAndInvalidStrings() {
        assertNull(cell(pci = 504).pci); assertEquals(504, cell(CellRat.NR, pci = 504).pci)
        assertNull(cell(arfcn = 262144).arfcn); assertNull(cell(CellRat.NR, arfcn = 3_279_166).arfcn)
        assertNull(cell(power = 0).rsrp)
        val c = CellularCells.convert(RawCell(CellRat.LTE, mcc = "abc", mnc = "1"))
        assertNull(c.plmn)
    }
    @Test fun ssAndCsiRemainIndependent() {
        val c = CellularCells.convert(RawCell(CellRat.NR, csiRsrp = -90, csiRsrq = -10, csiSinr = 9))
        assertNull(c.rsrp); assertNull(c.rsrq); assertNull(c.sinr); assertEquals(-90, c.csiRsrp)
    }
    @Test fun multiBandFilteringIsRatAware() {
        val nr = cell(CellRat.NR).copy(bands = listOf(77, 78))
        val lte = cell().copy(bands = listOf(78))
        assertEquals(listOf(nr), CellularCells.select(listOf(nr, lte), "n78", null, CellSort.RSRP, null))
        assertEquals(listOf(lte), CellularCells.select(listOf(nr, lte), "B78", null, CellSort.RSRP, null))
    }
    @Test fun dedupDoesNotMergeSamePciAcrossRatsFrequenciesPlmnsOrIdentity() {
        val a = cell()
        val different = listOf(a, cell(CellRat.NR), cell(arfcn = 101), cell(identity = 124), cell(plmn = "00102"))
        assertEquals(5, CellularCells.deduplicate(different).size)
        assertEquals(listOf(a.copy(measuredElapsedMs = 95_000)), CellularCells.deduplicate(listOf(a, a.copy(measuredElapsedMs = 95_000))))
    }
    @Test fun partialIdentityIsNotAWildcardAndConflictIsPreserved() {
        val a = cell(); val unknown = a.copy(identity = null)
        assertEquals(2, CellularCells.deduplicate(listOf(a, unknown)).size)
        assertEquals(2, CellularCells.deduplicate(listOf(unknown, unknown.copy(rsrp = -110))).size)
        assertEquals(2, CellularCells.deduplicate(listOf(a, a.copy(registered = true))).size)
    }
    @Test fun insufficientIdentityIsNotMerged() {
        val a = CellularCells.convert(RawCell(CellRat.NR))
        assertEquals(2, CellularCells.deduplicate(listOf(a, a)).size)
    }
    @Test fun sortKeepsServingAndPreferredNetworkFirstAndMissingLast() {
        val a = cell(power = -110); val b = cell(identity = 124, power = -90)
        val missing = cell(identity = 125).copy(rsrp = null)
        assertEquals(listOf(b, a, missing), CellularCells.select(listOf(missing, a, b), null, null, CellSort.RSRP, null))
        val serving = cell(identity = 126, power = -120, registered = true, connection = 1)
        val other = cell(identity = 127, plmn = "00102", power = -80)
        assertEquals(listOf(serving, b, other), CellularCells.select(listOf(other, b, serving), null, null, CellSort.RSRP, "00101"))
    }
    @Test fun qualitySortAndPlmnFilter() {
        val a = cell(sinr = 1); val b = cell(identity = 124, sinr = 9)
        val other = cell(plmn = "00102")
        assertEquals(listOf(b, a), CellularCells.select(listOf(other, a, b), null, CellRat.LTE, CellSort.QUALITY, "00101", true))
        assertTrue(CellularCells.select(listOf(a), null, null, CellSort.RSRP, null, true).isEmpty())
    }
    @Test fun timestampIsElapsedAndFutureOrMissingIsUnknown() {
        val a = cell(time = 90_000)
        assertEquals(10_000L, a.ageMs(100_000)); assertEquals(CellFreshness.RECENT, a.freshness(100_000))
        assertEquals(CellFreshness.STALE, a.freshness(121_000))
        assertEquals(CellFreshness.UNKNOWN, a.freshness(89_000))
        assertEquals(CellFreshness.UNKNOWN, a.copy(measuredElapsedMs = null).freshness(100_000))
    }
    @Test fun sameAndDifferentFrequencyNeedASameRatServingReference() {
        val serving = cell(registered = true, connection = 1)
        assertEquals("同频邻区", cell(identity = 124).frequencyRelation(listOf(serving)))
        assertEquals("异频邻区", cell(arfcn = 101).frequencyRelation(listOf(serving)))
        assertEquals("频率关系未知", cell(CellRat.NR).frequencyRelation(listOf(serving)))
    }
    @Test fun modeRequiresFreshAndConsistentPrimarySecondaryEvidence() {
        val nr = cell(CellRat.NR, registered = true, connection = 1)
        val lte = cell(registered = true, connection = 1)
        assertEquals("SA（系统报告）", CellularCells.mode(listOf(nr), CellRat.NR, 100_000))
        assertEquals("未知", CellularCells.mode(listOf(nr), null, 100_000))
        assertEquals("未知", CellularCells.mode(listOf(nr), CellRat.NR, 150_000))
        assertEquals("未知", CellularCells.mode(listOf(nr.copy(registered = false)), CellRat.NR, 100_000))
        assertEquals("NSA（系统报告）", CellularCells.mode(listOf(lte, nr.copy(connection = 2)), CellRat.LTE, 100_000))
        assertEquals("未知", CellularCells.mode(listOf(lte, nr.copy(connection = 0, registered = false)), CellRat.LTE, 100_000))
    }
    @Test fun emptyListsStayEmpty() {
        assertTrue(CellularCells.deduplicate(emptyList()).isEmpty())
        assertTrue(CellularCells.select(emptyList(), null, null, CellSort.RSRP, null).isEmpty())
    }
}
