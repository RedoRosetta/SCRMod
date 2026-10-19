package com.scr01.mod
import org.junit.Assert.*
import org.junit.Test
class RootOutputLogTest {
    @Test fun silentResultSkipsAllFormattingAndRetainsOriginalOutput() {
        var formatted=0
        val result=RootCommandResult("probe",0,"secret="+"x".repeat(100000),"error text",false)
        RootOutputLog(null) { formatted++; error("must not sanitize discarded output") }.result(result)
        assertEquals(0,formatted);assertEquals(100007,result.stdout.length);assertEquals("error text",result.stderr)
    }
    @Test fun visibleConsumerStillReceivesExitAndBothOutputs() {
        val lines=mutableListOf<String>();var formatted=0
        RootOutputLog({lines+=it}) { formatted++;it.uppercase() }.result(RootCommandResult("",1,"out","err",false))
        assertEquals(listOf("退出码：1","stdout：OUT","stderr：ERR"),lines);assertEquals(2,formatted)
    }
    @Test fun emptyOutputsAreNotFormattedAndTimeoutStillReported() {
        val lines=mutableListOf<String>()
        RootOutputLog({lines+=it}) { error("empty output must not be formatted") }.result(RootCommandResult("",null,""," ",true))
        assertEquals(listOf("退出码：超时"),lines)
    }
    @Test fun existingCredentialRedactionNewlineAndBoundArePreserved() {
        val lines=mutableListOf<String>()
        RootOutputLog({ lines+=it }).result(RootCommandResult("",0,"password=private\npassphrase=hidden psk=hide "+"z".repeat(600),"",false))
        assertFalse(lines[1].contains("private"));assertFalse(lines[1].contains("hidden"));assertFalse(lines[1].contains("psk=hide"))
        assertFalse(lines[1].contains('\n'));assertEquals("stdout：".length+400,lines[1].length)
    }
}

