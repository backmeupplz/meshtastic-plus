package com.backmeupplz.meshtasticplus

import android.os.Handler
import android.os.Looper

const val DEMO = "demo" // [Mesh.current] while the simulated node is in use

/**
 * A pretend node for trying the app without a radio (and for app store reviewers). It speaks the same FromRadio/ToRadio
 * protocol as a real node, so the rest of the app runs unchanged: config download, rooms, nodes, messages with
 * delivery checks and replies, settings that "restart" the node. Its names and messages are in the phone's language, like
 * the iPhone app's, so the store screenshots match it.
 */
class DemoLink : Link {
    private class Peer(val num: Long, val long: String, val short: String, val hops: Int, val snr: Float,
                       val battery: Int, val volts: Float, var favorite: Boolean, val heardAgoMin: Int)

    private val main = Handler(Looper.getMainLooper())
    private val me = 0x0de30001L
    private var myLong = string(R.string.demo_node)
    private var myShort = "DEMO"
    private val peers = listOf(
        Peer(0x6a3f21c0, string(R.string.demo_maya).fitBytes(39), "MAYA", 0, 9.5f, 78, 3.92f, true, 2),
        Peer(0x2b7e9d14, string(R.string.demo_ridge).fitBytes(39), "RDGE", 1, -4.25f, 101, 4.18f, false, 5),
        Peer(0x91c4e5a2, "Leo", "LEO", 2, -11f, 34, 3.61f, false, 25),
        Peer(0x5d02f7b8, string(R.string.demo_base).fitBytes(39), "BASE", 1, 2.5f, 92, 4.05f, false, 180),
    )
    private val sections = mutableMapOf( // Config oneof field -> section bytes, like a node's saved config
        1 to Pb().uint(7, 10800).build(), // device: announce every 3h
        2 to Pb().uint(1, 900).uint(2, 1).uint(5, 120).uint(13, 1).build(), // position
        3 to ByteArray(0), // power
        4 to ByteArray(0), // network
        5 to Pb().uint(1, 600).build(), // display
        6 to Pb().uint(1, 1).uint(7, 1).uint(8, 3).uint(9, 1).uint(13, 1).build(), // LoRa: US, Long Fast, 3 hops
        7 to Pb().uint(1, 1).build(), // Bluetooth on, PIN on screen
    )
    private val channels = mutableMapOf(
        0 to (Pb().bytes(2, byteArrayOf(1)).build() to 1), // Public
        1 to (Pb().bytes(2, ByteArray(32) { (it * 37 + 11).toByte() }).str(3, string(R.string.demo_room).replace(" ", "").fitBytes(11)).build() to 2),
        // Base Camp's phone has signal and relays SMS and calls into this room (see [Relay])
        2 to (Pb().bytes(2, ByteArray(32) { (it * 53 + 29).toByte() }).str(3, string(R.string.demo_sms_room).replace(" ", "").fitBytes(11)).build() to 2),
    )
    private var inTransaction = false
    private var reply = 0
    private val replies = listOf(
        R.string.demo_reply_1, R.string.demo_reply_2, R.string.demo_reply_3, R.string.demo_reply_4,
        R.string.demo_reply_5, R.string.demo_reply_6,
    ).map { string(it) }

    override fun send(toRadio: ByteArray) {
        val tr = Msg(toRadio)
        if (tr.has(3)) return sync(tr.long(3)) // want_config_id
        val p = tr.msg(1) ?: return
        val d = p.msg(4) ?: return
        when (d.long(1)) {
            1L -> text(p, d.str(2))
            6L -> admin(Msg(d.bytes(2) ?: ByteArray(0)))
        }
    }

    override fun close() = main.removeCallbacksAndMessages(null)

    private fun emit(fromRadio: Pb) = Mesh.fromRadio(this, fromRadio.build())

    private fun user(num: Long, long: String, short: String) =
        Pb().str(1, "!%08x".format(num)).str(2, long).str(3, short).uint(5, 69) // HELTEC_MESH_NODE_T114

    private fun metrics(battery: Int, volts: Float) = Pb().uint(1, battery.toLong()).fixed32(2, volts.toRawBits().toLong())

    /** What a node sends when an app connects: who it is, its config, rooms, nodes, then queued messages. */
    private fun sync(id: Long) {
        val now = System.currentTimeMillis() / 1000
        emit(Pb().msg(3, Pb().uint(1, me).str(13, "heltec-mesh-node-t114")))
        emit(Pb().msg(4, Pb().uint(1, me).msg(2, user(me, myLong, myShort)).fixed32(5, now).msg(6, metrics(64, 3.81f))))
        peers.forEach { n ->
            emit(Pb().msg(4, Pb().uint(1, n.num).msg(2, user(n.num, n.long, n.short)).fixed32(4, n.snr.toRawBits().toLong())
                .fixed32(5, now - n.heardAgoMin * 60).msg(6, metrics(n.battery, n.volts)).uint(9, n.hops.toLong())
                .uint(10, if (n.favorite) 1 else 0)))
        }
        emit(Pb().msg(13, Pb().str(1, "2.7.26.54e0d8d").uint(5, 1).uint(9, 69))) // metadata: firmware, Bluetooth, T114
        sections.forEach { (section, bytes) -> emit(Pb().msg(5, Pb().bytes(section, bytes))) }
        channels.forEach { (index, ch) -> emit(Pb().msg(10, Pb().uint(1, index.toLong()).bytes(2, ch.first).uint(3, ch.second.toLong()))) }
        emit(Pb().uint(7, id)) // config_complete_id
        // A little history so the screens aren't empty, oldest first per conversation (stable ids: the app skips ones it
        // already has).
        packet(peers[1], BROADCAST, 0, string(R.string.demo_history_1), 0x0de30101, 50)
        packet(peers[2], BROADCAST, 0, string(R.string.demo_history_2), 0x0de30102, 20)
        packet(peers[0], BROADCAST, 1, string(R.string.demo_history_3), 0x0de30103, 15)
        mine(BROADCAST, 1, string(R.string.demo_history_6), 0x0de30106, 14)
        packet(peers[3], BROADCAST, 1, string(R.string.demo_history_4), 0x0de30104, 12)
        packet(peers[0], me, 0, string(R.string.demo_history_5), 0x0de30105, 8)
        // The SMS room: texts and a missed call from outside numbers, answered through the relay (wire texts as [Relay] posts them)
        val base = peers[3]
        packet(base, BROADCAST, 2, "Relay on: SMS and calls to this phone will show up here. To text someone, send $SMS_COMMAND +15551234567 your message", 0x0de30110, 45)
        packet(base, BROADCAST, 2, "SMS from +14155550142: ${string(R.string.demo_sms_1)}", 0x0de30111, 40)
        packet(peers[0], BROADCAST, 2, "$SMS_COMMAND +14155550142 ${string(R.string.demo_sms_2)}", 0x0de30112, 37)
        packet(base, BROADCAST, 2, "SMS to +14155550142 sent (asked by ${peers[0].long})", 0x0de30113, 37)
        packet(base, BROADCAST, 2, "Missed call from +14155550177", 0x0de30114, 26)
        packet(base, BROADCAST, 2, "SMS from +14155550177: ${string(R.string.demo_sms_3)}", 0x0de30115, 25)
        mine(BROADCAST, 2, "$SMS_COMMAND +14155550177 ${string(R.string.demo_sms_4)}", 0x0de30116, 22)
        packet(base, BROADCAST, 2, "SMS to +14155550177 sent (asked by $myLong)", 0x0de30117, 22)
    }

    private fun packet(from: Peer, to: Long, channel: Int, text: String, id: Long, agoMin: Int) {
        val rx = System.currentTimeMillis() / 1000 - agoMin * 60
        emit(Pb().msg(2, Pb().fixed32(1, from.num).fixed32(2, to).uint(3, channel.toLong())
            .msg(4, Pb().uint(1, 1).str(2, text)).fixed32(6, id).fixed32(7, rx)
            .fixed32(8, from.snr.toRawBits().toLong()).uint(9, 3).uint(15, (3 + from.hops).toLong())))
    }

    /** A message this phone sent earlier, relayed by the mesh. The app keeps its own sent messages, so only once. */
    private fun mine(to: Long, channel: Int, text: String, id: Long, agoMin: Int) {
        if (Mesh.has(me, id)) return
        val rx = System.currentTimeMillis() / 1000 - agoMin * 60
        emit(Pb().msg(2, Pb().fixed32(1, me).fixed32(2, to).uint(3, channel.toLong())
            .msg(4, Pb().uint(1, 1).str(2, text)).fixed32(6, id).fixed32(7, rx)))
        emit(Pb().msg(2, Pb().fixed32(1, peers[1].num).fixed32(2, me) // someone rebroadcast it
            .msg(4, Pb().uint(1, 5).bytes(2, ByteArray(0)).fixed32(6, id))))
    }

    /** The phone sent a message: confirm delivery (or a relay for rooms), then someone answers. */
    private fun text(p: Msg, text: String) {
        val to = p.long(2)
        val channel = p.long(3).toInt()
        val dm = peers.firstOrNull { it.num == to }
        val ack = Pb().msg(2, Pb().fixed32(1, dm?.num ?: peers[1].num).fixed32(2, me)
            .msg(4, Pb().uint(1, 5).bytes(2, ByteArray(0)).fixed32(6, p.long(6)))) // routing ack, no error
        main.postDelayed({ emit(ack) }, 1200)
        val line = relayLine(text)
        if (line is RelayLine.Info) return // this phone turned its relay on or off: nothing to answer
        val sms = line as? RelayLine.Sms
        if (dm == null && channel == 2 && sms != null && !sms.incoming) { // a text for the SMS room: Base Camp's phone sends it
            main.postDelayed({
                packet(peers[3], BROADCAST, 2, "SMS to ${dialable(sms.number)} sent (asked by $myLong)", System.nanoTime() and 0x7FFFFFFF, 0)
            }, 3500)
            return
        }
        val responder = dm ?: if (channel == 1) peers[if (reply % 2 == 0) 0 else 3] else peers[reply % peers.size]
        val answer = replies[reply++ % replies.size]
        if (text.isNotBlank()) main.postDelayed({
            packet(responder, if (dm != null) me else BROADCAST, if (dm != null) 0 else channel, answer,
                System.nanoTime() and 0x7FFFFFFF, 0)
        }, 3500)
    }

    /** Settings changes land like on a real node: saved, then a short restart and a fresh config download. */
    private fun admin(a: Msg) {
        a.msg(32)?.let { myLong = it.str(2); myShort = it.str(3); restart() } // set_owner
        a.msg(34)?.let { c -> // set_config: merge like protobuf does (later fields win)
            c.fields.keys.forEach { s -> sections[s] = (sections[s] ?: ByteArray(0)) + (c.bytes(s) ?: ByteArray(0)) }
            if (!inTransaction) restart()
        }
        a.msg(33)?.let { ch -> // set_channel
            val index = ch.long(1).toInt()
            if (ch.long(3) == 0L) channels.remove(index) else channels[index] = (ch.bytes(2) ?: ByteArray(0)) to ch.long(3).toInt()
        }
        if (a.has(39) || a.has(40)) peers.firstOrNull { it.num == a.long(if (a.has(39)) 39 else 40) }?.favorite = a.has(39)
        if (a.has(64)) inTransaction = true
        if (a.has(65)) { inTransaction = false; restart() }
        if (a.has(94) || a.has(99) || a.has(100)) restart() // resets: the demo just restarts, nothing to wipe
    }

    private fun restart() = main.postDelayed({ Mesh.linkUp(this) }, 2000) // the app asks for the config again
}
