package com.scr01.mod
internal data class ServingCellRecord(val rat:CellRat,val identity:Long,val plmn:String,val bands:List<Int>)
internal object CellServingEvidence {
    fun enrich(cell:CellularCell,records:List<ServingCellRecord>):CellularCell {
        val matches=records.filter { cell.identity!=null && cell.plmn!=null && it.rat==cell.rat && it.identity==cell.identity && it.plmn==cell.plmn }
        val bands=matches.map { it.bands.filter { band -> band in 1..1024 }.distinct().sorted() }.filter { it.isNotEmpty() }.distinct().singleOrNull()
        return cell.copy(registrationEvidence=matches.isNotEmpty(),
            bands=if(cell.bands.isEmpty() && bands!=null) bands else cell.bands,
            bandEvidence=if(cell.bands.isEmpty() && bands!=null) "REGISTRATION_IDENTITY" else cell.bandEvidence)
    }
    fun matches(cell:CellularCell,identities:List<Triple<CellRat,Long,String>>) =
        cell.identity!=null && cell.plmn!=null && identities.any {
            it.first==cell.rat && it.second==cell.identity && it.third==cell.plmn
        }
}
