package com.scr01.mod

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DataUsageMaxOverrideTest {
    @Test
    fun `display formatter removes only insignificant trailing zeros`() {
        assertEquals("325", formatDataUsageGb("325.00"))
        assertEquals("325.5", formatDataUsageGb("325.50"))
        assertEquals("325.25", formatDataUsageGb("325.25"))
        assertEquals("500", formatDataUsageGb("500.00"))
    }

    @Test
    fun `target accepts supported integer range and normalizes whitespace`() {
        assertEquals("1", DataUsageMaxValidator.parseTarget(" 1 ").getOrThrow())
        assertEquals("320", DataUsageMaxValidator.parseTarget("320").getOrThrow())
        assertEquals("2000", DataUsageMaxValidator.parseTarget("2000").getOrThrow())
    }

    @Test
    fun `target rejects empty non integer zero negative and above maximum`() {
        listOf("", "  ", "320.5", "0", "-1", "2001", "99999", "abc").forEach {
            assertTrue("Expected rejection for '$it'", DataUsageMaxValidator.parseTarget(it).isFailure)
        }
    }

    @Test
    fun `Samsung values compare numerically across settings formatting`() {
        assertTrue(DataUsageMaxValidator.valuesEqual("320", "320.0"))
        assertTrue(DataUsageMaxValidator.valuesEqual("2000.0", "2000"))
        assertFalse(DataUsageMaxValidator.valuesEqual("320", "321"))
    }

    @Test
    fun `Samsung value parser rejects invalid and out of range values`() {
        assertEquals(150.0f, DataUsageMaxValidator.parseSamsungValue("150.0"))
        assertEquals(null, DataUsageMaxValidator.parseSamsungValue("null"))
        assertEquals(null, DataUsageMaxValidator.parseSamsungValue("0"))
        assertEquals(null, DataUsageMaxValidator.parseSamsungValue("2001"))
    }
}
