package com.scr01.mod

/** A silent consumer must not sanitize, allocate or format command output. */
internal class RootOutputLog(
    private val sink: ((String) -> Unit)?,
    private val sanitize: (String) -> String = ::safeRootOutput,
) {
    fun result(value: RootCommandResult) {
        val log = sink ?: return
        log("退出码：${value.exitCode?.toString() ?: if (value.timedOut) "超时" else "不可用"}")
        if (value.stdout.isNotBlank()) log("stdout：${sanitize(value.stdout)}")
        if (value.stderr.isNotBlank()) log("stderr：${sanitize(value.stderr)}")
    }
}

private fun safeRootOutput(value: String): String = value
    .replace(Regex("(?i)(psk|password|passphrase)=[^\\s]+"), "$1=<已隐藏>")
    .replace('\n', ' ')
    .take(400)
