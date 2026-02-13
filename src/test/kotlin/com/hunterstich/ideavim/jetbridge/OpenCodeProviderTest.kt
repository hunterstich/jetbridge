package com.hunterstich.ideavim.jetbridge

import org.junit.Test
import kotlin.test.assertEquals
import kotlin.text.contains

class OpenCodeProviderTest {
    @Test
    fun regexLsofPortAndIp() {
        val output =  listOf(
            "[COMMAND    PID   USER   FD   TYPE             DEVICE SIZE/OFF NODE NAME, ",
            ".opencode 8620 laptop   20u  IPv4 0xf341b5c7aeeffce5      0t0  TCP 127.0.0.1:3000 (LISTEN)]",
        )
        val result = output
            .filter { it.contains("TCP") }
            .map { line ->
                """TCP (\d+\.\d+\.\d+\.\d+):(\d+)""".toRegex().find(line)?.groupValues
            }
            .firstOrNull()

        val ip = result?.getOrNull(1)
        val port = result?.getOrNull(2)

        assertEquals("127.0.0.1", ip)
        assertEquals("3000", port)
    }
}