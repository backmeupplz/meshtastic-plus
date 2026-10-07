package com.backmeupplz.meshtasticplus

import android.os.Handler
import android.os.Looper

const val DEMO = "demo" // [Mesh.current] while the simulated node is in use

/**
 * A pretend node for trying the app without a radio (and for app store reviewers). It speaks the same FromRadio/ToRadio
 * protocol as a real node, so the rest of the app runs unchanged: config download, rooms, nodes, messages with
 * delivery checks and replies, settings that "restart" the node.
 */
class DemoLink : Link {
    private class Peer(val num: Long, val long: String, val short: String, val hops: Int, val snr: Float,
                       val battery: Int, val volts: Float, var favorite: Boolean, val heardAgoMin: Int)

    private val main = Handler(Looper.getMainLooper())
    private val me = 0x0de30001L
    private var myLong = "Demo Node"
    private var myShort = "DEMO"
    private val peers = listOf(
        Peer(0x6a3f21c0, "Maya's Handheld", "MAYA", 0, 9.5f, 78, 3.92f, true, 2),
        Peer(0x2b7e9d14, "Ridge Repeater", "RDGE", 1, -4.25f, 101, 4.18f, false, 5),
        Peer(0x91c4e5a2, "Leo", "LEO", 2, -11f, 34, 3.61f, false, 25),
        Peer(0x5d02f7b8, "Base Camp", "BASE", 1, 2.5f, 92, 4.05f, false, 180),
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
        1 to (Pb().bytes(2, ByteArray(32) { (it * 37 + 11).toByte() }).str(3, "Hiking").build() to 2),
    )
    private var inTransaction = false
    private var reply = 0
    private val replies = listOf(
        "Got it 👍", "Sounds good!", "Copy that, see you there.", "Signal's weak up here but I read you.",
        "On my way.", "Nice, the mesh works!",
    )

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
        // A little history so the screens aren't empty (stable ids: the app skips ones it already has).
        packet(peers[1], BROADCAST, 0, "Repeater on the ridge is back up after the storm.", 0x0de30101, 50)
        packet(peers[2], BROADCAST, 0, "Anyone heading to the trailhead tomorrow?", 0x0de30102, 20)
        packet(peers[0], BROADCAST, 1, "Made it to the second lake. Zero cell signal here, the mesh still works 🙂", 0x0de30103, 15)
        packet(peers[3], BROADCAST, 1, "Copy. Dinner at 7 at camp.", 0x0de30104, 12)
        packet(peers[0], me, 0, "Are you bringing the stove?", 0x0de30105, 8)
    }

    private fun packet(from: Peer, to: Long, channel: Int, text: String, id: Long, agoMin: Int) {
        val rx = System.currentTimeMillis() / 1000 - agoMin * 60
        emit(Pb().msg(2, Pb().fixed32(1, from.num).fixed32(2, to).uint(3, channel.toLong())
            .msg(4, Pb().uint(1, 1).str(2, text)).fixed32(6, id).fixed32(7, rx)
            .fixed32(8, from.snr.toRawBits().toLong()).uint(9, 3).uint(15, (3 + from.hops).toLong())))
    }

    /** The phone sent a message: confirm delivery (or a relay for rooms), then someone answers. */
    private fun text(p: Msg, text: String) {
        val to = p.long(2)
        val channel = p.long(3).toInt()
        val dm = peers.firstOrNull { it.num == to }
        val ack = Pb().msg(2, Pb().fixed32(1, dm?.num ?: peers[1].num).fixed32(2, me)
            .msg(4, Pb().uint(1, 5).bytes(2, ByteArray(0)).fixed32(6, p.long(6)))) // routing ack, no error
        main.postDelayed({ emit(ack) }, 1200)
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
