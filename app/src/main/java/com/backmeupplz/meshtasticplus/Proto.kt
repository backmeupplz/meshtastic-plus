package com.backmeupplz.meshtasticplus

import java.io.ByteArrayOutputStream

// Just enough protobuf to speak Meshtastic's mesh.proto (field numbers from meshtastic/protobufs).

class Pb {
    private val out = ByteArrayOutputStream()

    private fun varint(v: Long) {
        var x = v
        while (x and 0x7FL.inv() != 0L) { out.write(((x and 0x7F) or 0x80).toInt()); x = x ushr 7 }
        out.write(x.toInt())
    }

    fun uint(field: Int, v: Long) = apply { if (v != 0L) { varint((field shl 3).toLong()); varint(v) } }
    /** Like [uint] but also writes 0, to override an earlier value. Negative int32 works too (10-byte varint). */
    fun set(field: Int, v: Long) = apply { varint((field shl 3).toLong()); varint(v) }
    fun fixed32(field: Int, v: Long) = apply {
        varint(((field shl 3) or 5).toLong())
        repeat(4) { out.write((v ushr (8 * it)).toInt() and 0xFF) }
    }
    fun bytes(field: Int, v: ByteArray) = apply {
        varint(((field shl 3) or 2).toLong()); varint(v.size.toLong()); out.write(v)
    }
    fun str(field: Int, v: String) = bytes(field, v.toByteArray())
    fun msg(field: Int, m: Pb) = bytes(field, m.build())
    fun build(): ByteArray = out.toByteArray()
}

/** Parsed message: field number -> values (Long for varint/fixed, ByteArray for length-delimited). */
class Msg(b: ByteArray) {
    val fields = HashMap<Int, MutableList<Any>>()

    init {
        var i = 0
        fun varint(): Long {
            var r = 0L; var s = 0
            while (true) {
                val x = b[i++].toInt()
                r = r or ((x and 0x7F).toLong() shl s)
                if (x and 0x80 == 0) return r
                s += 7
            }
        }
        fun fixed(n: Int): Long {
            var r = 0L
            for (k in 0 until n) r = r or ((b[i + k].toLong() and 0xFF) shl (8 * k))
            i += n
            return r
        }
        while (i < b.size) {
            val tag = varint()
            val v: Any = when ((tag and 7).toInt()) {
                0 -> varint()
                1 -> fixed(8)
                2 -> varint().toInt().let { n -> b.copyOfRange(i, i + n).also { i += n } }
                5 -> fixed(4)
                else -> throw IllegalArgumentException("bad wire type")
            }
            fields.getOrPut((tag ushr 3).toInt()) { mutableListOf() }.add(v)
        }
    }

    fun has(f: Int) = f in fields
    fun long(f: Int) = fields[f]?.last() as? Long ?: 0L
    fun bytes(f: Int) = fields[f]?.last() as? ByteArray
    fun str(f: Int) = bytes(f)?.decodeToString() ?: ""
    fun msg(f: Int) = bytes(f)?.let(::Msg)
}

/** Serial stream framing: 0x94 0xC3 len_hi len_lo payload. Anything else on the wire (debug logs) is skipped. */
fun frame(b: ByteArray) = byteArrayOf(0x94.toByte(), 0xC3.toByte(), (b.size shr 8).toByte(), b.size.toByte()) + b

class Deframer(private val onFrame: (ByteArray) -> Unit) {
    private var state = 0
    private var len = 0
    private var buf = ByteArray(0)
    private var n = 0

    fun feed(bytes: ByteArray) {
        for (b in bytes) {
            val x = b.toInt() and 0xFF
            when (state) {
                0 -> if (x == 0x94) state = 1
                1 -> state = if (x == 0xC3) 2 else if (x == 0x94) 1 else 0
                2 -> { len = x shl 8; state = 3 }
                3 -> {
                    len = len or x
                    buf = ByteArray(len); n = 0
                    state = if (len == 0 || len > 512) 0 else 4 // 512 = firmware max; bigger means we lost sync
                }
                4 -> { buf[n++] = b; if (n == len) { state = 0; onFrame(buf) } }
            }
        }
    }
}

/** Channel settings inside a meshtastic.org/e/#... invite link (a base64url-encoded ChannelSet). */
fun inviteRooms(link: String): List<ByteArray> {
    val encoded = link.trim().substringAfter('#', "").replace('+', '-').replace('/', '_')
    if (encoded.isEmpty()) return emptyList()
    return runCatching {
        Msg(java.util.Base64.getUrlDecoder().decode(encoded)).fields[1]?.filterIsInstance<ByteArray>()
    }.getOrNull().orEmpty()
}
