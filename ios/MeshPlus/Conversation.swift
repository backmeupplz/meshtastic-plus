import SwiftUI

struct DayChip: View {
    let ms: Double
    var body: some View {
        let d = Date(timeIntervalSince1970: ms / 1000)
        let cal = Calendar.current
        Text(cal.isDateInToday(d) ? "Today" : cal.isDateInYesterday(d) ? "Yesterday" : d.formatted(date: .abbreviated, time: .omitted))
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
        let (icon, label) = m.status == "✓" ? ("checkmark", m.peer != 0 ? "Delivered" : "Relayed by the mesh")
            : failed ? ("exclamationmark.circle", m.peer != 0 ? "Not delivered" : "Not confirmed") : ("clock", "Sending")
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
                Text(m.peer != 0 ? "Not delivered · Tap to retry" : "Not confirmed · Tap to resend").font(.caption2).foregroundStyle(.red)
            }
        }
        .frame(maxWidth: .infinity, alignment: m.mine ? .trailing : .leading)
        .padding(.top, showName ? 6 : 0)
    }
}

struct ConversationView: View {
    let convo: String
    let onProfile: (Int64) -> Void
    @Environment(\.dismiss) private var dismiss
    @Environment(\.openURL) private var openURL
    @State private var text = ""
    @State private var invite = false
    @State private var leave = false
    @State private var reporting: Message?
    @State private var noMail = false
    @FocusState private var typing: Bool

    private var peer: Int64 { convo.hasPrefix("dm:") ? Int64(convo.dropFirst(3)) ?? 0 : 0 }
    private var channel: Int { convo.hasPrefix("ch:") ? Int(convo.dropFirst(3)) ?? 0 : 0 }

    /// Opens an email to the developer with the message, who sent it and when.
    private func report(_ m: Message) {
        let mesh = Mesh.shared
        let place = m.peer != 0 ? "a direct message" : "the room \(mesh.rooms[m.channel]?.title ?? "#\(m.channel)")"
        let when = Date(timeIntervalSince1970: m.time / 1000).formatted(date: .abbreviated, time: .standard)
        let body = "I'm reporting this message in Mesh+.\n\nFrom: \(mesh.nameOf(m.from)) (\(hexId(m.from)))\nTime: \(when)\nIn: \(place)\n\n" +
            "Message:\n\(m.text)\n\nWhat's wrong with it (optional):\n"
        openURL(mailURL(subject: "Mesh+ report: message from \(hexId(m.from))", body: body)) { accepted in if !accepted { noMail = true } }
    }

    var body: some View {
        let mesh = Mesh.shared
        let room = peer == 0 ? mesh.rooms[channel] : nil
        let node = mesh.nodes[peer]
        let messages = mesh.messages.filter { $0.convo == convo }
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
                                    Button { reporting = m } label: { Label("Report", systemImage: "exclamationmark.bubble") }
                                    Button(role: .destructive) {
                                        mesh.setBlocked(m.from, true)
                                        if peer != 0 { dismiss() }
                                    } label: { Label("Block \(mesh.nameOf(m.from))", systemImage: "hand.raised") }
                                }
                            }
                            .id(i)
                    }
                }
                .padding(12)
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
                                       description: Text(peer != 0 ? "Messages here are end-to-end encrypted." : "Say hi! Everyone in this room will see it."))
            }
        }
        .safeAreaInset(edge: .bottom) {
            VStack(alignment: .leading, spacing: 4) {
                HStack(alignment: .bottom, spacing: 8) {
                    TextField(mesh.connected ? "Message \(peer != 0 ? node?.name ?? "" : room?.title ?? "")" : "Waiting for the node…",
                              text: $text, axis: .vertical)
                        .lineLimit(1...5)
                        .focused($typing)
                        .padding(.horizontal, 14).padding(.vertical, 9)
                        .background(Color(.secondarySystemBackground), in: RoundedRectangle(cornerRadius: 20))
                    Button {
                        RatingPrompt.sent()
                        mesh.send(text.trimmingCharacters(in: .whitespacesAndNewlines), channel: channel, peer: peer)
                        text = ""
                    } label: { Image(systemName: "arrow.up.circle.fill").font(.system(size: 34)) }
                        .disabled(!canSend).accessibilityLabel("Send")
                }
                if tooLong { Text("Too long for one mesh message").font(.caption).foregroundStyle(.red).padding(.leading, 14) }
            }
            .padding(.horizontal, 12).padding(.vertical, 8)
            .background(.bar)
        }
        .navigationBarTitleDisplayMode(.inline)
        .toolbar {
            ToolbarItem(placement: .principal) {
                if peer != 0 {
                    Button { onProfile(peer) } label: {
                        StatusTitle(title: node?.name ?? hexId(peer), subtitle: node.map { heard($0, nowMs()) } ?? "", ok: node?.online(nowMs()) == true)
                    }
                    .foregroundStyle(.primary)
                } else {
                    VStack(spacing: 1) {
                        Text(room?.title ?? "Room").font(.headline)
                        Text(room?.isPublic != false ? "Public room, anyone nearby can read it" : "Private room").font(.caption).foregroundStyle(.secondary)
                    }
                }
            }
            ToolbarItemGroup(placement: .topBarTrailing) {
                if room != nil { Button { invite = true } label: { Image(systemName: "qrcode") }.accessibilityLabel("Invite") }
                Menu {
                    Button { mesh.toggleMute(convo) } label: {
                        mesh.isMuted(convo) ? Label("Unmute", systemImage: "bell") : Label("Mute", systemImage: "bell.slash")
                    }
                    if let node {
                        Button { mesh.setFavorite(peer, !node.favorite) } label: {
                            Label(node.favorite ? "Remove from favorites" : "Favorite", systemImage: node.favorite ? "star.slash" : "star")
                        }
                        .disabled(!mesh.connected)
                    }
                    if peer != 0 {
                        Button(role: .destructive) { mesh.setBlocked(peer, true); dismiss() } label: {
                            Label("Block \(mesh.nameOf(peer))", systemImage: "hand.raised")
                        }
                    }
                    if let room, !room.primary {
                        Button(role: .destructive) { leave = true } label: { Label("Leave room", systemImage: "rectangle.portrait.and.arrow.right") }
                    }
                } label: { Image(systemName: "ellipsis.circle") }
                    .accessibilityLabel("More")
            }
        }
        .onAppear {
            mesh.openConvo = convo
            mesh.markRead(convo)
            Notify.clear(convo)
        }
        .onDisappear { mesh.openConvo = nil }
        .sheet(isPresented: $invite) { if let room { InviteSheet(room: room) } }
        .alert("Leave \(room?.title ?? "this room")?", isPresented: $leave) {
            Button("Leave", role: .destructive) { if let room { mesh.leaveRoom(room.index) }; dismiss() }
            Button("Cancel", role: .cancel) {}
        } message: { Text("You'll need a new invite to come back.") }
        .confirmationDialog("Report this message?", isPresented: Binding(get: { reporting != nil }, set: { if !$0 { reporting = nil } }),
                            titleVisibility: .visible, presenting: reporting) { m in
            Button("Report and block \(mesh.nameOf(m.from))", role: .destructive) {
                report(m)
                mesh.setBlocked(m.from, true)
                if peer != 0 { dismiss() }
            }
            Button("Report only") { report(m) }
            Button("Cancel", role: .cancel) {}
        } message: { m in
            Text("Mesh+ opens an email to the developer with this message, who sent it and when. Blocking also hides everything from \(mesh.nameOf(m.from)) on this phone.")
        }
        .alert("No email app", isPresented: $noMail) {
            Button("Copy address") { UIPasteboard.general.string = supportEmail }
            Button("OK", role: .cancel) {}
        } message: { Text("Send your report to \(supportEmail).") }
    }
}
