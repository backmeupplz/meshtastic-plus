import SwiftUI

/// One editable field of the node's Config. `section` is the Config oneof (1 device, 2 position, 3 power, 4 network,
/// 5 display, 6 LoRa, 7 Bluetooth), `field` the field inside it (config.proto).
/// Kind: `options` = pick one, `toggle` = switch (`invert` when the field is a "disabled" flag), `pin` = 6 digits, `text` = string.
struct Setting: Identifiable {
    let section: Int, field: Int, title: String, info: String
    var options: [(Int64, String)]?
    var toggle = false, invert = false, pin = false, text = false, secret = false
    var also: (Int, Int64)? // another field of the same section to set along with this one
    var visible: ((Int, Int) -> Int64) -> Bool = { _ in true }
    var id: String { "\(section)/\(field)" }
}

struct SettingGroup: Identifiable {
    let title: String, settings: [Setting]
    var id: String { title }
}

enum SettingValue: Equatable { case int(Int64), text(String) }

/// "3 hours", "1 day": the largest whole unit, in the phone's language.
func duration(_ s: Int64) -> String {
    if s == 0xFFFF_FFFF { return String(localized: "Always") }
    let f = DateComponentsFormatter()
    f.unitsStyle = .full
    f.allowedUnits = s % 86400 == 0 ? .day : s % 3600 == 0 ? .hour : s % 60 == 0 ? .minute : .second
    return f.string(from: TimeInterval(s)) ?? "\(s)"
}

/// Interval choices; 0 means "firmware default" and is labeled with what that default is.
private func every(_ def: Int64, _ secs: Int64...) -> [(Int64, String)] {
    [(0, String(localized: "Default (\(duration(def)))"))] + secs.map { ($0, duration($0)) }
}

private let BT_WARNING = "\n\n" + String(localized: "Changing this can drop the Bluetooth connection while the node restarts.")

/// Meshtastic RegionCode values (config.proto), minus the amateur-radio bands that need a license.
let REGIONS: [(Int, String)] = [
    (1, String(localized: "United States, Canada, Mexico")), (3, String(localized: "Europe & UK 868 MHz")), (2, String(localized: "Europe & UK 433 MHz")),
    (6, String(localized: "Australia / New Zealand")), (11, String(localized: "New Zealand 865 MHz")), (22, String(localized: "Australia / New Zealand 433 MHz")),
    (26, String(localized: "Brazil")), (4, String(localized: "China")), (10, String(localized: "India")), (5, String(localized: "Japan")), (24, String(localized: "Kazakhstan 863 MHz")), (23, String(localized: "Kazakhstan 433 MHz")),
    (7, String(localized: "Korea")), (17, String(localized: "Malaysia 919 MHz")), (16, String(localized: "Malaysia 433 MHz")), (25, String(localized: "Nepal")), (21, String(localized: "Philippines 915 MHz")),
    (20, String(localized: "Philippines 868 MHz")), (19, String(localized: "Philippines 433 MHz")), (9, String(localized: "Russia")), (18, String(localized: "Singapore")), (8, String(localized: "Taiwan")),
    (12, String(localized: "Thailand")), (15, String(localized: "Ukraine 868 MHz")), (14, String(localized: "Ukraine 433 MHz")), (13, String(localized: "2.4 GHz (worldwide)")),
]

private let EU: Set<String> = [
    "AT", "BE", "BG", "HR", "CY", "CZ", "DK", "EE", "FI", "FR", "DE", "GR", "HU", "IE", "IT", "LV", "LT", "LU",
    "MT", "NL", "PL", "PT", "RO", "SK", "SI", "ES", "SE", "GB", "NO", "CH", "IS", "RS", "ME", "MK", "AL", "BA",
    "MD", "GE", "AM", "TR", "LI", "MC", "AD", "SM",
]

/// Best guess from the phone's region; the user confirms it.
func suggestedRegion(_ country: String) -> Int {
    if EU.contains(country) { return 3 }
    let map: [String: Int] = [
        "US": 1, "CA": 1, "MX": 1, "AU": 6, "NZ": 6, "BR": 26, "CN": 4, "IN": 10, "JP": 5, "KZ": 24, "KR": 7, "MY": 17,
        "NP": 25, "PH": 21, "RU": 9, "BY": 9, "SG": 18, "TW": 8, "TH": 12, "UA": 15,
    ]
    return map[country] ?? 0
}

func regionName(_ code: Int) -> String { REGIONS.first { $0.0 == code }?.1 ?? String(localized: "Not set") }

let SETTINGS: [SettingGroup] = [
    SettingGroup(title: String(localized: "Role"), settings: [
        Setting(section: 1, field: 1, title: String(localized: "Role"), info:
            String(localized: "What job this node does on the mesh.\n\n• Client: the normal choice for a node you carry and chat from. It relays other people's messages too.\n• Client (mute): doesn't relay. Use it when another node of yours nearby already does.\n• Client (hidden): stays quiet and only talks when needed, for privacy or battery.\n• Client base: a home node that relays eagerly for your favorites.\n• Router / Router (late): for nodes in high, well-placed spots that exist to extend the mesh. Late waits for others to relay first.\n• Tracker / Sensor: sends its position or sensor readings first.\n• Lost and found: keeps announcing its location so you can find it.\n\nRouter roles may turn on power saving, which switches Bluetooth off most of the time."),
            options: [(0, String(localized: "Client")), (1, String(localized: "Client (mute)")), (8, String(localized: "Client (hidden)")), (12, String(localized: "Client base")), (2, String(localized: "Router")),
                      (11, String(localized: "Router (late)")), (5, String(localized: "Tracker")), (6, String(localized: "Sensor")), (9, String(localized: "Lost and found")), (7, String(localized: "TAK")), (10, String(localized: "TAK tracker"))]),
        Setting(section: 1, field: 6, title: String(localized: "Relaying"), info:
            String(localized: "Which messages this node passes along for others.\n\n• Everything: relays all it hears. Best for the mesh, and the default.\n• Everything, without reading: same, without trying to decrypt. For dedicated repeaters.\n• Local rooms only: ignores traffic from other meshes it can't read.\n• Known nodes only: ignores nodes that aren't in its node list.\n• Standard messages only: skips unusual app traffic.\n• Nothing: never relays. Only allowed for Tracker and Sensor roles."),
            options: [(0, String(localized: "Everything")), (1, String(localized: "Everything, without reading")), (2, String(localized: "Local rooms only")),
                      (3, String(localized: "Known nodes only")), (5, String(localized: "Standard messages only")), (4, String(localized: "Nothing"))]),
        Setting(section: 1, field: 7, title: String(localized: "Announce name every"), info:
            String(localized: "How often the node tells everyone its name and encryption key. Others need this to show your name and to send you direct messages. More often adds radio traffic for everyone; the default suits most people."),
            options: every(10800, 3600, 21600, 43200, 86400)),
        Setting(section: 1, field: 12, title: String(localized: "Heartbeat LED"), info:
            String(localized: "Blinks the node's LED every few seconds to show it's running. Turn off to keep it dark or save a little battery."),
            toggle: true, invert: true),
    ]),
    SettingGroup(title: String(localized: "Radio"), settings: [
        Setting(section: 6, field: 7, title: String(localized: "Region"), info:
            String(localized: "The radio band. It has to match your country's rules, and nodes on different regions can't hear each other."),
            options: REGIONS.map { (Int64($0.0), $0.1) }),
        Setting(section: 6, field: 2, title: String(localized: "Speed vs. range"), info:
            String(localized: "The radio preset: slower presets reach farther, faster ones carry more messages. Every node has to use the same preset to hear each other, so only change this if your local mesh uses a different one. Almost everyone uses Long range, fast."),
            options: [(0, String(localized: "Long range, fast")), (7, String(localized: "Long range, moderate")), (9, String(localized: "Long range, turbo")), (3, String(localized: "Medium range, slow")),
                      (4, String(localized: "Medium range, fast")), (5, String(localized: "Short range, slow")), (6, String(localized: "Short range, fast")), (8, String(localized: "Short range, turbo"))],
            also: (1, 1)), // use_preset
        Setting(section: 6, field: 8, title: String(localized: "Max hops"), info:
            String(localized: "How many times a message may be passed along on its way. 3 is the default and plenty for most meshes; higher values add traffic for everyone."),
            options: [(0, String(localized: "Default (3)"))] + (1...7).map { (Int64($0), "\($0)") }),
        Setting(section: 6, field: 10, title: String(localized: "Transmit power"), info:
            String(localized: "How strongly the node transmits. Maximum uses the strongest power that's legal in your region and that the board supports. Lower saves battery but shortens range."),
            options: [(0, String(localized: "Maximum allowed")), (10, "10 dBm"), (14, "14 dBm"), (17, "17 dBm"), (20, "20 dBm"), (22, "22 dBm"),
                      (27, "27 dBm"), (30, "30 dBm")]),
        Setting(section: 6, field: 9, title: String(localized: "Transmit"), info:
            String(localized: "Lets the radio send. Off makes the node listen only: nothing you send will go out."), toggle: true),
        Setting(section: 6, field: 13, title: String(localized: "Boosted receive"), info:
            String(localized: "Makes the receiver a bit more sensitive on SX126x radios (most modern boards), for slightly more power use."), toggle: true),
        Setting(section: 6, field: 105, title: String(localized: "OK to share via internet"), info:
            String(localized: "Lets gateways that bridge the mesh to the internet (MQTT) forward your messages there. Off asks them not to."), toggle: true),
        Setting(section: 6, field: 104, title: String(localized: "Ignore internet messages"), info:
            String(localized: "Ignores messages that reached the mesh from the internet through MQTT gateways."), toggle: true),
    ]),
    SettingGroup(title: String(localized: "Location"), settings: [
        Setting(section: 2, field: 13, title: String(localized: "GPS"), info:
            String(localized: "Whether the node uses its GPS chip. Off saves a lot of battery. Choose Not present when the board has no GPS."),
            options: [(1, String(localized: "On")), (0, String(localized: "Off")), (2, String(localized: "Not present"))]),
        Setting(section: 2, field: 1, title: String(localized: "Share location every"), info: String(localized: "How often the node broadcasts its position to the mesh."),
            options: every(3600, 300, 900, 1800, 3600, 10800, 21600, 43200)),
        Setting(section: 2, field: 2, title: String(localized: "Smart sharing"), info:
            String(localized: "Also shares your location early after you've moved a good distance, and skips updates while you stay put."), toggle: true),
        Setting(section: 2, field: 5, title: String(localized: "Check GPS every"), info: String(localized: "How often the node wakes the GPS for a fix. Less often saves battery."),
            options: every(120, 30, 120, 300, 900, 3600)),
    ]),
    SettingGroup(title: String(localized: "Power"), settings: [
        Setting(section: 3, field: 1, title: String(localized: "Power saving"), info:
            String(localized: "Sleeps deeply between radio activity, for nodes on small batteries or solar. Saves a lot on ESP32 boards, but turns Bluetooth and the screen off most of the time. nRF52 boards are already efficient.") + BT_WARNING,
            toggle: true),
        Setting(section: 3, field: 2, title: String(localized: "Turn off on battery after"), info:
            String(localized: "Powers the node off this long after it's unplugged from USB or solar. Never keeps it running on battery."),
            options: [(0, String(localized: "Never"))] + ([300, 3600, 21600, 86400] as [Int64]).map { ($0, duration($0)) }),
    ]),
    SettingGroup(title: String(localized: "Screen"), settings: [
        Setting(section: 5, field: 1, title: String(localized: "Screen timeout"), info:
            String(localized: "How long the node's screen stays on after a button press or new message. Only matters for boards with a screen."),
            options: every(600, 15, 30, 60, 300, 600, 3600) + [(0xFFFF_FFFF, String(localized: "Always on"))]),
        Setting(section: 5, field: 5, title: String(localized: "Flip screen"), info: String(localized: "Turns the screen upside down, for cases that mount the board the other way."), toggle: true),
        Setting(section: 5, field: 6, title: String(localized: "Units"), info: String(localized: "Distances and altitudes on the node's screen."), options: [(0, String(localized: "Metric")), (1, String(localized: "Imperial"))]),
        Setting(section: 5, field: 12, title: String(localized: "12-hour clock"), info: String(localized: "Shows the time as 1:00 PM instead of 13:00 on the node's screen."), toggle: true),
    ]),
    SettingGroup(title: String(localized: "Bluetooth"), settings: [
        Setting(section: 7, field: 1, title: String(localized: "Bluetooth"), info:
            String(localized: "Lets phones connect to the node over Bluetooth. If you turn it off, this phone can't reach the node anymore; only a USB cable from a computer can change its settings again."),
            toggle: true),
        Setting(section: 7, field: 2, title: String(localized: "Pairing"), info:
            String(localized: "How a phone proves it's allowed to connect.\n\n• PIN on screen: shows a new random PIN each time (needs a screen).\n• Fixed PIN: always the same PIN, set below. Nodes without a screen use 123456.\n• No PIN: anyone nearby can connect and read your messages.") + BT_WARNING,
            options: [(0, String(localized: "PIN on screen")), (1, String(localized: "Fixed PIN")), (2, String(localized: "No PIN"))]),
        Setting(section: 7, field: 3, title: String(localized: "Fixed PIN"), info: String(localized: "The 6-digit PIN phones type when pairing."), pin: true, visible: { get in get(7, 2) == 1 }),
    ]),
    SettingGroup(title: String(localized: "Wi-Fi"), settings: [
        Setting(section: 4, field: 1, title: String(localized: "Wi-Fi"), info:
            String(localized: "Connects the node to a Wi-Fi network, for internet gateways. On ESP32 boards Wi-Fi turns Bluetooth off."),
            toggle: true, visible: { _ in Mesh.shared.hasWifi }),
        Setting(section: 4, field: 3, title: String(localized: "Network name"), info: String(localized: "The Wi-Fi network (SSID) to join."), text: true, visible: { _ in Mesh.shared.hasWifi }),
        Setting(section: 4, field: 4, title: String(localized: "Password"), info: String(localized: "The Wi-Fi password."), text: true, secret: true, visible: { _ in Mesh.shared.hasWifi }),
    ]),
]

/// A board from Meshtastic's hardware list (hardware.json, from api.meshtastic.org/resource/deviceHardware).
struct Board {
    let hwModel: Int, target: String, arch: String, name: String
    var chip: String { arch.hasPrefix("nrf52") ? "nRF52840" : arch == "portduino" ? "Linux" : arch.uppercased() }
}

// ponytail: bundled snapshot of the hardware list; refresh hardware.json (same file as the Android app's) when Meshtastic adds boards
let boards: [Board] = {
    guard let url = Bundle.main.url(forResource: "hardware", withExtension: "json"), let data = try? Data(contentsOf: url),
          let list = try? JSONSerialization.jsonObject(with: data) as? [[Any]] else { return [] }
    return list.compactMap { b in
        guard b.count == 4, let hw = b[0] as? Int, let t = b[1] as? String, let a = b[2] as? String, let n = b[3] as? String else { return nil }
        return Board(hwModel: hw, target: t, arch: a, name: n)
    }
}()

/// The connected node's board: exact build target when the firmware reports it, else the first board with its model.
func currentBoard() -> Board? {
    let mesh = Mesh.shared
    return boards.first { $0.target == mesh.pioEnv } ?? boards.first { $0.hwModel == mesh.hwModel && mesh.hwModel != 0 }
}

/// True when `latest` is a higher x.y.z than `installed` (the commit-hash suffix is ignored).
func isNewer(_ latest: String, _ installed: String) -> Bool {
    func parts(_ v: String) -> [Int] {
        let p = (v.hasPrefix("v") ? String(v.dropFirst()) : v).split(separator: ".").prefix(3).map { Int($0) ?? 0 }
        return p + Array(repeating: 0, count: 3 - p.count)
    }
    let a = parts(latest), b = parts(installed)
    guard let i = (0..<3).first(where: { a[$0] != b[$0] }) else { return false }
    return a[i] > b[i]
}

/// Firmware before 2.8.1 deadlocks halfway through a reset on boards with a screen (nested spiLock in MessageStore).
func resetFreezes() -> Bool { !Mesh.shared.firmware.isEmpty && isNewer("2.8.1", Mesh.shared.firmware) }

private let RESET_FREEZE_NOTE = "\n\n" + String(localized: "This node's firmware freezes partway through a reset (fixed in 2.8.1). When its screen stops changing, press its reset button once and it finishes. Updating the firmware first avoids this.")

struct Reset: Identifiable {
    let kind: Int, title: String, question: String, summary: String, details: String, confirm: String
    var id: Int { kind }
}

private let RESETS = [
    Reset(kind: 100, title: String(localized: "Clear node list"), question: String(localized: "Clear the node list?"), summary: String(localized: "Forget every node it has heard; favorites stay"), details:
        String(localized: "Your node forgets every other node it has heard, except favorites. They reappear as they're heard again. Handy when the list is full of nodes from far away."),
        confirm: String(localized: "Clear")),
    Reset(kind: 99, title: String(localized: "Reset settings"), question: String(localized: "Reset settings?"), summary: String(localized: "Back to factory settings; keeps its identity and pairing"), details:
        String(localized: "Every setting goes back to factory defaults: role, radio, region, name and private rooms (you'll need new invites). The node keeps its identity and Bluetooth pairing, and messages on this phone stay. It restarts, and you'll pick its region again."),
        confirm: String(localized: "Reset")),
    Reset(kind: 94, title: String(localized: "Factory reset"), question: String(localized: "Factory reset?"), summary: String(localized: "Erase everything, as if it were new"), details:
        String(localized: "Erases everything on the node: settings, rooms, node list, its encryption keys and Bluetooth pairings. Others will see it as a new node. It restarts and leaves Your devices: pair it again like a new node, with the PIN on its screen, then pick its region and name. Messages on this phone stay."),
        confirm: String(localized: "Erase everything")),
]

struct NodeSettingsView: View {
    @Environment(\.dismiss) private var dismiss
    @State private var edits: [String: SettingValue] = [:]
    @State private var info: Setting?
    @State private var editing: Setting?
    @State private var draft = ""
    @State private var resetting: Reset?
    private var mesh: Mesh { .shared }

    private func original(_ s: Setting) -> SettingValue {
        let m = mesh.configs[s.section].flatMap(Msg.init)
        return s.text ? .text(m?.str(s.field) ?? "") : .int(m?.long(s.field) ?? 0)
    }
    private func value(_ s: Setting) -> SettingValue { edits[s.id] ?? original(s) }
    private func get(_ section: Int, _ field: Int) -> Int64 {
        if case .int(let v) = edits["\(section)/\(field)"] { return v }
        return mesh.configs[section].flatMap(Msg.init)?.long(field) ?? 0
    }
    private func set(_ s: Setting, _ v: SettingValue) { edits[s.id] = v == original(s) ? nil : v }

    private func save() {
        var changes: [Int: Pb] = [:]
        for s in SETTINGS.flatMap(\.settings) {
            guard let v = edits[s.id] else { continue }
            var pb = changes[s.section] ?? Pb()
            switch v { case .text(let t): pb = pb.str(s.field, t); case .int(let n): pb = pb.set(s.field, n) }
            if let also = s.also { pb = pb.set(also.0, also.1) }
            changes[s.section] = pb
        }
        mesh.saveSettings(changes)
        dismiss()
    }

    private func display(_ s: Setting, _ v: SettingValue) -> String {
        switch v {
        case .int(let n): return s.options?.first { $0.0 == n }?.1 ?? (s.pin ? (n == 0 ? String(localized: "Not set") : "\(n)") : duration(n))
        case .text(let t): return t.isEmpty ? String(localized: "Not set") : s.secret ? "••••••••" : t
        }
    }

    var body: some View {
        List {
            Section { BoardCard() }
            if mesh.configs.isEmpty { Text("Waiting for the node's settings…").foregroundStyle(.secondary) }
            ForEach(SETTINGS) { group in
                let shown = group.settings.filter { mesh.configs[$0.section] != nil && $0.visible(get) }
                if !shown.isEmpty {
                    Section(group.title) { ForEach(shown) { row($0) } }
                }
            }
            if !mesh.configs.isEmpty {
                Section("Reset") {
                    ForEach(RESETS) { r in
                        Button { resetting = r } label: {
                            VStack(alignment: .leading, spacing: 2) {
                                Text(r.title).foregroundStyle(r.kind == 100 ? Color.primary : .red)
                                Text(r.summary).font(.footnote).foregroundStyle(.secondary)
                            }
                        }
                        .disabled(!mesh.connected)
                    }
                }
            }
        }
        .navigationTitle("Node settings")
        .navigationBarTitleDisplayMode(.inline)
        .safeAreaInset(edge: .bottom) {
            if !edits.isEmpty {
                HStack {
                    Button("Undo") { edits = [:] }
                    Button { save() } label: {
                        Label(String(localized: "Save \(edits.count) changes"), systemImage: "checkmark").frame(maxWidth: .infinity)
                    }
                    .buttonStyle(.borderedProminent).controlSize(.large).disabled(!mesh.connected)
                }
                .padding().background(.bar)
            }
        }
        .alert(info?.title ?? "", isPresented: Binding(get: { info != nil }, set: { if !$0 { info = nil } })) {
            Button("Got it") { info = nil }
        } message: { Text(info?.info ?? "") }
        .alert(editing?.title ?? "", isPresented: Binding(get: { editing != nil }, set: { if !$0 { editing = nil } })) {
            if editing?.secret == true { SecureField(editing?.title ?? "", text: $draft) }
            else { TextField(editing?.title ?? "", text: $draft).keyboardType(editing?.pin == true ? .numberPad : .default) }
            Button("OK") {
                guard let s = editing else { return }
                if s.pin {
                    if draft.count == 6, draft.allSatisfy(\.isNumber), draft.first != "0", let n = Int64(draft) { set(s, .int(n)) }
                } else if draft.utf8.count <= 64 { set(s, .text(draft)) }
                editing = nil
            }
            Button("Cancel", role: .cancel) { editing = nil }
        } message: {
            Text(editing?.pin == true ? String(localized: "6 digits, not starting with 0.") : editing?.info ?? "")
        }
        .alert(resetting?.question ?? "", isPresented: Binding(get: { resetting != nil }, set: { if !$0 { resetting = nil } })) {
            Button(resetting?.confirm ?? "", role: .destructive) {
                if let r = resetting { mesh.reset(r.kind); dismiss() }
                resetting = nil
            }
            Button("Cancel", role: .cancel) { resetting = nil }
        } message: {
            Text((resetting?.details ?? "") + (resetting.map { $0.kind != 100 && resetFreezes() } == true ? RESET_FREEZE_NOTE : ""))
        }
    }

    @ViewBuilder private func row(_ s: Setting) -> some View {
        let v = value(s)
        let changed = edits[s.id] != nil
        let title = HStack(spacing: 4) {
            Text(s.title).fontWeight(changed ? .semibold : .regular)
            Button { info = s } label: { Image(systemName: "info.circle").foregroundStyle(.secondary) }
                .buttonStyle(.borderless).accessibilityLabel("About \(s.title)")
        }
        if s.toggle {
            Toggle(isOn: Binding(get: { (v == .int(1)) != s.invert }, set: { on in set(s, .int(on != s.invert ? 1 : 0)) })) { title }
        } else if let options = s.options {
            NavigationLink {
                OptionsView(title: s.title, options: options, current: v) { set(s, .int($0)) }
            } label: {
                LabeledContent { Text(display(s, v)).foregroundStyle(changed ? Color.accentColor : .secondary) } label: { title }
            }
        } else {
            Button {
                draft = { if case .text(let t) = v { return t }; if case .int(let n) = v, n != 0 { return "\(n)" }; return "" }()
                editing = s
            } label: {
                LabeledContent { Text(display(s, v)).foregroundStyle(changed ? Color.accentColor : .secondary) } label: { title }
            }
            .foregroundStyle(.primary)
        }
    }
}

struct OptionsView: View {
    let title: String, options: [(Int64, String)], current: SettingValue, onPick: (Int64) -> Void
    @Environment(\.dismiss) private var dismiss

    var body: some View {
        List(options.indices, id: \.self) { i in
            let option = options[i]
            Button { onPick(option.0); dismiss() } label: {
                HStack {
                    Text(option.1).foregroundStyle(.primary)
                    Spacer()
                    if current == .int(option.0) { Image(systemName: "checkmark").foregroundStyle(Color.accentColor) }
                }
            }
        }
        .navigationTitle(title)
        .navigationBarTitleDisplayMode(.inline)
    }
}

/// Which board this is, its chip and firmware, so you can tell your nodes apart.
struct BoardCard: View {
    var body: some View {
        let mesh = Mesh.shared
        let board = currentBoard()
        let chip = [board?.chip, mesh.firmware.isEmpty ? nil : String(localized: "Firmware \(mesh.firmware)")].compactMap { $0 }.joined(separator: " · ")
        let details = [chip, String(localized: "Node ID \(hexId(mesh.myNum))"), mesh.nodes[mesh.myNum]?.power].compactMap { $0 }.joined(separator: "\n")
        HStack(alignment: .top, spacing: 14) {
            Image(systemName: "cpu").font(.title2).foregroundStyle(.secondary)
            VStack(alignment: .leading, spacing: 4) {
                Text(board?.name ?? String(localized: "Unknown board")).font(.headline)
                Text(details).font(.subheadline).foregroundStyle(.secondary)
                Text("To update its firmware, use the web flasher at flasher.meshtastic.org on a computer.")
                    .font(.footnote).foregroundStyle(.secondary).padding(.top, 4)
            }
        }
        .padding(.vertical, 4)
    }
}
