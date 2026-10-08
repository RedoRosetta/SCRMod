package com.scr01.mod

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.withContext
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.Inet4Address
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.SocketTimeoutException
import java.nio.ByteBuffer
import java.security.SecureRandom

internal data class StunReply(val mapped: InetSocketAddress, val other: InetSocketAddress?)

/** RFC 5389 framing and RFC 5780 IPv4 behavior discovery; no Nintendo grade inference. */
internal object StunCodec {
    const val cookie = 0x2112a442
    fun request(id: ByteArray, change: Int = 0): ByteArray {
        require(id.size == 12)
        return ByteBuffer.allocate(if (change == 0) 20 else 28).apply {
            putShort(1); putShort(if (change == 0) 0 else 8); putInt(cookie); put(id)
            if (change != 0) { putShort(3); putShort(4); putInt(change) }
        }.array()
    }
    fun parse(bytes: ByteArray, id: ByteArray): StunReply? {
        if (bytes.size < 20 || id.size != 12) return null
        val b = ByteBuffer.wrap(bytes)
        if (b.short.toInt() and 0xffff != 0x101) return null
        val length = b.short.toInt() and 0xffff
        if (length % 4 != 0 || length + 20 != bytes.size || b.int != cookie) return null
        val tx = ByteArray(12); b.get(tx)
        if (!tx.contentEquals(id)) return null
        var plain: InetSocketAddress? = null
        var mapped: InetSocketAddress? = null
        var other: InetSocketAddress? = null
        while (b.remaining() >= 4) {
            val kind = b.short.toInt() and 0xffff
            val size = b.short.toInt() and 0xffff
            val padded = (size + 3) and -4
            if (padded > b.remaining()) return null
            val start = b.position()
            if (kind in listOf(1, 0x20, 0x802c) && size == 8 && bytes[start + 1].toInt() == 1) {
                b.get(); b.get()
                var port = b.short.toInt() and 0xffff
                var ip = b.int
                if (kind == 0x20) { port = port xor (cookie ushr 16); ip = ip xor cookie }
                if (port == 0) return null
                val address = InetSocketAddress(InetAddress.getByAddress(ByteBuffer.allocate(4).putInt(ip).array()), port)
                when (kind) { 1 -> plain = address; 0x20 -> mapped = address; else -> other = address }
            }
            b.position(start + padded)
        }
        if (b.hasRemaining()) return null
        return (mapped ?: plain)?.let { StunReply(it, other) }
    }
}

internal enum class NatMapping { INDEPENDENT, ADDRESS_DEPENDENT, ADDRESS_PORT_DEPENDENT, UNKNOWN }
internal enum class NatFiltering { INDEPENDENT, ADDRESS_DEPENDENT, ADDRESS_PORT_DEPENDENT, UNKNOWN }
internal data class NatTestResult(
    val title: String, val detail: String, val endpoint: String = "", val server: String = "",
    val mapping: NatMapping = NatMapping.UNKNOWN,
    val filtering: NatFiltering = NatFiltering.UNKNOWN,
    val direct: Boolean = false,
)

// App letter labels for observed NAT behavior; no console-specific test is performed.
internal fun NatTestResult.letterType(): String? = when {
    direct -> "A"
    mapping == NatMapping.UNKNOWN -> null
    mapping == NatMapping.ADDRESS_PORT_DEPENDENT -> "D"
    mapping == NatMapping.ADDRESS_DEPENDENT -> null
    filtering == NatFiltering.INDEPENDENT -> "A"
    filtering == NatFiltering.ADDRESS_DEPENDENT -> "B"
    filtering == NatFiltering.ADDRESS_PORT_DEPENDENT -> "C"
    else -> null
}

internal object NatDiscovery {
    private val dnsWorkers = Executors.newFixedThreadPool(2) { task -> Thread(task, "scrmod-nat-dns").apply { isDaemon = true } }
    val defaultServers = listOf("stun.hot-chilli.net:3478", "stun.miwifi.com:3478", "stun.l.google.com:19302")
    fun classify(mapping: NatMapping, filtering: NatFiltering): String = when {
        mapping == NatMapping.UNKNOWN -> "无法判定 NAT 类型"
        mapping == NatMapping.ADDRESS_PORT_DEPENDENT -> "对称型 NAT"
        mapping == NatMapping.ADDRESS_DEPENDENT -> "地址相关 NAT"
        filtering == NatFiltering.INDEPENDENT -> "全锥型 NAT"
        filtering == NatFiltering.ADDRESS_DEPENDENT -> "疑似地址受限 NAT"
        filtering == NatFiltering.ADDRESS_PORT_DEPENDENT -> "疑似端口受限 NAT"
        else -> "映射独立，过滤类型未知"
    }
    fun serverAddress(value: String): InetSocketAddress {
        val parts = value.trim().split(':')
        require(parts.size == 2 && parts[0].matches(Regex("[a-zA-Z0-9.-]{1,253}"))) { "服务器格式应为 域名:端口" }
        val port = parts[1].toIntOrNull()
        require(port != null && port in 1..65535) { "端口应为 1–65535" }
        val lookup = dnsWorkers.submit<Array<InetAddress>> { InetAddress.getAllByName(parts[0]) }
        val addresses = try { lookup.get(3, TimeUnit.SECONDS) } finally { if (!lookup.isDone) lookup.cancel(true) }
        val ip = addresses.firstOrNull { it is Inet4Address }
            ?: error("服务器没有 IPv4 地址")
        return InetSocketAddress(ip, port)
    }
    suspend fun test(custom: String, progress: (String) -> Unit): NatTestResult = runInterruptible(Dispatchers.IO) {
        val deadline = System.nanoTime() + 45_000_000_000L
        val servers = if (custom.isBlank()) defaultServers else listOf(custom.trim())
        var partial: NatTestResult? = null
        val errors = mutableListOf<String>()
        for (server in servers) {
            progress("正在检测 $server")
            try {
                ensureTestActive(deadline)
                val result = discover(serverAddress(server), server, progress, deadline)
                if (result.title != "无法判定 NAT 类型") return@runInterruptible result
                partial = partial ?: result
            } catch (e: CancellationException) { throw e }
            catch (e: InterruptedException) { throw CancellationException("检测已取消", e) }
            catch (e: Exception) { errors += "$server：${e.message ?: "连接失败"}" }
        }
        partial ?: NatTestResult("检测未完成", errors.joinToString("\n"))
    }
    private fun ensureTestActive(deadline: Long) {
        if (Thread.currentThread().isInterrupted) throw CancellationException("检测已取消")
        if (System.nanoTime() >= deadline) throw java.net.SocketTimeoutException("检测超时")
    }
    private fun exchange(socket: DatagramSocket, target: InetSocketAddress, source: InetSocketAddress = target, change: Int = 0, deadline: Long): StunReply? {
        val id = ByteArray(12).also { SecureRandom().nextBytes(it) }
        val request = StunCodec.request(id, change)
        repeat(3) { attempt ->
            ensureTestActive(deadline)
            socket.send(DatagramPacket(request, request.size, target))
            val until = System.nanoTime() + (600L shl attempt) * 1_000_000L
            while (System.nanoTime() < until) {
                ensureTestActive(deadline)
                socket.soTimeout = ((until - System.nanoTime()) / 1_000_000L).toInt().coerceIn(1, 250)
                val packet = DatagramPacket(ByteArray(2048), 2048)
                try { socket.receive(packet) } catch (_: SocketTimeoutException) { continue }
                if (packet.socketAddress != source) continue
                val response = StunCodec.parse(packet.data.copyOf(packet.length), id)
                if (response != null) return response
            }
        }
        return null
    }
    private fun discover(primary: InetSocketAddress, server: String, progress: (String) -> Unit, deadline: Long): NatTestResult {
        return DatagramSocket().use { socket ->
            val localIp = DatagramSocket().use { route -> route.connect(primary); route.localAddress }
            discoverWith(primary, server, progress, InetSocketAddress(localIp, socket.localPort)) { target, source, change ->
                exchange(socket, target, source, change, deadline)
            }
        }
    }
    internal fun discoverWith(
        primary: InetSocketAddress, server: String, progress: (String) -> Unit,
        local: InetSocketAddress? = null,
        query: (InetSocketAddress, InetSocketAddress, Int) -> StunReply?,
    ): NatTestResult {
            val first = query(primary, primary, 0) ?: error("UDP 请求无有效回应")
            val endpoint = "${first.mapped.address.hostAddress}:${first.mapped.port}"
            if (first.mapped == local) return NatTestResult("公网直连（未检测到 NAT）", "当前出口未检测到地址转换。", endpoint, server, direct = true)
            val other = first.other
            if (other == null || other.address == primary.address || other.port == primary.port ||
                other.address.isAnyLocalAddress || other.address.isLoopbackAddress || other.address.isMulticastAddress || other.address.isSiteLocalAddress) {
                return NatTestResult("无法判定 NAT 类型", "已获得公网映射；服务器未提供完整行为检测，自动尝试其他服务器。", endpoint, server)
            }
            // Filtering MUST precede contacting OTHER-ADDRESS on this socket.
            progress("正在检测入站限制")
            val changed = query(primary, other, 6)
            val filtering = if (changed != null) NatFiltering.INDEPENDENT else {
                val alternatePort = InetSocketAddress(primary.address, other.port)
                val portOnly = query(primary, alternatePort, 2)
                // A healthy baseline is required before drawing even a tentative conclusion from loss.
                val healthy = query(primary, primary, 0)?.mapped == first.mapped
                if (!healthy) NatFiltering.UNKNOWN
                else if (portOnly != null) NatFiltering.ADDRESS_DEPENDENT
                else NatFiltering.ADDRESS_PORT_DEPENDENT
            }
            progress("正在检测端口映射")
            val alternateIp = InetSocketAddress(other.address, primary.port)
            val second = query(alternateIp, alternateIp, 0)
            val mapping = when {
                second == null -> NatMapping.UNKNOWN
                second.mapped == first.mapped -> NatMapping.INDEPENDENT
                else -> {
                    val third = query(other, other, 0)
                    when {
                        third == null -> NatMapping.UNKNOWN
                        third.mapped == second.mapped -> NatMapping.ADDRESS_DEPENDENT
                        else -> NatMapping.ADDRESS_PORT_DEPENDENT
                    }
                }
            }
            return NatTestResult(classify(mapping, filtering),
                "带“疑似”的类型建议复测。", endpoint, server, mapping, filtering)
    }
}
