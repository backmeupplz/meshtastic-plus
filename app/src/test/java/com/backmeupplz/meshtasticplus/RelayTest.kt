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
}
