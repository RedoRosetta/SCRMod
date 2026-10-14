package com.scr01.mod
internal object CellConnectionLabel {
    @Suppress("UNUSED_PARAMETER")
    fun current(cells:List<CellularCell>,dataRat:CellRat?):String {
        val labels=cells.filter { it.serving && it.role!=CellRole.CONFLICT && it.bands.isNotEmpty() }.map {
            (if(it.rat==CellRat.NR) "5G NR" else "4G LTE")+" · "+it.bandLabel
        }.distinct()
        return if(labels.isEmpty()) "" else "当前连接："+labels.joinToString(" / ")
    }
}
