import Foundation

/// A pretend node for trying the app without a radio (and for App Review). It speaks the same FromRadio/ToRadio
/// protocol as a real node, so the rest of the app runs unchanged: config download, rooms, nodes, messages with
/// delivery checks and replies, settings that "restart" the node. Same content as the Android app's Demo.kt, in the
/// phone's language so the store screenshots match it.
final class DemoLink: RadioLink {
    private struct Peer {
        let num: Int64, long: String, short: String, hops: Int, snr: Float, battery: Int, volts: Float
        var favorite: Bool
        let heardAgoMin: Int
    }

    private let me: Int64 = 0x0de3_0001
    private var myLong = String(localized: "Demo Node"), myShort = "DEMO"
    private var peers = [
        Peer(num: 0x6a3f_21c0, long: String(localized: "Maya's Handheld").fitBytes(39), short: "MAYA", hops: 0, snr: 9.5, battery: 78, volts: 3.92, favorite: true, heardAgoMin: 2),
        Peer(num: 0x2b7e_9d14, long: String(localized: "Ridge Repeater").fitBytes(39), short: "RDGE", hops: 1, snr: -4.25, battery: 101, volts: 4.18, favorite: false, heardAgoMin: 5),
        Peer(num: 0x91c4_e5a2, long: "Leo", short: "LEO", hops: 2, snr: -11, battery: 34, volts: 3.61, favorite: false, heardAgoMin: 25),
        Peer(num: 0x5d02_f7b8, long: String(localized: "Base Camp").fitBytes(39), short: "BASE", hops: 1, snr: 2.5, battery: 92, volts: 4.05, favorite: false, heardAgoMin: 180),
    ]
    private var sections: [Int: [UInt8]] = [ // Config oneof field -> section bytes, like a node's saved config
        1: Pb().uint(7, 10800).build, // device: announce every 3h
        2: Pb().uint(1, 900).uint(2, 1).uint(5, 120).uint(13, 1).build, // position
        3: [], // power
        4: [], // network
        5: Pb().uint(1, 600).build, // display
        6: Pb().uint(1, 1).uint(7, 1).uint(8, 3).uint(9, 1).uint(13, 1).build, // LoRa: US, Long Fast, 3 hops
        7: Pb().uint(1, 1).build, // Bluetooth on, PIN on screen
    ]
    private var channels: [Int: (settings: [UInt8], role: Int)] = [
        0: (Pb().bytes(2, [1]).build, 1), // Public
        1: (Pb().bytes(2, (0..<32).map { UInt8(truncatingIfNeeded: $0 * 37 + 11) }).str(3, String(localized: "Hiking").replacingOccurrences(of: " ", with: "").fitBytes(11)).build, 2),
    ]
    private var inTransaction = false
    private var reply = 0
    private let replies = [
        String(localized: "Got it 👍"), String(localized: "Sounds good!"), String(localized: "Copy that, see you there."),
        String(localized: "Signal's weak up here but I read you."), String(localized: "On my way."), String(localized: "Nice, the mesh works!"),
    ]
    private var closed = false

    func send(_ toRadio: [UInt8]) {
        guard let tr = Msg(toRadio) else { return }
        if tr.has(3) { return sync(tr.long(3)) } // want_config_id
        guard let p = tr.msg(1), let d = p.msg(4) else { return }
        switch d.long(1) {
        case 1: text(p, d.str(2))
        case 6: if let a = Msg(d.bytes(2) ?? []) { admin(a) }
        default: break
        }
    }

    func close() { closed = true }

    private func later(_ seconds: Double, _ f: @escaping () -> Void) {
        DispatchQueue.main.asyncAfter(deadline: .now() + seconds) { [weak self] in if self?.closed == false { f() } }
    }

    private func emit(_ fromRadio: Pb) {
        let bytes = fromRadio.build
        DispatchQueue.main.async { [weak self] in
            guard let self, !self.closed else { return }
            Mesh.shared.fromRadio(self, bytes)
        }
    }

    private func user(_ num: Int64, _ long: String, _ short: String) -> Pb {
        Pb().str(1, hexId(num)).str(2, long).str(3, short).uint(5, 69) // HELTEC_MESH_NODE_T114
    }

    private func metrics(_ battery: Int, _ volts: Float) -> Pb { Pb().uint(1, Int64(battery)).fixed32(2, Int64(volts.bitPattern)) }

    /// What a node sends when an app connects: who it is, its config, rooms, nodes, then queued messages.
    private func sync(_ id: Int64) {
        let now = Int64(Date().timeIntervalSince1970)
        emit(Pb().msg(3, Pb().uint(1, me).str(13, "heltec-mesh-node-t114")))
        emit(Pb().msg(4, Pb().uint(1, me).msg(2, user(me, myLong, myShort)).fixed32(5, now).msg(6, metrics(64, 3.81))))
        for n in peers {
            emit(Pb().msg(4, Pb().uint(1, n.num).msg(2, user(n.num, n.long, n.short)).fixed32(4, Int64(n.snr.bitPattern))
                .fixed32(5, now - Int64(n.heardAgoMin * 60)).msg(6, metrics(n.battery, n.volts)).uint(9, Int64(n.hops))
                .uint(10, n.favorite ? 1 : 0)))
        }
        emit(Pb().msg(13, Pb().str(1, "2.7.26.54e0d8d").uint(5, 1).uint(9, 69))) // metadata: firmware, Bluetooth, T114
        for (section, bytes) in sections.sorted(by: { $0.key < $1.key }) { emit(Pb().msg(5, Pb().bytes(section, bytes))) }
        for (index, ch) in channels.sorted(by: { $0.key < $1.key }) {
            emit(Pb().msg(10, Pb().uint(1, Int64(index)).bytes(2, ch.settings).uint(3, Int64(ch.role))))
        }
        emit(Pb().uint(7, id)) // config_complete_id
        // A little history so the screens aren't empty (stable ids: the app skips ones it already has).
        packet(peers[1], BROADCAST, 0, String(localized: "Repeater on the ridge is back up after the storm."), 0x0de3_0101, 50)
        packet(peers[2], BROADCAST, 0, String(localized: "Anyone heading to the trailhead tomorrow?"), 0x0de3_0102, 20)
        packet(peers[0], BROADCAST, 1, String(localized: "Made it to the second lake. Zero cell signal here, the mesh still works 🙂"), 0x0de3_0103, 15)
        packet(peers[3], BROADCAST, 1, String(localized: "Copy. Dinner at 7 at camp."), 0x0de3_0104, 12)
        packet(peers[0], me, 0, String(localized: "Are you bringing the stove?"), 0x0de3_0105, 8)
    }

    private func packet(_ from: Peer, _ to: Int64, _ channel: Int, _ text: String, _ id: Int64, _ agoMin: Int) {
        let rx = Int64(Date().timeIntervalSince1970) - Int64(agoMin * 60)
        emit(Pb().msg(2, Pb().fixed32(1, from.num).fixed32(2, to).uint(3, Int64(channel))
            .msg(4, Pb().uint(1, 1).str(2, text)).fixed32(6, id).fixed32(7, rx)
            .fixed32(8, Int64(from.snr.bitPattern)).uint(9, 3).uint(15, Int64(3 + from.hops))))
    }

    /// The phone sent a message: confirm delivery (or a relay for rooms), then someone answers.
    private func text(_ p: Msg, _ text: String) {
        let to = p.long(2), channel = Int(p.long(3))
        let dm = peers.first { $0.num == to }
        let ack = Pb().msg(2, Pb().fixed32(1, dm?.num ?? peers[1].num).fixed32(2, me)
            .msg(4, Pb().uint(1, 5).bytes(2, []).fixed32(6, p.long(6)))) // routing ack, no error
        later(1.2) { [self] in emit(ack) }
        let responder = dm ?? (channel == 1 ? peers[reply % 2 == 0 ? 0 : 3] : peers[reply % peers.count])
        let answer = replies[reply % replies.count]
        reply += 1
        if text.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty { return }
        later(3.5) { [self] in
            packet(responder, dm != nil ? me : BROADCAST, dm != nil ? 0 : channel, answer, Int64.random(in: 1...0x7FFF_FFFF), 0)
        }
    }

    /// Settings changes land like on a real node: saved, then a short restart and a fresh config download.
    private func admin(_ a: Msg) {
        if let o = a.msg(32) { myLong = o.str(2); myShort = o.str(3); restart() } // set_owner
        if let c = a.msg(34) { // set_config: merge like protobuf does (later fields win)
            for s in c.fields.keys { sections[s] = (sections[s] ?? []) + (c.bytes(s) ?? []) }
            if !inTransaction { restart() }
        }
        if let ch = a.msg(33) { // set_channel
            let index = Int(ch.long(1))
            channels[index] = ch.long(3) == 0 ? nil : (ch.bytes(2) ?? [], Int(ch.long(3)))
        }
        if a.has(39) || a.has(40), let k = peers.firstIndex(where: { $0.num == a.long(a.has(39) ? 39 : 40) }) {
            peers[k].favorite = a.has(39)
        }
        if a.has(64) { inTransaction = true }
        if a.has(65) { inTransaction = false; restart() }
        if a.has(94) || a.has(99) || a.has(100) { restart() } // resets: the demo just restarts, nothing to wipe
    }

    private func restart() { later(2) { [self] in Mesh.shared.linkUp(self) } } // the app asks for the config again
}
