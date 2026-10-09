package com.scr01.mod

import java.io.ByteArrayInputStream
import java.io.File
import java.util.concurrent.TimeUnit
import org.junit.Assert.*
import org.junit.Test

class ProcessOutputCaptureTest {
    @Test fun closingOneCaptureDoesNotStopOtherCommandsReaders() {
        fun start(): Process {
            val java = File(System.getProperty("java.home"), "bin/java" + if (System.getProperty("os.name").orEmpty().startsWith("Windows")) ".exe" else "")
            val classpath = File(requireNotNull(OutputFloodFixture::class.java.protectionDomain).codeSource.location.toURI()).path
            return ProcessBuilder(java.path, "-cp", classpath, OutputFloodFixture::class.java.name).start()
        }
        val first = start()
        val second = start()
        try {
            val firstOutput = ProcessOutputCapture(first)
            ProcessOutputCapture(second).use { secondOutput ->
                assertTrue(first.waitFor(5, TimeUnit.SECONDS))
                assertEquals(262144, firstOutput.stdout().length)
                firstOutput.close()
                assertTrue(second.waitFor(5, TimeUnit.SECONDS))
                assertEquals(262144, secondOutput.stdout().length)
                assertEquals(262144, secondOutput.stderr().length)
            }
        } finally {
            if (first.isAlive) first.destroyForcibly()
            if (second.isAlive) second.destroyForcibly()
        }
    }
    @Test fun oversizedOutputIsDrainedButOnlyBoundedBytesAreRetained() {
        val input = ByteArrayInputStream(ByteArray(100000) { 'x'.code.toByte() })
        val result = ProcessOutputCapture.readBounded(input, 64)
        assertEquals(64, result.substringBefore('\n').length)
        assertTrue(result.contains("输出已截断"))
        assertEquals(0, input.available())
    }
    @Test fun concurrentStdoutAndStderrDoNotBlockProcessExit() {
        val java = File(System.getProperty("java.home"), "bin/java" + if (System.getProperty("os.name").orEmpty().startsWith("Windows")) ".exe" else "")
        val classpath = File(requireNotNull(OutputFloodFixture::class.java.protectionDomain).codeSource.location.toURI()).path
        val process = ProcessBuilder(java.path, "-cp", classpath, OutputFloodFixture::class.java.name).start()
        try {
            ProcessOutputCapture(process).use { output ->
                assertTrue(process.waitFor(5, TimeUnit.SECONDS))
                assertEquals(0, process.exitValue())
                assertEquals(262144, output.stdout().length)
                assertEquals(262144, output.stderr().length)
            }
        } finally { if (process.isAlive) process.destroyForcibly() }
    }
}
