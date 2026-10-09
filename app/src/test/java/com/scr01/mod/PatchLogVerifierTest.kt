package com.scr01.mod

import org.junit.Assert.assertEquals
import org.junit.Test

class PatchLogVerifierTest {
    @Test
    fun `blank log is unavailable rather than mismatched`() {
        assertEquals(PatchLogVerification.Unavailable, PatchLogVerifier.verifyActive(""))
    }

    @Test
    fun `known active line matches`() {
        val line = "scr01_idc36: patch active wrapper=${ModuleAssetVerifier.expectedWrapper} " +
            "callsite=${ModuleAssetVerifier.expectedCallsitePatched}"
        assertEquals(PatchLogVerification.Matched, PatchLogVerifier.verifyActive(line))
    }

    @Test
    fun `present but wrong bytes are rejected`() {
        val line = "scr01_idc36: patch active wrapper=${"00".repeat(32)} " +
            "callsite=${ModuleAssetVerifier.expectedCallsiteOriginal}"
        assertEquals(PatchLogVerification.Mismatched, PatchLogVerifier.verifyActive(line))
    }
}
