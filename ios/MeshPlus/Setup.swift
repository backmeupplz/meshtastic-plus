import SwiftUI

let green = Color(red: 0.18, green: 0.62, blue: 0.31) // 0xFF2E9E50
let amber = Color(red: 0.91, green: 0.64, blue: 0.0) // 0xFFE8A400

struct Hero: View {
    let icon: String
    var body: some View {
        Image(systemName: icon)
            .font(.system(size: 44, weight: .medium))
            .foregroundStyle(Color.accentColor)
            .frame(width: 104, height: 104)
            .background(Color.accentColor.opacity(0.15), in: Circle())
    }
}

// MARK: - Terms: shown once, before any chatting (demo included)

struct TermsView: View {
    var body: some View {
        ScrollView {
            VStack(spacing: 16) {
                Hero(icon: "hand.raised").padding(.top, 48)
                Text("Be kind on the mesh").font(.title.weight(.semibold))
                Text("Mesh+ lets you chat with people nearby over radio. There's zero tolerance for objectionable content and abusive users: don't post anything hateful, threatening, sexual or harassing.")
                    .multilineTextAlignment(.center).foregroundStyle(.secondary)
                VStack(alignment: .leading, spacing: 14) {
                    Label("Press and hold any message to report it or block its sender. You can also block someone from their profile in Nodes.", systemImage: "exclamationmark.bubble")
                    Label("Blocking hides everything from that person on this phone, including their notifications.", systemImage: "hand.raised")
                    Label("Reports go to the developer, who reviews and acts on them.", systemImage: "envelope")
                }
                .padding(.vertical, 8)
                Link("Read the terms of use", destination: URL(string: "https://meshplus.app/terms.html")!)
                Button { Mesh.shared.agree() } label: { Text("Agree").frame(maxWidth: .infinity) }
                    .buttonStyle(.borderedProminent).controlSize(.large).padding(.top, 8)
                Text("By tapping Agree you accept the terms of use.").font(.footnote).foregroundStyle(.secondary)
            }
            .padding(24)
        }
    }
}

// MARK: - Setup: blocks the app until a node is connected

struct DeviceRow<Trailing: View>: View {
    let icon: String, title: String, subtitle: String
    var highlighted = false
    let onTap: () -> Void
    @ViewBuilder var trailing: Trailing

    var body: some View {
        Button(action: onTap) {
            HStack(spacing: 16) {
                Image(systemName: icon).frame(width: 24)
                VStack(alignment: .leading, spacing: 2) {
                    Text(title).font(.headline).lineLimit(1)
                    Text(subtitle).font(.subheadline).foregroundStyle(.secondary).lineLimit(2)
                }
                Spacer()
                trailing
            }
            .padding(.horizontal, 16).padding(.vertical, 12)
            .frame(maxWidth: .infinity, minHeight: 64)
            .background(highlighted ? Color.accentColor.opacity(0.18) : Color(.secondarySystemBackground), in: RoundedRectangle(cornerRadius: 16))
        }
        .foregroundStyle(.primary)
    }
}

/// Devices used before, with the current one checked. Tapping another one switches to it.
struct DeviceRows: View {
    let onPick: (String) -> Void
    @State private var removing: Saved?

    var body: some View {
        let mesh = Mesh.shared
        VStack(spacing: 8) {
            ForEach(mesh.saved, id: \.key) { d in
                let current = d.key == mesh.current
                DeviceRow(icon: "dot.radiowaves.left.and.right", title: d.name.isEmpty ? String(localized: "Node") : d.name,
                          subtitle: current ? mesh.status : "Bluetooth", highlighted: current, onTap: { if !current { onPick(d.key) } }) {
                    if current { Image(systemName: "checkmark").foregroundStyle(Color.accentColor).accessibilityLabel("Connected") }
                    else {
                        Button { removing = d } label: { Image(systemName: "xmark").foregroundStyle(.secondary) }
                            .buttonStyle(.borderless).accessibilityLabel("Forget this device")
                    }
                }
            }
        }
        .alert(removing.map { $0.name.isEmpty ? String(localized: "Remove this device?") : String(localized: "Remove \($0.name)?") } ?? "",
               isPresented: Binding(get: { removing != nil }, set: { if !$0 { removing = nil } })) {
            Button("Remove", role: .destructive) { if let r = removing { mesh.forgetSaved(r.key) }; removing = nil }
            Button("Cancel", role: .cancel) { removing = nil }
        } message: {
            Text("It disappears from Your devices. The node itself isn't changed, it stays paired with this phone, and your messages stay. To use it again, find it with Add device.")
        }
    }
}

/// Meshtastic nodes advertising nearby, minus the ones already in Your devices.
struct NearbyNodes: View {
    let onPick: (Found) -> Void

    var body: some View {
        let known = Set(Mesh.shared.saved.map(\.key))
        let fresh = Bluetooth.shared.found.values.filter { !known.contains($0.id.uuidString) }.sorted { $0.name < $1.name }
        VStack(alignment: .leading, spacing: 8) {
            HStack {
                Text("Nearby nodes").font(.headline)
                Spacer()
                ProgressView()
            }
            if fresh.isEmpty {
                Text("Looking… make sure the node is on and not connected to another phone.").foregroundStyle(.secondary)
            }
            ForEach(fresh) { f in
                DeviceRow(icon: "antenna.radiowaves.left.and.right", title: f.name, subtitle: String(localized: "Tap to connect"), onTap: { onPick(f) }) {
                    Image(systemName: "chevron.forward").foregroundStyle(.secondary)
                }
            }
        }
        .padding(.top, 32)
        .onAppear { Bluetooth.shared.scan() }
        .onDisappear { Bluetooth.shared.stopScan() }
    }
}

struct SetupView: View {
    @State private var scanning = false

    var body: some View {
        let mesh = Mesh.shared
        ScrollViewReader { proxy in
            ScrollView {
                VStack(spacing: 0) {
                    Hero(icon: "antenna.radiowaves.left.and.right").padding(.top, 56)
                    Text(verbatim: "Mesh+").font(.largeTitle.weight(.semibold)).padding(.top, 24)
                    Text("Connect your Meshtastic node to start chatting, no internet or cell service needed.")
                        .multilineTextAlignment(.center).foregroundStyle(.secondary).padding(.top, 8)
                    Spacer().frame(height: 40)
                    if mesh.active {
                        ProgressView().controlSize(.large)
                        Text(mesh.status).font(.headline).multilineTextAlignment(.center).padding(.top, 16)
                        if mesh.transport == "Bluetooth" {
                            Text("If iOS asks for a PIN, type the one shown on the node's screen. No screen? It's usually 123456.")
                                .font(.footnote).foregroundStyle(.secondary).multilineTextAlignment(.center).padding(.top, 12)
                        }
                        Button("Cancel") { mesh.forget() }.padding(.top, 16)
                    } else {
                        if !mesh.status.isEmpty {
                            Label(mesh.status, systemImage: "exclamationmark.circle")
                                .padding(16).frame(maxWidth: .infinity, alignment: .leading)
                                .background(Color.red.opacity(0.12), in: RoundedRectangle(cornerRadius: 16))
                                .padding(.bottom, 24)
                        }
                        if !mesh.saved.isEmpty {
                            Text("Your devices").font(.headline).frame(maxWidth: .infinity, alignment: .leading).padding(.bottom, 8)
                            DeviceRows { mesh.connect($0) }.padding(.bottom, 24)
                        }
                        Button { scanning = true } label: {
                            Label(mesh.saved.isEmpty ? String(localized: "Connect with Bluetooth") : String(localized: "Find another node"), systemImage: "antenna.radiowaves.left.and.right")
                                .frame(maxWidth: .infinity)
                        }
                        .buttonStyle(.borderedProminent).controlSize(.large)
                        if scanning {
                            NearbyNodes { f in scanning = false; mesh.connectBle(f.peripheral) }.id("scan")
                        }
                        Button("No radio yet? Try the demo") { scanning = false; mesh.connectDemo() }.padding(.top, 24)
                    }
                }
                .padding(24)
            }
            // The scan list sits below the buttons, off screen on smaller phones: bring it into view.
            .onChange(of: scanning) { _, on in if on { withAnimation { proxy.scrollTo("scan", anchor: .top) } } }
        }
    }
}

// MARK: - Region: a fresh node stays silent until it knows which radio band is legal here

struct RegionView: View {
    let editing: Bool
    @Environment(\.dismiss) private var dismiss
    @State private var picked: Int

    init(editing: Bool) {
        self.editing = editing
        _picked = State(initialValue: editing ? Mesh.shared.region : suggestedRegion(Locale.current.region?.identifier ?? ""))
    }

    var body: some View {
        let mesh = Mesh.shared
        List {
            Section {
                VStack(spacing: 12) {
                    Hero(icon: "globe")
                    Text("Where are you?").font(.title2.weight(.semibold))
                    Text("Your node stays silent until it knows which radio band is legal where you are. Pick the one for your country.")
                        .multilineTextAlignment(.center).foregroundStyle(.secondary)
                }
                .frame(maxWidth: .infinity).listRowBackground(Color.clear)
            }
            Section {
                ForEach(REGIONS.indices, id: \.self) { i in
                    let (code, label) = REGIONS[i]
                    Button { picked = code } label: {
                        HStack {
                            Text(label).foregroundStyle(.primary)
                            Spacer()
                            if picked == code { Image(systemName: "checkmark").foregroundStyle(Color.accentColor) }
                        }
                    }
                }
            }
        }
        .navigationTitle(editing ? String(localized: "Region") : "")
        .navigationBarTitleDisplayMode(.inline)
        .safeAreaInset(edge: .bottom) {
            Button {
                if picked != mesh.region { mesh.saveRegion(picked) }
                if editing { dismiss() }
            } label: { Label("Use this region", systemImage: "checkmark").frame(maxWidth: .infinity) }
                .buttonStyle(.borderedProminent).controlSize(.large).disabled(picked == 0 || !mesh.connected)
                .padding().background(.bar)
        }
    }
}

// MARK: - Identity

extension String {
    /// Trims to fit a protobuf string field of `max` UTF-8 bytes (firmware limits).
    func fitBytes(_ max: Int) -> String {
        var s = self
        while s.utf8.count > max { s.removeLast() }
        return s
    }
}

func initials(_ name: String) -> String {
    let words = name.split(whereSeparator: \.isWhitespace)
    let s = words.count > 1 ? words.map { String($0.prefix(1)) }.joined() : words.first.map(String.init) ?? ""
    return s.uppercased().fitBytes(4)
}

struct NameView: View {
    let editing: Bool
    @Environment(\.dismiss) private var dismiss
    @State private var long: String
    @State private var short: String
    @State private var shortEdited: Bool

    init(editing: Bool) {
        self.editing = editing
        _long = State(initialValue: editing ? Mesh.shared.myLong : "")
        _short = State(initialValue: editing ? Mesh.shared.myShort : "")
        _shortEdited = State(initialValue: editing)
    }

    var body: some View {
        let mesh = Mesh.shared
        let canSave = !long.trimmingCharacters(in: .whitespaces).isEmpty && !short.trimmingCharacters(in: .whitespaces).isEmpty && mesh.connected
        ScrollView {
            VStack(spacing: 12) {
                Hero(icon: "person.text.rectangle").padding(.top, editing ? 24 : 56)
                Text(editing ? String(localized: "Your name") : String(localized: "What should people call you?")).font(.title2.weight(.semibold)).multilineTextAlignment(.center)
                Text("This is how you show up for everyone on the mesh.").foregroundStyle(.secondary).multilineTextAlignment(.center)
                TextField("Name", text: $long)
                    .textInputAutocapitalization(.words).textFieldStyle(.roundedBorder).padding(.top, 20)
                    .onChange(of: long) { _, v in
                        let fit = v.fitBytes(39)
                        if fit != v { long = fit }
                        if !shortEdited { short = initials(fit) }
                    }
                TextField("Short name", text: Binding(get: { short }, set: { short = $0.fitBytes(4); shortEdited = true }))
                    .textInputAutocapitalization(.characters).textFieldStyle(.roundedBorder)
                Text("Up to 4 characters, shown on node screens").font(.footnote).foregroundStyle(.secondary)
                    .frame(maxWidth: .infinity, alignment: .leading)
                Button {
                    mesh.setOwner(long.trimmingCharacters(in: .whitespaces), short.trimmingCharacters(in: .whitespaces))
                    if editing { dismiss() }
                } label: { Label("Save", systemImage: "checkmark").frame(maxWidth: .infinity) }
                    .buttonStyle(.borderedProminent).controlSize(.large).disabled(!canSave).padding(.top, 12)
                if !editing { Button("Skip for now") { mesh.nameAsked() } }
            }
            .padding(24)
        }
        .navigationTitle(editing ? String(localized: "Name") : "")
        .navigationBarTitleDisplayMode(.inline)
    }
}
