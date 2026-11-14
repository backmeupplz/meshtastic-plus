import MapKit
import SwiftUI

/// Every node that has shared a position, on Apple Maps. Tapping one opens its profile.
struct MapTab: View {
    let onProfile: (Int64) -> Void

    var body: some View {
        TimelineView(.periodic(from: .now, by: 30)) { ctx in
            let mesh = Mesh.shared
            let now = ctx.date.timeIntervalSince1970 * 1000
            let placed = mesh.nodes.values.filter { $0.lat != nil && $0.lon != nil && !mesh.blocked.contains($0.num) }
                .sorted { $0.num < $1.num }
            if placed.isEmpty {
                ContentUnavailableView("No locations yet", systemImage: "map",
                                       description: Text("Nodes show up here once they share their location."))
            } else {
                Map(initialPosition: .automatic) { // .automatic fits every node in view
                    ForEach(placed, id: \.num) { n in
                        Annotation(n.name, coordinate: CLLocationCoordinate2D(latitude: n.lat!, longitude: n.lon!)) {
                            Button { onProfile(n.num) } label: {
                                Text(n.short.isEmpty ? initials(n.name) : n.short)
                                    .font(.caption.weight(.bold)).foregroundStyle(.white).lineLimit(1).minimumScaleFactor(0.6)
                                    .frame(width: 40, height: 40)
                                    .background(nodeColor(n.num), in: Circle())
                                    .overlay(Circle().stroke(.white, lineWidth: 2.5))
                                    .opacity(n.num == mesh.myNum || n.online(now) ? 1 : 0.5)
                            }
                            .accessibilityLabel(n.name)
                        }
                    }
                }
            }
        }
    }
}
