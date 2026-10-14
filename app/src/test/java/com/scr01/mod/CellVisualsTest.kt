package com.scr01.mod
import org.junit.Assert.*
import org.junit.Test
class CellVisualsTest {
    @Test fun referenceScaleIsBoundedAndPreservesSmallDifferences() {
        assertEquals(0f,CellVisuals.fraction(-150));assertEquals(1f,CellVisuals.fraction(-50))
        assertTrue(CellVisuals.fraction(-88)>CellVisuals.fraction(-92))
        assertNotEquals(.88f,CellVisuals.fraction(-88))
    }
    @Test fun strongerNeighborNeverBecomesServingAndTechnologiesStaySeparate() {
        val serving=CellularCells.convert(RawCell(CellRat.LTE,rsrp=-110,registered=true,connection=1))
        val stronger=CellularCells.convert(RawCell(CellRat.LTE,rsrp=-88,connection=0))
        val nr=CellularCells.convert(RawCell(CellRat.NR,rsrp=-70,connection=0))
        val cells=listOf(serving,stronger,nr)
        assertEquals(listOf(stronger),CellVisuals.neighbors(cells,CellRat.LTE))
        assertEquals(listOf(nr),CellVisuals.neighbors(cells,CellRat.NR))
        assertTrue(serving.serving);assertFalse(stronger.serving)
    }
}
