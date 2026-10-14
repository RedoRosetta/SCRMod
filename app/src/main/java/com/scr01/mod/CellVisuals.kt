package com.scr01.mod

/** Visual reference only, not throughput, quality percentage or a handover recommendation. */
internal object CellVisuals {
    fun key(cell:CellularCell)=listOf(cell.rat,cell.plmn,cell.identity,cell.arfcn,cell.pci,cell.tac).joinToString(":")
    fun fraction(dbm:Int):Float=((dbm+140)/80f).coerceIn(0f,1f)
    fun neighbors(cells:List<CellularCell>,rat:CellRat):List<CellularCell> =
        cells.filter { !it.serving && it.rat==rat }.sortedByDescending { it.rsrp ?: Int.MIN_VALUE }
}
