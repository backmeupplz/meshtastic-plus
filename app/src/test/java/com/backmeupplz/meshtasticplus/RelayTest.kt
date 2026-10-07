package com.backmeupplz.meshtasticplus

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RelayTest {
    @Test fun shortTextIsOneMessage() = assertEquals(listOf("SMS from +1: hi"), splitForMesh("SMS from +1: hi"))

    @Test fun longTextSplitsIntoNumberedParts() {
        val text = "SMS from +15551234567: " + "привет мир ".repeat(40) // multi-byte, ~860 bytes
        val parts = splitForMesh(text)
        assertTrue(parts.size > 1)
        parts.forEachIndexed { i, p ->
            assertTrue(p.startsWith("(${i + 1}/${parts.size}) "))
            assertTrue(p.toByteArray().size <= 200)
        }
        assertEquals(text, parts.joinToString("") { it.substringAfter(") ") })
    }

    @Test fun relayLinesParse() {
        assertEquals(RelayLine.Sms("+17813300607", "Hi: there", incoming = true), relayLine("SMS from +17813300607: Hi: there"))
        assertEquals(RelayLine.Sms("+15551234567", "on my way", incoming = false), relayLine("/sms +15551234567 on my way"))
        assertEquals(RelayLine.Status("+15551234567", sent = true), relayLine("SMS to +15551234567 sent (asked by Bob)"))
        assertEquals(RelayLine.Call("13082101775", "Missed call"), relayLine("Missed call from 13082101775"))
        assertEquals(null, relayLine("just chatting"))
    }

    @Test fun splitPartsJoinBack() {
        val text = "SMS from +15551234567: " + "привет мир ".repeat(40)
        val parts = splitForMesh(text).mapIndexed { i, p -> Message(i.toLong(), "relay", p, i.toLong(), mine = false, from = 7) }
        val joined = joinParts(parts + Message(99, "bob", "hi", 99, mine = false, from = 8))
        assertEquals(listOf(text, "hi"), joined.map { it.text })
    }
}
