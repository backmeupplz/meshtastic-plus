import AVFoundation
import CoreImage.CIFilterBuiltins
import StoreKit
import SwiftUI

func nodeColor(_ num: Int64) -> Color { Color(hue: Double(UInt32(truncatingIfNeeded: num) % 360) / 360, saturation: 0.71, brightness: 0.74) }

func shortTime(_ ms: Double) -> String {
    let d = Date(timeIntervalSince1970: ms / 1000)
    return Calendar.current.isDateInToday(d) ? d.formatted(date: .omitted, time: .shortened) : d.formatted(date: .numeric, time: .omitted)
}

func heard(_ n: Node, _ now: Double) -> String {
    if n.lastHeard == 0 { return String(localized: "Never heard") }
    if now - n.lastHeard < 60_000 { return String(localized: "Heard just now") }
    let f = RelativeDateTimeFormatter()
    f.unitsStyle = .full
    let ago = f.localizedString(for: Date(timeIntervalSince1970: n.lastHeard / 1000), relativeTo: Date(timeIntervalSince1970: now / 1000))
    return String(localized: "Heard \(ago)")
}

func hops(_ n: Node) -> String? { n.hops.map { $0 == 0 ? String(localized: "Direct") : String(localized: "\($0) hops away") } }

let supportEmail = "support@borodutch.com"

func mailURL(subject: String, body: String) -> URL {
    var allowed = CharacterSet.alphanumerics
    allowed.insert(charactersIn: "-._~ ")
    func enc(_ s: String) -> String { s.addingPercentEncoding(withAllowedCharacters: allowed)!.replacingOccurrences(of: " ", with: "%20") }
    return URL(string: "mailto:\(supportEmail)?subject=\(enc(subject))&body=\(enc(body))")!
}

struct StatusTitle: View {
    let title: String, subtitle: String, ok: Bool
    var body: some View {
        VStack(spacing: 1) {
            Text(title).font(.headline).lineLimit(1)
            HStack(spacing: 6) {
                Circle().fill(ok ? green : amber).frame(width: 8, height: 8)
                Text(subtitle).font(.caption).foregroundStyle(.secondary).lineLimit(1)
            }
        }
    }
}

struct StatusPill: View {
    let online: Bool
    var body: some View {
        let color = online ? green : Color.secondary
        HStack(spacing: 6) {
            Circle().fill(color).frame(width: 8, height: 8)
            Text(online ? String(localized: "Online") : String(localized: "Offline")).font(.caption.weight(.medium)).foregroundStyle(color)
        }
        .padding(.horizontal, 10).padding(.vertical, 4)
        .background(color.opacity(0.14), in: Capsule())
    }
}

// MARK: - Home: chats + nodes

enum HomeSheet: Identifiable {
    case settings, devices, newRoom, profile(Int64), invite(Int)
    var id: String {
        switch self { case .settings: "settings"; case .devices: "devices"; case .newRoom: "room"; case .profile(let n): "p\(n)"; case .invite(let i): "i\(i)" }
    }
}

/// Everything Home shows, kept above the compact/regular layout switch: folding or unfolding an iPhone Duo changes
/// the width size class while the app runs, and the layout that comes in must pick up the same open conversation,
/// sheet, prompt, drafts and keyboard.
@Observable final class HomeModel {
    var tab = 0
    // Portrait regular widths (Duo's inner display) would otherwise hide the list behind a button.
    var columns = NavigationSplitViewVisibility.all
    var path: [String] = [] // open conversations, newest last; the wide layout shows the last one next to the list
    var sheet: HomeSheet?
    var drafts: [String: String] = [:] // unsent text by conversation
    var focused: String? // conversation whose composer has the keyboard
    @ObservationIgnored var composer: UUID? // the composer that came on screen or took the keyboard last
    var leaving: Int? // room waiting for "Leave"
    var reporting: Message?
    var noMail = false

    /// Opens a conversation over the current one, or goes back to it if it's already open further down: the wide
    /// layout shows only the last one, so a second copy would pile up unseen.
    func open(_ convo: String) {
        if let i = path.firstIndex(of: convo) { path.removeSubrange((i + 1)...) } else { path.append(convo) }
    }

    /// Closes a conversation (and anything opened from it), as its back button would.
    func close(_ convo: String) { if let i = path.firstIndex(of: convo) { path.removeSubrange(i...) } }
}

struct HomeView: View {
    @State private var model = HomeModel()
    @Environment(\.horizontalSizeClass) private var width
    @Environment(\.requestReview) private var requestReview

    private var unread: Int {
        let mesh = Mesh.shared
        return Set(mesh.messages.filter { !$0.mine }.map(\.convo)).map(mesh.unread).reduce(0, +)
    }

    var body: some View {
        let mesh = Mesh.shared
        Group {
            if width == .regular { split } else { stack }
        }
        .environment(model)
        .modifier(ConversationPrompts(model: model))
        .sheet(item: $model.sheet) { s in
            switch s {
            case .settings: SettingsSheet()
            case .devices: NavigationStack { DevicesView() }
            case .newRoom: NewRoomSheet { model.open($0) }
            case .profile(let num): ProfileSheet(num: num) { model.open(convoKey(0, num)) }
            case .invite(let index): if let room = mesh.rooms[index] { InviteSheet(room: room) }
            }
        }
        .onChange(of: model.path) { old, new in if new.isEmpty && !old.isEmpty && RatingPrompt.ask() { requestReview() } }
        .onChange(of: mesh.pendingConvo, initial: true) { _, convo in
            guard let convo else { return }
            model.sheet = nil
            model.leaving = nil
            model.reporting = nil
            model.path = [convo]
            mesh.pendingConvo = nil
        }
        // Set here rather than in the conversation's onAppear/onDisappear: while the layouts swap, the outgoing
        // conversation can disappear after the incoming one appeared.
        .onChange(of: model.path.last, initial: true) { _, convo in
            mesh.openConvo = convo
            if model.focused != convo { model.focused = nil } // left it with the keyboard up: don't bring it back next time
        }
        .onDisappear { mesh.openConvo = nil }
    }

    /// Cover display and every other compact width: tabs, with conversations pushed over them.
    private var stack: some View {
        NavigationStack(path: $model.path) {
            TabView(selection: $model.tab) {
                ChatsTab().tabItem { Label("Chats", systemImage: "bubble.left.and.bubble.right") }.tag(0).badge(unread)
                NodesTab { model.sheet = .profile($0) }.tabItem { Label("Nodes", systemImage: "point.3.connected.trianglepath.dotted") }.tag(1)
            }
            .navigationBarTitleDisplayMode(.inline)
            .navigationDestination(for: String.self) { ConversationView(convo: $0) }
            .toolbar { toolbar }
        }
    }

    /// Inner display and other regular widths: chats or nodes on the side, the conversation next to them.
    private var split: some View {
        let selection = Binding<String?>(get: { model.path.last }, set: { model.path = $0.map { [$0] } ?? [] })
        return NavigationSplitView(columnVisibility: $model.columns) {
            VStack(spacing: 0) {
                Picker("", selection: $model.tab) {
                    (unread > 0 ? Text("Chats \(unread)") : Text("Chats")).tag(0) // the tab bar's unread badge
                    Text("Nodes").tag(1)
                }
                .pickerStyle(.segmented).labelsHidden().padding(.horizontal).padding(.bottom, 8)
                if model.tab == 0 { ChatsTab(selection: selection) } else { NodesTab { model.sheet = .profile($0) } }
            }
            .navigationBarTitleDisplayMode(.inline)
            .toolbar { toolbar }
            .navigationSplitViewColumnWidth(min: 280, ideal: 340, max: 420)
        } detail: {
            // Nothing opens by itself: a conversation shown here is marked read and stays open through a fold.
            if let convo = model.path.last {
                ConversationView(convo: convo).id(convo)
            } else {
                Image(systemName: "bubble.left.and.bubble.right").font(.system(size: 56)).foregroundStyle(.tertiary)
                    .accessibilityHidden(true)
            }
        }
        .navigationSplitViewStyle(.balanced)
    }

    @ToolbarContentBuilder private var toolbar: some ToolbarContent {
        let mesh = Mesh.shared
        ToolbarItem(placement: .topBarLeading) {
            Button { model.sheet = .settings } label: { Image(systemName: "gearshape") }.accessibilityLabel("Settings")
        }
        ToolbarItem(placement: .principal) {
            Button { model.sheet = .devices } label: { StatusTitle(title: mesh.myLong, subtitle: mesh.status, ok: mesh.connected) }
                .foregroundStyle(.primary).accessibilityHint("Switch device")
        }
        if model.tab == 0 {
            ToolbarItem(placement: .topBarTrailing) {
                Button { model.sheet = .newRoom } label: { Image(systemName: "plus") }.accessibilityLabel("New room")
            }
        }
    }
}

/// Caps scrolling content at a readable width on wide windows; changes nothing on a phone-width one.
extension View {
    func readableWidth(_ max: CGFloat = 640) -> some View { frame(maxWidth: max).frame(maxWidth: .infinity) }
}

struct ConversationRow: View {
    let icon: String, title: String, convo: String
    @Environment(\.dynamicTypeSize) private var type

    var body: some View {
        let mesh = Mesh.shared
        let last = mesh.messages.last { $0.convo == convo }
        let unread = mesh.unread(convo)
        // Accessibility text sizes leave no room for the icon and a trailing column: the name would shrink to a letter.
        let big = type.isAccessibilitySize
        let name = HStack(spacing: 6) {
            Text(title).fontWeight(unread > 0 ? .semibold : .regular).lineLimit(big ? 2 : 1)
            if mesh.isMuted(convo) { Image(systemName: "bell.slash").font(.caption).foregroundStyle(.secondary).accessibilityLabel("Muted") }
        }
        let preview = Text(last.map { ($0.mine ? String(localized: "You") : $0.name) + ": " + $0.text } ?? String(localized: "No messages yet"))
            .font(.subheadline).foregroundStyle(.secondary).lineLimit(big ? 2 : 1)
        let time = last.map { Text(shortTime($0.time)).font(.caption).foregroundStyle(.secondary) }
        let badge = unread > 0 ? Text(verbatim: "\(unread)").font(.caption2.bold()).foregroundStyle(.white)
            .padding(.horizontal, 7).padding(.vertical, 2).background(Color.accentColor, in: Capsule()) : nil
        if big {
            VStack(alignment: .leading, spacing: 2) {
                name
                preview
                HStack(spacing: 8) { time; badge }
            }
        } else {
            HStack(spacing: 14) {
                Image(systemName: icon).foregroundStyle(.secondary).frame(width: 24)
                VStack(alignment: .leading, spacing: 2) {
                    name
                    preview
                }
                Spacer()
                VStack(alignment: .trailing, spacing: 4) {
                    time
                    badge
                }
            }
        }
    }
}

struct ChatsTab: View {
    var selection: Binding<String?>? // the wide layout's open conversation; the compact one pushes instead

    var body: some View {
        let mesh = Mesh.shared
        let rooms = mesh.rooms.values.sorted { $0.index < $1.index }
        // DMs: everyone we've talked to, plus favorites so they're one tap away.
        var peers: [Int64] = []
        for p in mesh.messages.filter({ $0.peer != 0 }).sorted(by: { $0.time > $1.time }).map(\.peer) +
            mesh.nodes.values.filter({ $0.favorite && $0.num != mesh.myNum && !mesh.blocked.contains($0.num) }).map(\.num) where !peers.contains(p) {
            peers.append(p)
        }
        return list {
            Section("Rooms") {
                ForEach(rooms, id: \.index) { r in
                    NavigationLink(value: convoKey(r.index, 0)) {
                        ConversationRow(icon: r.isPublic ? "globe" : "lock", title: r.title, convo: convoKey(r.index, 0))
                    }
                }
            }
            Section("Direct messages") {
                if peers.isEmpty { Text("Open someone in Nodes to message them privately.").foregroundStyle(.secondary) }
                ForEach(peers, id: \.self) { num in
                    let n = mesh.nodes[num]
                    NavigationLink(value: convoKey(0, num)) {
                        ConversationRow(icon: n?.favorite == true ? "star.fill" : "person", title: n?.name ?? hexId(num), convo: convoKey(0, num))
                    }
                }
            }
        }
    }

    @ViewBuilder private func list(@ViewBuilder _ rows: () -> some View) -> some View {
        if let selection { List(selection: selection, content: rows) } else { List(content: rows) }
    }
}

struct NodesTab: View {
    let onProfile: (Int64) -> Void

    var body: some View {
        TimelineView(.periodic(from: .now, by: 30)) { ctx in
            let mesh = Mesh.shared
            let now = ctx.date.timeIntervalSince1970 * 1000
            let nodes = mesh.nodes.values.filter { $0.num != mesh.myNum }
                .sorted { a, b in a.online(now) != b.online(now) ? a.online(now) : a.lastHeard > b.lastHeard }
            if nodes.isEmpty {
                ContentUnavailableView("No nodes yet", systemImage: "point.3.connected.trianglepath.dotted",
                                       description: Text("Nodes show up here as your radio hears them."))
            } else {
                let favorites = nodes.filter(\.favorite), others = nodes.filter { !$0.favorite }
                List {
                    if !favorites.isEmpty {
                        Section("Favorites") { ForEach(favorites, id: \.num) { row($0, now) } }
                    }
                    Section(favorites.isEmpty ? "" : String(localized: "Everyone")) { ForEach(others, id: \.num) { row($0, now) } }
                }
            }
        }
    }

    private func row(_ n: Node, _ now: Double) -> some View {
        Button { onProfile(n.num) } label: {
            HStack {
                VStack(alignment: .leading, spacing: 2) {
                    Text(n.name).lineLimit(1)
                    Text(([heard(n, now), hops(n)].compactMap { $0 } + (Mesh.shared.blocked.contains(n.num) ? [String(localized: "Blocked")] : [])).joined(separator: " · "))
                        .font(.subheadline).foregroundStyle(.secondary)
                }
                Spacer()
                StatusPill(online: n.online(now))
            }
        }
        .foregroundStyle(.primary)
    }
}

// MARK: - Sheets

struct ProfileSheet: View {
    let num: Int64
    let onMessage: () -> Void
    @Environment(\.dismiss) private var dismiss

    var body: some View {
        let mesh = Mesh.shared
        let n = mesh.nodes[num] ?? Node(num: num)
        let blocked = mesh.blocked.contains(num)
        let now = nowMs()
        VStack(alignment: .leading, spacing: 10) {
            HStack {
                VStack(alignment: .leading, spacing: 2) {
                    Text(n.name).font(.title2.weight(.semibold))
                    Text([n.short, hexId(num)].filter { !$0.isEmpty }.joined(separator: " · ")).foregroundStyle(.secondary)
                }
                Spacer()
                StatusPill(online: n.online(now))
            }
            .padding(.bottom, 6)
            Label(heard(n, now), systemImage: "clock")
            if let h = hops(n) { Label(h, systemImage: "point.3.connected.trianglepath.dotted") }
            if let snr = n.snr { Label { Text("Signal \(snr, specifier: "%.1f") dB SNR") } icon: { Image(systemName: "cellularbars") } }
            if let p = n.power { Label(p, systemImage: "battery.75percent") }
            HStack(spacing: 12) {
                Button { dismiss(); onMessage() } label: { Label("Message", systemImage: "bubble.left").frame(maxWidth: .infinity) }
                    .buttonStyle(.borderedProminent).disabled(blocked)
                Button { mesh.setFavorite(num, !n.favorite) } label: {
                    Label(n.favorite ? String(localized: "Favorited") : String(localized: "Favorite"), systemImage: n.favorite ? "star.fill" : "star").frame(maxWidth: .infinity)
                }
                .buttonStyle(.bordered).disabled(!mesh.connected)
            }
            .controlSize(.large).padding(.top, 14)
            Button(role: blocked ? nil : .destructive) { mesh.setBlocked(num, !blocked) } label: {
                Label(blocked ? String(localized: "Unblock") : String(localized: "Block"), systemImage: "hand.raised").frame(maxWidth: .infinity)
            }
            .buttonStyle(.bordered).controlSize(.large)
            Text(blocked ? String(localized: "Their messages and DMs are hidden on this phone.")
                 : String(localized: "Blocking hides their messages and DMs on this phone and stops their notifications."))
                .font(.footnote).foregroundStyle(.secondary)
        }
        .padding(24)
        .presentationDetents([.medium, .large])
    }
}

struct SettingsSheet: View {
    @Environment(\.dismiss) private var dismiss
    @Environment(\.openURL) private var openURL

    var body: some View {
        let mesh = Mesh.shared
        NavigationStack {
            List {
                Section {
                    NavigationLink { NameView(editing: true) } label: {
                        LabeledContent("Name", value: "\(mesh.myLong) (\(mesh.myShort))")
                    }
                    NavigationLink { RegionView(editing: true) } label: { LabeledContent("Region", value: regionName(mesh.region)) }
                    NavigationLink { NodeSettingsView() } label: {
                        VStack(alignment: .leading, spacing: 2) {
                            Text("Node settings")
                            Text([currentBoard()?.name, String(localized: "role, radio, Bluetooth")].compactMap { $0 }.joined(separator: " · "))
                                .font(.subheadline).foregroundStyle(.secondary)
                        }
                    }
                    NavigationLink { DevicesView() } label: {
                        VStack(alignment: .leading, spacing: 2) {
                            Text("Your devices")
                            Text(mesh.demo ? String(localized: "Connected to the demo node · tap to switch") : String(localized: "Connected over Bluetooth · tap to switch")).font(.subheadline).foregroundStyle(.secondary)
                        }
                    }
                }
                Section {
                    NavigationLink { BlockedView() } label: {
                        LabeledContent("Blocked nodes", value: mesh.blocked.isEmpty ? String(localized: "None") : "\(mesh.blocked.count)")
                    }
                    Link("Terms of use", destination: URL(string: "https://meshplus.app/terms.html")!)
                    Link("Privacy policy", destination: URL(string: "https://meshplus.app/privacy.html")!)
                    Button("Send feedback") {
                        let version = Bundle.main.infoDictionary?["CFBundleShortVersionString"] as? String ?? ""
                        openURL(mailURL(subject: "Mesh+ feedback", body: "\n\nMesh+ \(version) on iOS \(UIDevice.current.systemVersion)"))
                    }
                }
                Section {
                    Button("Disconnect", role: .destructive) { dismiss(); mesh.forget() }
                } footer: {
                    Text(verbatim: "Mesh+ \(Bundle.main.infoDictionary?["CFBundleShortVersionString"] as? String ?? "") · \(supportEmail)")
                }
            }
            .navigationTitle("Settings")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar { ToolbarItem(placement: .confirmationAction) { Button("Done") { dismiss() } } }
        }
    }
}

struct BlockedView: View {
    var body: some View {
        let mesh = Mesh.shared
        List {
            if mesh.blocked.isEmpty {
                Text("You haven't blocked anyone. To block someone, press and hold one of their messages, or open them in Nodes.")
                    .foregroundStyle(.secondary)
            }
            ForEach(mesh.blocked.sorted(), id: \.self) { num in
                HStack {
                    VStack(alignment: .leading, spacing: 2) {
                        Text(mesh.nameOf(num))
                        Text(hexId(num)).font(.subheadline).foregroundStyle(.secondary)
                    }
                    Spacer()
                    Button("Unblock") { mesh.setBlocked(num, false) }.buttonStyle(.bordered)
                }
            }
        }
        .navigationTitle("Blocked nodes")
        .navigationBarTitleDisplayMode(.inline)
    }
}

/// Your devices: switch between nodes, forget them, or add one.
struct DevicesView: View {
    @Environment(\.dismiss) private var dismiss
    @State private var adding = false

    var body: some View {
        let mesh = Mesh.shared
        ScrollView {
            VStack(alignment: .leading, spacing: 12) {
                if adding {
                    Text("Turn the node on and keep it close. Devices you already have aren't listed.").foregroundStyle(.secondary)
                    NearbyNodes { f in dismiss(); mesh.connectBle(f.peripheral) }
                    Button("Back to your devices") { adding = false }.padding(.top, 8)
                } else {
                    DeviceRows { key in dismiss(); mesh.connect(key) }
                    Button { adding = true } label: { Label("Add device", systemImage: "plus").frame(maxWidth: .infinity) }
                        .buttonStyle(.bordered).controlSize(.large)
                }
            }
            .padding(24)
        }
        .navigationTitle(adding ? String(localized: "Add a device") : String(localized: "Your devices"))
        .navigationBarTitleDisplayMode(.inline)
    }
}

struct NewRoomSheet: View {
    let onOpen: (String) -> Void
    @Environment(\.dismiss) private var dismiss
    @State private var name = ""
    @State private var error = ""
    @State private var scanning = false

    private func join(_ link: String) {
        let (message, index) = Mesh.shared.join(link)
        if let index { dismiss(); onOpen(convoKey(index, 0)) } else { error = message }
    }

    private func create() {
        if let index = Mesh.shared.createRoom(name.trimmingCharacters(in: .whitespaces)) { dismiss(); onOpen(convoKey(index, 0)) }
        else { error = String(localized: "Your node already has 8 rooms. Leave one first.") }
    }

    var body: some View {
        let mesh = Mesh.shared
        VStack(alignment: .leading, spacing: 8) {
            Text("Create a room").font(.title2.weight(.semibold))
            Text("Private and encrypted. Only people you invite can read it.").foregroundStyle(.secondary)
            HStack {
                TextField("Room name", text: Binding(get: { name }, set: { name = $0.replacingOccurrences(of: " ", with: "").fitBytes(11) }))
                    .textFieldStyle(.roundedBorder).submitLabel(.done).onSubmit { if !name.isEmpty { create() } }
                Button { create() } label: { Image(systemName: "plus") }.buttonStyle(.borderedProminent)
                    .disabled(name.isEmpty || !mesh.connected).accessibilityLabel("Create")
            }
            .padding(.top, 4)
            Text("Join a room").font(.title2.weight(.semibold)).padding(.top, 24)
            Text("Rooms are encrypted, so you can't browse them. Scan someone's invite QR code or paste their link.").foregroundStyle(.secondary)
            HStack(spacing: 12) {
                Button { scanning = true } label: { Label("Scan QR", systemImage: "qrcode.viewfinder").frame(maxWidth: .infinity) }
                    .buttonStyle(.bordered).controlSize(.large)
                PasteButton(payloadType: String.self) { strings in
                    DispatchQueue.main.async { join(strings.first ?? "") }
                }
                .buttonBorderShape(.roundedRectangle).controlSize(.large)
            }
            .padding(.top, 4)
            if !error.isEmpty { Text(error).foregroundStyle(.red).padding(.top, 8) }
        }
        .padding(24)
        .presentationDetents([.medium, .large])
        .sheet(isPresented: $scanning) {
            NavigationStack {
                QRScanner { code in scanning = false; join(code) }
                    .ignoresSafeArea()
                    .navigationTitle("Scan an invite")
                    .navigationBarTitleDisplayMode(.inline)
                    .toolbar { ToolbarItem(placement: .cancellationAction) { Button("Cancel") { scanning = false } } }
            }
        }
    }
}

func qrImage(_ text: String) -> UIImage? {
    let f = CIFilter.qrCodeGenerator()
    f.message = Data(text.utf8)
    guard let out = f.outputImage?.transformed(by: CGAffineTransform(scaleX: 12, y: 12)),
          let cg = CIContext().createCGImage(out, from: out.extent) else { return nil }
    return UIImage(cgImage: cg)
}

struct InviteSheet: View {
    let room: Room

    var body: some View {
        let link = Mesh.shared.inviteLink(room)
        VStack(spacing: 12) {
            Text("Invite to \(room.title)").font(.title2.weight(.semibold))
            Text("Anyone who scans this can read and write in the room.").foregroundStyle(.secondary).multilineTextAlignment(.center)
            if let qr = qrImage(link) {
                Image(uiImage: qr).interpolation(.none).resizable().scaledToFit().frame(width: 240, height: 240)
                    .padding(10).background(.white, in: RoundedRectangle(cornerRadius: 16)).accessibilityLabel("Invite QR code")
            }
            HStack(spacing: 12) {
                ShareLink(item: link) { Label("Share link", systemImage: "square.and.arrow.up").frame(maxWidth: .infinity) }
                    .buttonStyle(.borderedProminent)
                Button { UIPasteboard.general.string = link } label: { Label("Copy", systemImage: "doc.on.doc").frame(maxWidth: .infinity) }
                    .buttonStyle(.bordered)
            }
            .controlSize(.large)
        }
        .padding(24)
        .presentationDetents([.large])
    }
}

/// Camera QR scanner for room invites.
struct QRScanner: UIViewControllerRepresentable {
    let onCode: (String) -> Void
    func makeUIViewController(context: Context) -> ScannerController { ScannerController(onCode: onCode) }
    func updateUIViewController(_ c: ScannerController, context: Context) {}
}

final class ScannerController: UIViewController, AVCaptureMetadataOutputObjectsDelegate {
    private var onCode: ((String) -> Void)?
    private let session = AVCaptureSession()
    private let preview = AVCaptureVideoPreviewLayer()

    init(onCode: @escaping (String) -> Void) {
        self.onCode = onCode
        super.init(nibName: nil, bundle: nil)
    }

    required init?(coder: NSCoder) { nil }

    override func viewDidLoad() {
        super.viewDidLoad()
        view.backgroundColor = .black
        AVCaptureDevice.requestAccess(for: .video) { granted in
            DispatchQueue.main.async { granted ? self.start() : self.say(String(localized: "Mesh+ needs the camera to scan invites. Allow it in Settings → Apps → Mesh+.")) }
        }
    }

    private func start() {
        guard let device = AVCaptureDevice.default(for: .video), let input = try? AVCaptureDeviceInput(device: device),
              session.canAddInput(input) else { return say(String(localized: "This device has no camera. Paste the invite link instead.")) }
        session.addInput(input)
        let output = AVCaptureMetadataOutput()
        guard session.canAddOutput(output) else { return }
        session.addOutput(output)
        output.setMetadataObjectsDelegate(self, queue: .main)
        output.metadataObjectTypes = [.qr]
        preview.session = session
        preview.videoGravity = .resizeAspectFill
        view.layer.addSublayer(preview)
        DispatchQueue.global(qos: .userInitiated).async { self.session.startRunning() }
    }

    private func say(_ text: String) {
        let label = UILabel()
        label.text = text
        label.textColor = .white
        label.numberOfLines = 0
        label.textAlignment = .center
        label.frame = view.bounds.insetBy(dx: 32, dy: 0)
        label.autoresizingMask = [.flexibleWidth, .flexibleHeight]
        view.addSubview(label)
    }

    override func viewDidLayoutSubviews() {
        super.viewDidLayoutSubviews()
        preview.frame = view.bounds
    }

    override func viewWillDisappear(_ animated: Bool) {
        super.viewWillDisappear(animated)
        session.stopRunning()
    }

    func metadataOutput(_ output: AVCaptureMetadataOutput, didOutput objects: [AVMetadataObject], from c: AVCaptureConnection) {
        guard let code = (objects.first as? AVMetadataMachineReadableCodeObject)?.stringValue, let onCode else { return }
        self.onCode = nil
        session.stopRunning()
        onCode(code)
    }
}
