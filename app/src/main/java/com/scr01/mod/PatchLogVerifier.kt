package com.scr01.mod

enum class PatchLogVerification { Matched, Mismatched, Unavailable }

object PatchLogVerifier {
    fun verifyActive(line: String): PatchLogVerification {
        if (line.isBlank()) return PatchLogVerification.Unavailable
        val normalized = line.lowercase()
        return if (
            normalized.contains("wrapper=${ModuleAssetVerifier.expectedWrapper}") &&
            normalized.contains("callsite=${ModuleAssetVerifier.expectedCallsitePatched}")
        ) PatchLogVerification.Matched else PatchLogVerification.Mismatched
    }
}
