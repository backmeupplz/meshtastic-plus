import SwiftUI

struct DayChip: View {
    let ms: Double
    var body: some View {
        let d = Date(timeIntervalSince1970: ms / 1000)
        let cal = Calendar.current
        Text(cal.isDateInToday(d) ? String(localized: "Today") : cal.isDateInYesterday(d) ? String(localized: "Yesterday") : d.formatted(date: .abbreviated, time: .omitted))
            .font(.caption.weight(.medium))
            .padding(.horizontal, 12).padding(.vertical, 4)
            .background(Color(.secondarySystemBackground), in: Capsule())
            .frame(maxWidth: .infinity)
            .padding(.vertical, 8)
    }
}

struct Bubble: View {
    let m: Message, showName: Bool

    var body: some View {
        let mesh = Mesh.shared
        let failed = m.mine && m.status == "✗"
        let (icon, label) = m.status == "✓" ? ("checkmark", m.peer != 0 ? String(localized: "Delivered") : String(localized: "Relayed by the mesh"))
            : failed ? ("exclamationmark.circle", m.peer != 0 ? String(localized: "Not delivered") : String(localized: "Not confirmed"))
            : ("clock", String(localized: "Sending"))
        VStack(alignment: m.mine ? .trailing : .leading, spacing: 2) {
            if !m.mine && showName {
                Text(mesh.nodes[m.from]?.name ?? m.name).font(.caption.weight(.semibold)).foregroundStyle(nodeColor(m.from)).padding(.leading, 4)
            }
            VStack(alignment: .trailing, spacing: 2) {
                Text(m.text).multilineTextAlignment(.leading)
                HStack(spacing: 4) {
                    Text(Date(timeIntervalSince1970: m.time / 1000).formatted(date: .omitted, time: .shortened))
                    if m.mine { Image(systemName: icon).accessibilityLabel(label) }
                }
                .font(.caption2).opacity(0.7)
            }
            .padding(.horizontal, 14).padding(.top, 8).padding(.bottom, 6)
            .foregroundStyle(m.mine ? Color.white : .primary)
            .background(m.mine ? Color("Bubble") : Color(.secondarySystemBackground), in: RoundedRectangle(cornerRadius: 20))
            .frame(maxWidth: 290, alignment: m.mine ? .trailing : .leading)
            .onTapGesture { if failed && mesh.connected { mesh.retry(m) } }
            if failed {
                // Room messages have no delivery receipts, only "someone relayed it"; missing that isn't proof of failure.
                Text(m.peer != 0 ? String(localized: "Not delivered · Tap to retry") : String(localized: "Not confirmed · Tap to resend")).font(.caption2).foregroundStyle(.red)
            }
        }
        .frame(maxWidth: .infinity, alignment: m.mine ? .trailing : .leading)
        .padding(.top, showName ? 6 : 0)
    }
}

/// A room or DM. Its draft, sheets and prompts live in `HomeModel`, so folding the phone, which swaps Home's layout
/// and rebuilds this view, keeps them.
struct ConversationView: View {
    let convo: String
    @Environment(HomeModel.self) private var model
    @FocusState private var typing: Bool
    @State private var instance = UUID()

    private var peer: Int64 { convo.hasPrefix("dm:") ? Int64(convo.dropFirst(3)) ?? 0 : 0 }
    private var channel: Int { convo.hasPrefix("ch:") ? Int(convo.dropFirst(3)) ?? 0 : 0 }

    var body: some View {
        let mesh = Mesh.shared
        let room = peer == 0 ? mesh.rooms[channel] : nil
        let node = mesh.nodes[peer]
        let messages = mesh.messages.filter { $0.convo == convo }
        let text = model.drafts[convo] ?? ""
        let tooLong = text.trimmingCharacters(in: .whitespacesAndNewlines).utf8.count > 200 // firmware text payload limit
        let canSend = mesh.connected && !text.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty && !tooLong
        ScrollViewReader { proxy in
            ScrollView {
                LazyVStack(spacing: 4) {
                    ForEach(messages.indices, id: \.self) { i in
                        let m = messages[i]
                        let prev = i > 0 ? messages[i - 1] : nil
                        let newDay = prev.map { !Calendar.current.isDate(Date(timeIntervalSince1970: $0.time / 1000), inSameDayAs: Date(timeIntervalSince1970: m.time / 1000)) } ?? true
                        if newDay { DayChip(ms: m.time) }
                        Bubble(m: m, showName: peer == 0 && (newDay || prev?.from != m.from || prev?.mine != m.mine))
                            .contextMenu {
                                Button { UIPasteboard.general.string = m.text } label: { Label("Copy", systemImage: "doc.on.doc") }
                                if !m.mine {
                                    Button { model.reporting = m } label: { Label("Report", systemImage: "exclamationmark.bubble") }
                                    Button(role: .destructive) {
                                        mesh.setBlocked(m.from, true)
                                        if peer != 0 { model.close(convo) }
                                    } label: { Label("Block \(mesh.nameOf(m.from))", systemImage: "hand.raised") }
                                }
                            }
                            .id(i)
                    }
                }
                .padding(12)
                .readableWidth(720)
            }
            .defaultScrollAnchor(.bottom)
            .scrollDismissesKeyboard(.immediately)
            .onTapGesture { typing = false }
            .onChange(of: messages.count) { _, n in
                mesh.markRead(convo)
                withAnimation { proxy.scrollTo(n - 1, anchor: .bottom) }
            }
        }
        .overlay {
            if messages.isEmpty {
                ContentUnavailableView("No messages yet", systemImage: "bubble.left.and.bubble.right",
                                       description: Text(peer != 0 ? String(localized: "Messages here are end-to-end encrypted.")
                                                       : String(localized: "Say hi! Everyone in this room will see it.")))
            }
        }
        .safeAreaInset(edge: .bottom) {
            VStack(alignment: .leading, spacing: 4) {
                HStack(alignment: .bottom, spacing: 8) {
                    TextField(mesh.connected ? String(localized: "Message \(peer != 0 ? node?.name ?? "" : room?.title ?? "")") : String(localized: "Waiting for the node…"),
                              text: Binding(get: { model.drafts[convo] ?? "" }, set: { model.drafts[convo] = $0 }), axis: .vertical)
                        .lineLimit(1...5)
                        .focused($typing)
                        .padding(.horizontal, 14).padding(.vertical, 9)
                        .background(Color(.secondarySystemBackground), in: RoundedRectangle(cornerRadius: 20))
                    Button {
                        RatingPrompt.sent()
                        mesh.send(text.trimmingCharacters(in: .whitespacesAndNewlines), channel: channel, peer: peer)
                        model.drafts[convo] = nil
                    } label: { Image(systemName: "arrow.up.circle.fill").font(.system(size: 34)) }
                        .disabled(!canSend).accessibilityLabel("Send")
                }
                if tooLong { Text("Too long for one mesh message").font(.caption).foregroundStyle(.red).padding(.leading, 14) }
            }
            .readableWidth(720)
            .padding(.horizontal, 12).padding(.vertical, 8)
            .background(.bar)
        }
        .navigationBarTitleDisplayMode(.inline)
        .toolbar {
            ToolbarItem(placement: .principal) {
                if peer != 0 {
                    Button { model.sheet = .profile(peer) } label: {
                        StatusTitle(title: node?.name ?? hexId(peer), subtitle: node.map { heard($0, nowMs()) } ?? "", ok: node?.online(nowMs()) == true)
                    }
                    .foregroundStyle(.primary)
                } else {
                    VStack(spacing: 1) {
                        Text(room?.title ?? String(localized: "Room")).font(.headline)
                        Text(room?.isPublic != false ? String(localized: "Public room, anyone nearby can read it") : String(localized: "Private room")).font(.caption).foregroundStyle(.secondary)
                    }
                }
            }
            ToolbarItemGroup(placement: .topBarTrailing) {
                if room != nil { Button { model.sheet = .invite(channel) } label: { Image(systemName: "qrcode") }.accessibilityLabel("Invite") }
                Menu {
                    Button { mesh.toggleMute(convo) } label: {
                        mesh.isMuted(convo) ? Label("Unmute", systemImage: "bell") : Label("Mute", systemImage: "bell.slash")
                    }
                    if let node {
                        Button { mesh.setFavorite(peer, !node.favorite) } label: {
                            Label(node.favorite ? String(localized: "Remove from favorites") : String(localized: "Favorite"), systemImage: node.favorite ? "star.slash" : "star")
                        }
                        .disabled(!mesh.connected)
                    }
                    if peer != 0 {
                        Button(role: .destructive) { mesh.setBlocked(peer, true); model.close(convo) } label: {
                            Label("Block \(mesh.nameOf(peer))", systemImage: "hand.raised")
                        }
                    }
                    if let room, !room.primary {
                        Button(role: .destructive) { model.leaving = room.index } label: { Label("Leave room", systemImage: "rectangle.portrait.and.arrow.right") }
                    }
                } label: { Image(systemName: "ellipsis.circle") }
                    .accessibilityLabel("More")
            }
        }
        .onAppear {
            mesh.markRead(convo)
            Notify.clear(convo)
            model.composer = instance
            // The keyboard was up in the layout this one replaced: bring it back once this one is on screen.
            if model.focused == convo { DispatchQueue.main.asyncAfter(deadline: .now() + 0.15) { typing = true } }
        }
        .onChange(of: typing) { _, on in
            let model = model, me = instance
            if on { model.focused = convo; model.composer = me; return }
            // Folding takes the keyboard from the outgoing composer too, and that isn't the user closing it: forget it
            // only if no other composer came in meanwhile.
            DispatchQueue.main.asyncAfter(deadline: .now() + 0.25) { if model.composer == me && model.focused == convo { model.focused = nil } }
        }
    }
}

/// The conversation's prompts, presented from above Home's layout switch so folding the phone doesn't dismiss them.
struct ConversationPrompts: ViewModifier {
    @Bindable var model: HomeModel
    @Environment(\.openURL) private var openURL

    /// Opens an email to the developer with the message, who sent it and when.
    private func report(_ m: Message) {
        let mesh = Mesh.shared
        let place = m.peer != 0 ? "a direct message" : "the room \(mesh.rooms[m.channel]?.title ?? "#\(m.channel)")"
        let when = Date(timeIntervalSince1970: m.time / 1000).formatted(date: .abbreviated, time: .standard)
        let body = "I'm reporting this message in Mesh+.\n\nFrom: \(mesh.nameOf(m.from)) (\(hexId(m.from)))\nTime: \(when)\nIn: \(place)\n\n" +
            "Message:\n\(m.text)\n\nWhat's wrong with it (optional):\n"
        openURL(mailURL(subject: "Mesh+ report: message from \(hexId(m.from))", body: body)) { accepted in if !accepted { model.noMail = true } }
    }

    func body(content: Content) -> some View {
        let mesh = Mesh.shared
        let room = model.leaving.flatMap { mesh.rooms[$0] }
        content
            // `presenting` hands the room to the action: dismissing the alert can clear `leaving` before the action runs.
            .alert(room.map { String(localized: "Leave \($0.title)?") } ?? String(localized: "Leave this room?"), isPresented: Binding(get: { model.leaving != nil }, set: { if !$0 { model.leaving = nil } }),
                   presenting: model.leaving) { index in
                Button("Leave", role: .destructive) { mesh.leaveRoom(index); model.close(convoKey(index, 0)) }
                Button("Cancel", role: .cancel) {}
            } message: { _ in Text("You'll need a new invite to come back.") }
            .confirmationDialog("Report this message?", isPresented: Binding(get: { model.reporting != nil }, set: { if !$0 { model.reporting = nil } }),
                                titleVisibility: .visible, presenting: model.reporting) { m in
                Button("Report and block \(mesh.nameOf(m.from))", role: .destructive) {
                    report(m)
                    mesh.setBlocked(m.from, true)
                    if m.peer != 0 { model.close(m.convo) }
                }
                Button("Report only") { report(m) }
                Button("Cancel", role: .cancel) {}
            } message: { m in
                Text("Mesh+ opens an email to the developer with this message, who sent it and when. Blocking also hides everything from \(mesh.nameOf(m.from)) on this phone.")
            }
            .alert("No email app", isPresented: $model.noMail) {
                Button("Copy address") { UIPasteboard.general.string = supportEmail }
                Button("OK", role: .cancel) {}
            } message: { Text("Send your report to \(supportEmail).") }
    }
}
