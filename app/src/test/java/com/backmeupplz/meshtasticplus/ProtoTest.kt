package com.backmeupplz.meshtasticplus

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

class ProtoTest {
    @Test fun roundTrip() {
        val data = Pb().uint(1, 1).str(2, "привет")
        val pkt = Msg(Pb().fixed32(1, 0xDEADBEEF).fixed32(2, 0xFFFFFFFF).msg(4, data).uint(9, 300).build())
        assertEquals(0xDEADBEEF, pkt.long(1))
        assertEquals(0xFFFFFFFF, pkt.long(2))
        assertEquals(300L, pkt.long(9))
        assertEquals(1L, pkt.msg(4)!!.long(1))
        assertEquals("привет", pkt.msg(4)!!.str(2))
        // Known bytes: ToRadio { want_config_id = 150 } and an empty Heartbeat.
        assertArrayEquals(byteArrayOf(0x18, 0x96.toByte(), 0x01), Pb().uint(3, 150).build())
        assertArrayEquals(byteArrayOf(0x3A, 0x00), Pb().msg(7, Pb()).build())
        // saveRegion relies on an appended field overriding the original (protobuf last-one-wins).
        val lora = Pb().uint(1, 1).uint(7, 0).uint(8, 3).build()
        val patched = Msg(lora + Pb().uint(7, 3).build())
        assertEquals(3L, patched.long(7))
        assertEquals(3L, patched.long(8))
    }

    @Test fun parsesOfficialInvite() {
        // Default LongFast invite as shared by the official apps: one channel with the well-known key 0x01.
        val rooms = inviteRooms("https://meshtastic.org/e/#CgMSAQESBggBQANIAQ")
        assertEquals(1, rooms.size)
        assertArrayEquals(byteArrayOf(1), Msg(rooms[0]).bytes(2))
        assertEquals(0, inviteRooms("https://example.com/no-fragment").size)
        assertEquals(0, inviteRooms("https://meshtastic.org/e/#!!!").size)
    }

    @Test fun deframeSkipsLogNoise() {
        val got = mutableListOf<ByteArray>()
        val d = Deframer { got += it }
        val a = byteArrayOf(1, 2, 3)
        val stream = "INFO | boot\r\n".toByteArray() + frame(a) + byteArrayOf(0x94.toByte()) + frame(byteArrayOf(9))
        d.feed(stream.copyOfRange(0, 15)) // split mid-frame
        d.feed(stream.copyOfRange(15, stream.size))
        assertEquals(2, got.size)
        assertArrayEquals(a, got[0])
        assertArrayEquals(byteArrayOf(9), got[1])
    }
}
