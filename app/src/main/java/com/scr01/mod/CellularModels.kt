package com.scr01.mod

internal enum class CellRat { LTE, NR }
internal enum class CellOrigin(val label: String) { CACHE("系统缓存"), UPDATE("更新回调"), LISTENER("系统通知") }
internal enum class CellRole(val label: String) {
    PRIMARY("主服务"), SECONDARY("次服务"), REGISTERED("服务"), NEIGHBOR("邻区"), CONFLICT("状态冲突")
}
internal enum class CellSort(val label: String) { RSRP("信号强度"), QUALITY("信噪比") }
internal enum class CellFreshness(val label: String) { RECENT("近期测量"), STALE("历史测量"), UNKNOWN("时间未知") }

/** Raw Android values pass through here so sentinel/range handling can be tested on the JVM. */
internal data class RawCell(
    val rat: CellRat, val bands: List<Int> = emptyList(), val arfcn: Int = Int.MAX_VALUE,
    val pci: Int = Int.MAX_VALUE, val identity: Long = Long.MAX_VALUE, val tac: Int = Int.MAX_VALUE,
    val mcc: String? = null, val mnc: String? = null, val operator: String? = null,
    val rsrp: Int = Int.MAX_VALUE, val rsrq: Int = Int.MAX_VALUE, val sinr: Int = Int.MAX_VALUE,
    val csiRsrp: Int = Int.MAX_VALUE, val csiRsrq: Int = Int.MAX_VALUE, val csiSinr: Int = Int.MAX_VALUE,
    val registered: Boolean = false, val connection: Int = Int.MAX_VALUE,
    val measuredElapsedMs: Long = Long.MAX_VALUE,
)

internal data class CellularCell(
    val rat: CellRat, val bands: List<Int>, val arfcn: Int?, val pci: Int?, val identity: Long?,
    val tac: Int?, val mcc: String?, val mnc: String?, val operator: String?,
    val rsrp: Int?, val rsrq: Int?, val sinr: Int?, val csiRsrp: Int?, val csiRsrq: Int?, val csiSinr: Int?,
    val registered: Boolean, val connection: Int?, val measuredElapsedMs: Long?,
    val registrationEvidence: Boolean = false,
    val bandEvidence: String = "CELL_INFO",
) {
    val plmn: String? get() = if (mcc != null && mnc != null) mcc + mnc else null
    val bandLabel: String get() = bands.takeIf { it.isNotEmpty() }?.joinToString("/") {
        if (rat == CellRat.NR) "n$it" else "B$it"
    } ?: "未知"
    val role: CellRole get() = when {
        registrationEvidence -> CellRole.REGISTERED
        connection in listOf(1, 2) && !registered -> CellRole.CONFLICT
        registered && connection == 0 -> CellRole.CONFLICT
        connection == 1 -> CellRole.PRIMARY
        connection == 2 -> CellRole.SECONDARY
        registered -> CellRole.REGISTERED
        else -> CellRole.NEIGHBOR
    }
    val serving: Boolean get() = registrationEvidence || registered || connection in listOf(1, 2)
    val sinrLabel: String get() = if (rat == CellRat.NR) "SS-SINR" else "RSSNR"
    val measurementKey: List<Any?> get() = listOf(rat, arfcn, pci, identity, tac, mcc, mnc, registered, connection)
    fun ageMs(now: Long): Long? = measuredElapsedMs?.takeIf { it > 0 && it <= now }?.let { now - it }
    fun freshness(now: Long): CellFreshness = when (val age = ageMs(now)) {
        null -> CellFreshness.UNKNOWN
        in 0..30_000 -> CellFreshness.RECENT
        else -> CellFreshness.STALE
    }
    fun frequencyRelation(all: List<CellularCell>): String {
        if (serving) return role.label
        val servingChannels = all.filter { it.serving && it.rat == rat }.mapNotNull { it.arfcn }.toSet()
        return when {
            arfcn == null || servingChannels.isEmpty() -> "频率关系未知"
            arfcn in servingChannels -> "同频邻区"
            else -> "异频邻区"
        }
    }
}

internal object CellularCells {
    private fun number(value: Int, range: IntRange): Int? = value.takeIf { it != Int.MAX_VALUE && it in range }
    private fun digits(value: String?, length: IntRange): String? = value?.takeIf {
        it.length in length && it.all { c -> c in '0'..'9' }
    }
    fun convert(raw: RawCell): CellularCell {
        val nr = raw.rat == CellRat.NR
        return CellularCell(raw.rat, raw.bands.filter { it in 1..1024 }.distinct().sorted(),
            number(raw.arfcn, 0..if (nr) 3_279_165 else 262_143), number(raw.pci, 0..if (nr) 1007 else 503),
            raw.identity.takeIf { it != Long.MAX_VALUE &&
                it in 0..if (nr) 68_719_476_735L else 268_435_455L },
            number(raw.tac, 0..if (nr) 16_777_215 else 65_535), digits(raw.mcc, 3..3), digits(raw.mnc, 2..3),
            raw.operator?.takeIf { it.isNotBlank() }, number(raw.rsrp, if (nr) -156..-31 else -140..-43),
            number(raw.rsrq, if (nr) -43..20 else -34..3), number(raw.sinr, if (nr) -23..40 else -20..30),
            if (nr) number(raw.csiRsrp, -156..-31) else null,
            if (nr) number(raw.csiRsrq, -43..20) else null,
            if (nr) number(raw.csiSinr, -23..23) else null,
            raw.registered, number(raw.connection, 0..2),
            raw.measuredElapsedMs.takeIf { it > 0 && it != Long.MAX_VALUE })
    }

    /** Exact identity only: unknown fields never act as wildcards across different cells/PLMNs. */
    fun deduplicate(cells: List<CellularCell>): List<CellularCell> = cells.groupBy {
        it.measurementKey
    }.values.flatMap { group ->
        val cell = group.first()
        // No frequency/PCI/identity means there is insufficient evidence to merge even equal entries.
        if (cell.identity == null && (cell.arfcn == null || cell.pci == null)) group
        else if (cell.identity == null) group.distinct()
        else listOf(group.maxWith(compareBy<CellularCell> { it.measuredElapsedMs ?: -1 }
            .thenBy { listOf(it.rsrp, it.rsrq, it.sinr).count { v -> v != null } }))
    }

    fun select(cells: List<CellularCell>, band: String?, rat: CellRat?, sort: CellSort,
        preferredPlmn: String?, onlyPreferred: Boolean = false): List<CellularCell> = cells.filter {
        (rat == null || it.rat == rat) && (band == null || band in bandKeys(it)) &&
            (!onlyPreferred || (preferredPlmn != null && it.plmn == preferredPlmn))
    }.sortedWith(compareByDescending<CellularCell> { it.serving }
        .thenByDescending { preferredPlmn != null && it.plmn == preferredPlmn }
        .thenByDescending { (if (sort == CellSort.RSRP) it.rsrp else it.sinr) ?: Int.MIN_VALUE }
        .thenBy { it.rat.name }.thenBy { it.arfcn ?: Int.MAX_VALUE }.thenBy { it.pci ?: Int.MAX_VALUE })

    fun bandKeys(cell: CellularCell): List<String> = cell.bands.map { if (cell.rat == CellRat.NR) "n$it" else "B$it" }

    fun mode(cells: List<CellularCell>, dataRat: CellRat?, now: Long): String {
        // Cached/ambiguous cells and network branding alone cannot establish SA/NSA.
        val current = cells.filter { it.freshness(now) == CellFreshness.RECENT }
        if (current.any { it.role == CellRole.CONFLICT }) return "未知"
        val nrPrimary = current.any { it.rat == CellRat.NR && it.role == CellRole.PRIMARY }
        val ltePrimary = current.any { it.rat == CellRat.LTE && it.role == CellRole.PRIMARY }
        val nrSecondary = current.any { it.rat == CellRat.NR && it.role == CellRole.SECONDARY }
        return when {
            dataRat == CellRat.NR && nrPrimary && !ltePrimary -> "SA（系统报告）"
            dataRat == CellRat.LTE && ltePrimary && nrSecondary && !nrPrimary -> "NSA（系统报告）"
            else -> "未知"
        }
    }
}

internal data class CellBatch(val cells: List<CellularCell>, val dataRat: CellRat? = null,
    val networkPlmn: String? = null, val networkName: String? = null, val skipped: Int = 0,
    val subscriptionId: Int? = null)
