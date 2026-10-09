package com.scr01.mod

import kotlinx.coroutines.*
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import org.junit.Assert.*
import org.junit.Test

class NatCancellationTest {
    @Test fun silentUdpServerCanBeCancelledWithoutWaitingForAllRetries() = runBlocking {
        DatagramSocket(0, InetAddress.getByName("127.0.0.1")).use { server ->
            server.soTimeout = 2000
            val test = launch(Dispatchers.Default) { NatDiscovery.test("127.0.0.1:${server.localPort}") {} }
            withContext(Dispatchers.IO) { server.receive(DatagramPacket(ByteArray(2048), 2048)) }
            withTimeout(2000) { test.cancelAndJoin() }
            assertTrue(test.isCancelled)
        }
    }
}
