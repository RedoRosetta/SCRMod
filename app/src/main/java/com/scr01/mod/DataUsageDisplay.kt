package com.scr01.mod

import java.math.BigDecimal

/**
 * Formats a data-usage value for SCR01Mod UI text only.
 * The persisted/raw value is never changed by this function.
 */
internal fun formatDataUsageGb(raw: String): String {
    val trimmed = raw.trim()
    if (trimmed.isEmpty()) return trimmed
    return runCatching {
        BigDecimal(trimmed).stripTrailingZeros().toPlainString()
    }.getOrDefault(trimmed)
}
