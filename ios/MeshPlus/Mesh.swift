import CoreBluetooth
import Foundation
import Observation
import Security
import UserNotifications

struct Message: Codable, Equatable {
    var id: Int64
    var name: String
    var text: String
    var time: Double // ms since 1970, like the Android app
    var mine: Bool
    var status = ""
    var from: Int64 = 0
    var channel = 0
    var peer: Int64 = 0 // != 0: direct message with that node
    var me: Int64 = 0 // the node this message belongs to, so each node keeps its own history
    var convo: String { convoKey(channel, peer) }
}

func convoKey(_ channel: Int, _ peer: Int64) -> String { peer != 0 ? "dm:\(peer)" : "ch:\(channel)" }
func hexId(_ num: Int64) -> String { String(format: "!%08x", UInt32(truncatingIfNeeded: num)) }
func nowMs() -> Double { Date().timeIntervalSince1970 * 1000 }

struct Node: Equatable {
    var num: Int64
    var long = "", short = ""
    var lastHeard: Double = 0
    var snr: Float?
    var hops: Int?
    var favorite = false
    var battery: Int?
    var voltage: Float?

    var name: String { long.isEmpty ? hexId(num) : long }
    func online(_ now: Double) -> Bool { now - lastHeard < 2 * 3600_000 } // same 2h rule as the official apps
    /// "Battery 62% · 3.71 V" / "Plugged in · 4.18 V": the voltage shows whether a battery is there and charging.
    var power: String? {
        let parts = [battery.map { $0 > 100 ? "Plugged in" : "Battery \($0)%" }, voltage.flatMap { $0 > 0 ? String(format: "%.2f V", $0) : nil }]
            .compactMap { $0 }
        return parts.isEmpty ? nil : parts.joined(separator: " · ")
    }
}

/// A Meshtastic channel ("room"): anyone with its name and key can read and write it.
struct Room: Equatable {
    let index: Int, name: String, psk: [UInt8], primary: Bool, settings: [UInt8]
    var title: String { name.isEmpty ? (psk == [1] ? "Public" : "Primary") : name }
    var isPublic: Bool { psk.count <= 1 } // no key, or one of the well-known default keys
}

/// A device used before: its Bluetooth identifier and the node's name.
struct Saved: Equatable {
    let key: String
    var name: String
}

protocol RadioLink: AnyObject {
    func send(_ toRadio: [UInt8])
    func close()
}

let BROADCAST: Int64 = 0xFFFF_FFFF
private let TEXT_APP: Int64 = 1, NODEINFO_APP: Int64 = 4, ROUTING_APP: Int64 = 5, ADMIN_APP: Int64 = 6, TELEMETRY_APP: Int64 = 67
private let INVITE = "https://meshtastic.org/e/#"
let DEMO = "demo" // `Mesh.current` while the simulated node is in use

func randomId() -> Int64 { Int64.random(in: 1...0xFFFF_FFFF) }

/// The whole app state, like the Android app's Mesh object.
@Observable final class Mesh {
    static let shared = Mesh()

    private var all: [Message] = []
    var nodes: [Int64: Node] = [:]
    var rooms: [Int: Room] = [:] // by channel index, 0 = primary
    var lastRead: [String: Double] = [:] // "node/conversation" -> time it was last open
    private var muted: Set<String> = [] // "node/conversation" that don't notify
    var blocked: Set<Int64> = [] // nodes whose messages are hidden on this phone
    var saved: [Saved] = [] // devices used before
    var current: String? // key of the device we're on, as in `saved`
    var status = ""
    var transport = "" // "Bluetooth" / "Demo"
    var active = false // a link exists: pairing, connecting or connected
    var ready = false // synced with the node at least once on this link
    var connected = false // link is up right now
    var askName = false // node still has its factory name: offer to pick one
    var region = -1 // LoRa region code, 0 = unset (radio won't transmit), -1 = not known yet
    var myNum: Int64 = 0
    var myLong = ""
    var myShort = ""
    var configs: [Int: [UInt8]] = [:] // the node's Config sections as received, by oneof field (6 = LoRa)
    var firmware = "" // DeviceMetadata.firmware_version
    var hwModel = 0 // HardwareModel enum
    var pioEnv = "" // exact firmware build target, e.g. heltec-mesh-node-t114 (firmware 2.6+)
    var hasWifi = false
    var agreedTerms = false // the community rules were accepted on this phone
    var pendingConvo: String? // a notification was tapped
    @ObservationIgnored var visible = false // the app is on screen
    @ObservationIgnored var openConvo: String? // conversation on screen, so its messages don't notify

    @ObservationIgnored private let defaults = UserDefaults.standard
    @ObservationIgnored private var link: RadioLink?
    @ObservationIgnored private var heartbeat: Timer?
    @ObservationIgnored private let file = FileManager.default.urls(for: .applicationSupportDirectory, in: .userDomainMask)[0]
        .appendingPathComponent("messages.json")
    private var lora: [UInt8]? { configs[6] }

    /// History of the connected node only, without blocked nodes.
    var messages: [Message] {
        all.filter { $0.me == myNum && ($0.mine || !blocked.contains($0.from)) && !blocked.contains($0.peer) }
    }

    private init() {
        if CommandLine.arguments.contains("-uitest") { // UI tests start from a fresh install
            UserDefaults.standard.removePersistentDomain(forName: Bundle.main.bundleIdentifier!)
            try? FileManager.default.removeItem(at: file)
        }
        agreedTerms = defaults.bool(forKey: "terms")
        if let data = try? Data(contentsOf: file), let list = try? JSONDecoder().decode([Message].self, from: data) { all = list }
        lastRead = defaults.dictionary(forKey: "read") as? [String: Double] ?? [:]
        muted = Set(defaults.stringArray(forKey: "muted") ?? [])
        blocked = Set((defaults.array(forKey: "blocked") as? [Int64]) ?? [])
        saved = (defaults.array(forKey: "saved") as? [[String]] ?? []).compactMap { $0.count == 2 ? Saved(key: $0[0], name: $0[1]) : nil }
        // Reconnect on launch to the last node.
        if let last = defaults.string(forKey: "ble") { connect(last) }
    }

    // MARK: Connections

    /// Connects to a device from `saved`.
    func connect(_ key: String) {
        guard let id = UUID(uuidString: key) else { return }
        disconnect()
        active = true
        transport = "Bluetooth"
        current = key
        status = "Connecting to \(savedName(key) ?? "your node")…"
        Bluetooth.shared.whenOn { [self] in
            guard current == key, link == nil else { return }
            if let p = Bluetooth.shared.peripheral(id) { connectBle(p) }
            else { forgetSaved(key); disconnect(); status = "That node isn't known to this phone anymore. Find it below to pair it again." }
        }
    }

    func connectBle(_ p: CBPeripheral) {
        disconnect()
        active = true
        transport = "Bluetooth"
        current = p.identifier.uuidString
        defaults.set(current, forKey: "ble")
        status = "Connecting to \(savedName(p.identifier.uuidString) ?? p.name ?? "your node")…"
        link = BleLink(p)
    }

    /// The simulated node: try the app without a radio (also what App Review uses).
    func connectDemo() {
        disconnect()
        active = true
        transport = "Demo"
        current = DEMO
        status = "Connecting to the demo node…"
        let demo = DemoLink()
        link = demo
        linkUp(demo)
    }

    var demo: Bool { current == DEMO }

    func agree() {
        agreedTerms = true
        defaults.set(true, forKey: "terms")
    }

    private func closeLink() {
        heartbeat?.invalidate()
        link?.close()
        link = nil
    }

    func disconnect() {
        closeLink()
        current = nil
        active = false
        ready = false
        connected = false
        status = ""
        rooms = [:]
        configs = [:]
        firmware = ""; hwModel = 0; pioEnv = ""; hasWifi = false
        nodes = [:] // each node has its own node list and favorites
        myNum = 0
    }

    /// User pressed Disconnect: also forget the node so we don't auto-reconnect next launch.
    func forget() {
        defaults.removeObject(forKey: "ble")
        disconnect()
    }

    private func savedName(_ key: String) -> String? { saved.first { $0.key == key }.flatMap { $0.name.isEmpty ? nil : $0.name } }

    func forgetSaved(_ key: String) {
        saved.removeAll { $0.key == key }
        saveSaved()
    }

    private func saveSaved() { defaults.set(saved.map { [$0.key, $0.name] }, forKey: "saved") }

    func linkUp(_ from: RadioLink) {
        guard from === link else { return }
        status = status.hasPrefix("Connecting to") ? status.replacingOccurrences(of: "Connecting to", with: "Syncing with") : "Syncing…"
        from.send(Pb().uint(3, randomId()).build) // want_config_id: node replays its state + queued messages
        heartbeat?.invalidate()
        heartbeat = Timer.scheduledTimer(withTimeInterval: 60, repeats: true) { [weak self] _ in
            self?.link?.send(Pb().msg(7, Pb()).build) // keeps the API session alive
        }
    }

    func linkDown(_ from: RadioLink, _ reason: String) {
        guard from === link else { return }
        connected = false
        status = reason
    }

    /// The device isn't speaking Meshtastic, or pairing failed: stop retrying, show why.
    func failed(_ from: RadioLink, _ reason: String) {
        guard from === link else { return }
        closeLink()
        active = false
        connected = false
        status = reason
    }

    func fromRadio(_ from: RadioLink, _ bytes: [UInt8]) {
        guard from === link, let fr = Msg(bytes) else { return }
        handle(fr)
    }

    private func handle(_ fr: Msg) {
        if let m = fr.msg(3) { myNum = m.long(1); pioEnv = m.str(13) }
        if let ni = fr.msg(4) {
            let num = ni.long(1)
            var n = nodes[num] ?? Node(num: num)
            n.lastHeard = max(n.lastHeard, Double(ni.long(5)) * 1000)
            if let snr = ni.float(4) { n.snr = snr }
            if ni.has(9) { n.hops = Int(ni.long(9)) }
            n.favorite = ni.long(10) == 1
            if let dm = ni.msg(6) { metrics(&n, dm) }
            nodes[num] = n
            if let u = ni.msg(2) { user(num, u) }
        }
        if let c = fr.msg(5) { // Config: one section per frame
            for k in c.fields.keys { configs[k] = c.bytes(k) ?? [] }
            if let lora = c.bytes(6) { region = Int(Msg(lora)?.long(7) ?? 0) }
        }
        if let md = fr.msg(13) { firmware = md.str(1); hwModel = Int(md.long(9)); hasWifi = md.long(4) == 1 } // DeviceMetadata
        if let c = fr.msg(10) {
            let index = Int(c.long(1)), role = c.long(3)
            rooms[index] = role == 0 ? nil : room(index, c.bytes(2) ?? [], role == 1)
        }
        if fr.has(7) {
            connected = true
            status = "Connected"
            // Nodes without GPS have no clock; without this their timestamps (last heard, message times) are garbage.
            admin(Pb().fixed32(43, Int64(Date().timeIntervalSince1970))) // set_time_only
            if !ready { askName = myLong.range(of: "^Meshtastic [0-9a-f]{4}$", options: .regularExpression) != nil && !defaults.bool(forKey: "named") }
            ready = true
            if let key = current, key != DEMO {
                saved.removeAll { $0.key == key }
                saved.insert(Saved(key: key, name: myLong), at: 0)
                saveSaved()
            }
            Notify.ask()
        }
        if let p = fr.msg(2) { packet(p) }
    }

    /// DeviceMetrics: battery level and voltage.
    private func metrics(_ n: inout Node, _ m: Msg) {
        if m.has(1) { n.battery = Int(m.long(1)) }
        if let v = m.float(2) { n.voltage = v }
    }

    private func room(_ index: Int, _ settings: [UInt8], _ primary: Bool) -> Room {
        let m = Msg(settings)
        return Room(index: index, name: m?.str(3) ?? "", psk: m?.bytes(2) ?? [], primary: primary, settings: settings)
    }

    private func user(_ num: Int64, _ u: Msg) {
        var n = nodes[num] ?? Node(num: num)
        n.long = u.str(2); n.short = u.str(3)
        nodes[num] = n
        if num == myNum { myLong = u.str(2); myShort = u.str(3); if hwModel == 0 { hwModel = Int(u.long(5)) } }
    }

    private func packet(_ p: Msg) {
        let from = p.long(1)
        if from != myNum && from != 0 {
            var n = nodes[from] ?? Node(num: from)
            n.lastHeard = nowMs()
            if let snr = p.float(8) { n.snr = snr }
            let hopStart = p.long(15)
            if hopStart > 0 { n.hops = Int(hopStart - p.long(9)) }
            nodes[from] = n
        }
        guard let d = p.msg(4) else { return } // still encrypted = not on one of our channels
        let payload = d.bytes(2) ?? []
        switch d.long(1) {
        case TEXT_APP:
            let id = p.long(6)
            if id != 0 && all.contains(where: { $0.me == myNum && $0.id == id && !$0.mine }) { return } // node replays its queue on reconnect
            let rx = Double(p.long(7)) * 1000
            let dm = p.long(2) == myNum && from != myNum
            let m = Message(id: id, name: nodes[from]?.name ?? hexId(from), text: String(decoding: payload, as: UTF8.self),
                            time: rx > 1_600_000_000_000 ? rx : nowMs(), mine: from == myNum, from: from,
                            channel: dm ? 0 : Int(p.long(3)), peer: dm ? from : 0, me: myNum)
            add(m)
            if !m.mine { Notify.message(m) }
        case NODEINFO_APP:
            if let u = Msg(payload) { user(from, u) }
        case TELEMETRY_APP:
            if let dm = Msg(payload)?.msg(2), var n = nodes[from] { metrics(&n, dm); nodes[from] = n }
        case ROUTING_APP:
            if let i = all.firstIndex(where: { $0.mine && $0.me == myNum && $0.id == d.long(6) }) {
                // Broadcast: implicit ack = someone rebroadcast it. DM: real ack from the recipient. error_reason != 0 = gave up.
                all[i].status = (Msg(payload)?.long(3) ?? 0) == 0 ? "✓" : "✗"
                save()
            }
        default: break
        }
    }

    // MARK: Messages

    /// Channel message when `peer` is 0, else a DM (the firmware picks the channel and end-to-end encrypts it).
    func send(_ text: String, channel: Int, peer: Int64) {
        let id = randomId()
        let data = Pb().uint(1, TEXT_APP).str(2, text)
        let packet = Pb().fixed32(2, peer != 0 ? peer : BROADCAST).uint(3, Int64(channel)).msg(4, data).fixed32(6, id).uint(10, 1) // want_ack
        link?.send(Pb().msg(1, packet).build)
        add(Message(id: id, name: myLong, text: text, time: nowMs(), mine: true, status: "…", from: myNum, channel: channel, peer: peer, me: myNum))
        markRead(convoKey(channel, peer))
    }

    func retry(_ m: Message) {
        all.removeAll { $0 == m }
        send(m.text, channel: m.channel, peer: m.peer)
    }

    func unread(_ convo: String) -> Int {
        let since = lastRead["\(myNum)/\(convo)"] ?? 0
        return messages.filter { !$0.mine && $0.convo == convo && $0.time > since }.count
    }

    /// Muted conversations still collect messages and unread counts, they just don't notify. Per node, like rooms.
    func isMuted(_ convo: String, me: Int64? = nil) -> Bool { muted.contains("\(me ?? myNum)/\(convo)") }

    func toggleMute(_ convo: String) {
        let key = "\(myNum)/\(convo)"
        if muted.remove(key) == nil { muted.insert(key) }
        defaults.set(Array(muted), forKey: "muted")
    }

    func markRead(_ convo: String) {
        lastRead["\(myNum)/\(convo)"] = nowMs()
        defaults.set(lastRead, forKey: "read")
    }

    /// Hides everything from `num` on this phone: its messages, its DMs and their notifications.
    func setBlocked(_ num: Int64, _ on: Bool) {
        if on { blocked.insert(num) } else { blocked.remove(num) }
        defaults.set(Array(blocked), forKey: "blocked")
    }

    /// The name a node had when we last saw a message from it (blocked nodes may not be in the current node list).
    func nameOf(_ num: Int64) -> String {
        nodes[num]?.name ?? all.last { $0.from == num && !$0.mine }?.name ?? hexId(num)
    }

    // MARK: Node admin

    /// Sends an AdminMessage to our own node.
    private func admin(_ m: Pb) {
        let packet = Pb().fixed32(2, myNum).msg(4, Pb().uint(1, ADMIN_APP).msg(2, m)).fixed32(6, randomId())
        link?.send(Pb().msg(1, packet).build)
    }

    func setOwner(_ long: String, _ short: String) {
        admin(Pb().msg(32, Pb().str(2, long).str(3, short))) // set_owner
        myLong = long; myShort = short
        status = "Restarting to apply changes…"
        var n = nodes[myNum] ?? Node(num: myNum)
        n.long = long; n.short = short
        nodes[myNum] = n
        nameAsked()
    }

    func saveRegion(_ code: Int) {
        guard let config = lora else { return }
        // Appending a field overrides the earlier value (protobuf last-one-wins), keeping the rest of the LoRa config.
        admin(Pb().msg(34, Pb().bytes(6, config + Pb().uint(7, Int64(code)).build))) // set_config
        region = code
        status = "Restarting to apply changes…"
    }

    /// Changes several config fields at once: `changes` maps Config section (1 = device … 7 = bluetooth) to the fields
    /// to override. Wrapped in an edit transaction so the node saves everything and restarts once.
    func saveSettings(_ changes: [Int: Pb]) {
        admin(Pb().uint(64, 1)) // begin_edit_settings
        for (section, fields) in changes { admin(Pb().msg(34, Pb().bytes(section, (configs[section] ?? []) + fields.build))) }
        admin(Pb().uint(65, 1)) // commit_edit_settings: save + restart
        for (section, fields) in changes { configs[section] = (configs[section] ?? []) + fields.build }
        if let l = configs[6] { region = Int(Msg(l)?.long(7) ?? 0) }
        status = "Restarting to apply changes…"
    }

    /// 100 = clear node list (favorites stay), 99 = settings back to defaults, 94 = everything incl. keys and Bluetooth pairings.
    func reset(_ kind: Int) {
        admin(Pb().uint(kind, 1)) // nodedb_reset / factory_reset_config / factory_reset_device
        status = kind == 100 ? "Clearing the node list…" : "Resetting the node…"
        if kind == 100 { for n in nodes.values where !n.favorite && n.num != myNum { nodes[n.num] = nil } }
        else { defaults.removeObject(forKey: "named") } // its name is back to the default: offer to pick one again
        guard kind == 94, let key = current, key != DEMO else { return }
        let freezes = resetFreezes() // before disconnect() forgets the firmware version
        // The node erased its half of the pairing, so this device entry is dead. iOS has no API to drop our half.
        DispatchQueue.main.asyncAfter(deadline: .now() + 1.5) { [self] in // let the request reach the node first
            defaults.removeObject(forKey: "ble")
            forgetSaved(key)
            disconnect()
            status = "Factory reset sent. In iOS Settings → Bluetooth, tap ⓘ next to the node and choose Forget This Device." +
                (freezes ? " When its screen stops changing, press its reset button once (a firmware bug fixed in 2.8.1)." : "") +
                " Then find it below and pair it with the new PIN."
        }
    }

    func setFavorite(_ num: Int64, _ favorite: Bool) {
        admin(Pb().uint(favorite ? 39 : 40, num)) // set_favorite_node / remove_favorite_node, stored on the node
        nodes[num]?.favorite = favorite
    }

    private func setRoom(_ index: Int, _ settings: [UInt8], _ role: Int) {
        admin(Pb().msg(33, Pb().uint(1, Int64(index)).bytes(2, settings).uint(3, Int64(role)))) // set_channel
        rooms[index] = role == 0 ? nil : room(index, settings, role == 1)
    }

    private func freeSlot() -> Int? { (1...7).first { rooms[$0] == nil } }

    /// New private room with a random 256-bit key. Returns its index, or nil when all 8 slots are used.
    func createRoom(_ name: String) -> Int? {
        guard let index = freeSlot() else { return nil }
        var psk = [UInt8](repeating: 0, count: 32)
        _ = SecRandomCopyBytes(kSecRandomDefault, psk.count, &psk)
        setRoom(index, Pb().bytes(2, psk).str(3, name).build, 2)
        return index
    }

    func leaveRoom(_ index: Int) { if index != 0 { setRoom(index, [], 0) } }

    /// Invite link in the format every Meshtastic app understands (meshtastic.org/e/#ChannelSet).
    func inviteLink(_ room: Room) -> String {
        var set = Pb().bytes(1, room.settings)
        if let lora { set = set.bytes(2, lora) }
        return INVITE + base64url(set.build)
    }

    /// Adds the rooms from an invite link. Returns a message for the user and the first joined room, if any.
    func join(_ link: String) -> (String, Int?) {
        let settings = inviteRooms(link)
        if settings.isEmpty { return ("That doesn't look like a Meshtastic invite", nil) }
        var first: Int?
        for s in settings {
            let r = room(0, s, false)
            if let existing = rooms.values.first(where: { $0.name == r.name && $0.psk == r.psk }) { first = first ?? existing.index; continue }
            guard let index = freeSlot() else { return ("Your node already has 8 rooms. Leave one first.", first) }
            setRoom(index, s, 2)
            first = first ?? index
        }
        return ("Joined", first)
    }

    func nameAsked() {
        askName = false
        defaults.set(true, forKey: "named")
    }

    private func add(_ m: Message) {
        all.append(m)
        save()
    }

    // ponytail: rewrites the whole file per message; switch to append-only if history gets huge
    private func save() {
        try? FileManager.default.createDirectory(at: file.deletingLastPathComponent(), withIntermediateDirectories: true)
        try? JSONEncoder().encode(all).write(to: file, options: .atomic)
    }
}

/// Local notifications for new messages.
enum Notify {
    static func ask() {
        if CommandLine.arguments.contains("-uitest") { return } // a permission alert would block the UI tests
        UNUserNotificationCenter.current().requestAuthorization(options: [.alert, .sound, .badge]) { _, _ in }
    }

    /// A message arrived: notify unless its conversation is on screen. One notification per conversation, newest wins.
    static func message(_ m: Message) {
        let mesh = Mesh.shared
        if mesh.visible && mesh.openConvo == m.convo { return }
        if mesh.isMuted(m.convo, me: m.me) || mesh.blocked.contains(m.from) { return }
        let content = UNMutableNotificationContent()
        let dm = m.peer != 0
        content.title = dm ? m.name : mesh.rooms[m.channel]?.title ?? "Room"
        content.body = dm ? m.text : "\(m.name): \(m.text)"
        content.sound = .default
        content.threadIdentifier = m.convo
        content.userInfo = ["convo": m.convo]
        UNUserNotificationCenter.current().add(UNNotificationRequest(identifier: m.convo, content: content, trigger: nil))
    }

    static func clear(_ convo: String) {
        UNUserNotificationCenter.current().removeDeliveredNotifications(withIdentifiers: [convo])
    }
}
