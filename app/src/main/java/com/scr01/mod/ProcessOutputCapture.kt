package com.scr01.mod

import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.util.concurrent.Executors
import java.util.concurrent.Future
import java.util.concurrent.TimeUnit

/** Drain both pipes while the command runs, retaining bounded output. */
internal class ProcessOutputCapture(private val process: Process) : AutoCloseable {
    private val stdout = readers.submit<String> { readBounded(process.inputStream) }
    private val stderr = readers.submit<String> { readBounded(process.errorStream) }
    fun stdout(): String = collect(stdout)
    fun stderr(): String = collect(stderr)
    private fun collect(task: Future<String>): String = try {
        task.get(1, TimeUnit.SECONDS)
    } catch (_: Exception) { "[输出读取未完成]" }
    override fun close() {
        runCatching { process.inputStream.close() }
        runCatching { process.errorStream.close() }
        runCatching { process.outputStream.close() }
        stdout.cancel(true)
        stderr.cancel(true)
    }
    companion object {
        // Persistent watchers must not starve other commands' pipe readers.
        // Reuse idle threads; the cached pool expires them after 60 seconds.
        private val readers = Executors.newCachedThreadPool { task ->
            Thread(task, "scrmod-command-output").apply { isDaemon = true }
        }
        internal fun readBounded(input: InputStream, limit: Int = 1024 * 1024): String {
            require(limit >= 0)
            val output = ByteArrayOutputStream()
            val buffer = ByteArray(8192)
            var truncated = false
            input.use {
                while (true) {
                    val count = it.read(buffer)
                    if (count < 0) break
                    val keep = count.coerceAtMost((limit - output.size()).coerceAtLeast(0))
                    output.write(buffer, 0, keep)
                    if (keep < count) truncated = true
                }
            }
            return output.toString("UTF-8").trim() + if (truncated) "\n[输出已截断]" else ""
        }
    }
}
