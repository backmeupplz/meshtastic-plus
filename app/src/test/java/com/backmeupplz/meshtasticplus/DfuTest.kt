package com.backmeupplz.meshtasticplus

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Expected bytes come from adafruit-nrfutil's own util.py/crc16.py, so a framing mistake can't reach a real node. */
class DfuTest {
    private fun hex(b: ByteArray) = b.joinToString("") { "%02x".format(it) }

    @Test fun crc() = assertEquals(0x29B1, Dfu.crc16("123456789".toByteArray()))

    @Test fun startPacket() = assertEquals(
        "c0d14e01e003000000040000000000000000000000c8350b00a36bc0",
        hex(Dfu.packet(1, Dfu.int32(3) + Dfu.int32(4) + Dfu.int32(0) + Dfu.int32(0) + Dfu.int32(734664))),
    )

    @Test fun slipEscaping() = assertEquals(
        "c0da9e008804000000dbdcdbdd007fff906cc0",
        hex(Dfu.packet(2, Dfu.int32(4) + byteArrayOf(0xC0.toByte(), 0xDB.toByte(), 0, 0x7F, 0xFF.toByte()))),
    )

    @Test fun versions() {
        assertTrue(isNewer("2.7.26.54e0d8d", "2.6.11.60ec05e"))
        assertTrue(isNewer("2.10.0.aaaaaaa", "2.9.9.bbbbbbb"))
        assertFalse(isNewer("2.7.26.54e0d8d", "2.7.26.54e0d8d"))
        assertFalse(isNewer("2.7.26.54e0d8d", "2.8.0.1234567")) // alpha installed: no downgrade offer
    }
}
