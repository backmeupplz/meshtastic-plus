import Foundation

// Just enough protobuf to speak Meshtastic's mesh.proto (field numbers from meshtastic/protobufs).
// A port of the Android app's Proto.kt.

struct Pb {
    private(set) var build: [UInt8] = []

    private mutating func varint(_ v: UInt64) {
        var x = v
        while x >= 0x80 { build.append(UInt8(x & 0x7F) | 0x80); x >>= 7 }
        build.append(UInt8(x))
    }

    func uint(_ field: Int, _ v: Int64) -> Pb { v == 0 ? self : set(field, v) }
    /// Like `uint` but also writes 0, to override an earlier value. Negative int32 works too (10-byte varint).
    func set(_ field: Int, _ v: Int64) -> Pb { var p = self; p.varint(UInt64(field << 3)); p.varint(UInt64(bitPattern: v)); return p }
    func fixed32(_ field: Int, _ v: Int64) -> Pb {
        var p = self
        p.varint(UInt64(field << 3 | 5))
        for i in 0..<4 { p.build.append(UInt8(truncatingIfNeeded: v >> (8 * i))) }
        return p
    }
    func bytes(_ field: Int, _ v: [UInt8]) -> Pb {
        var p = self
        p.varint(UInt64(field << 3 | 2)); p.varint(UInt64(v.count)); p.build += v
        return p
    }
    func str(_ field: Int, _ v: String) -> Pb { bytes(field, Array(v.utf8)) }
    func msg(_ field: Int, _ m: Pb) -> Pb { bytes(field, m.build) }
}

/// Parsed message: field number -> values (Int64 for varint/fixed, bytes for length-delimited). Nil when malformed.
struct Msg {
    enum Value { case int(Int64), bytes([UInt8]) }
    private(set) var fields: [Int: [Value]] = [:]

    init?(_ b: [UInt8]) {
        var i = 0
        func varint() -> UInt64? {
            var r: UInt64 = 0, s: UInt64 = 0
            while i < b.count {
                let x = b[i]; i += 1
                if s < 64 { r |= UInt64(x & 0x7F) << s }
                if x & 0x80 == 0 { return r }
                s += 7
            }
            return nil
        }
        func fixed(_ n: Int) -> UInt64? {
            guard i + n <= b.count else { return nil }
            var r: UInt64 = 0
            for k in 0..<n { r |= UInt64(b[i + k]) << (8 * k) }
            i += n
            return r
        }
        while i < b.count {
            guard let tag = varint(), tag >> 3 < 1 << 29 else { return nil }
            let v: Value
            switch tag & 7 {
            case 0: guard let x = varint() else { return nil }; v = .int(Int64(bitPattern: x))
            case 1: guard let x = fixed(8) else { return nil }; v = .int(Int64(bitPattern: x))
            case 2:
                guard let n = varint(), n <= UInt64(b.count - i) else { return nil }
                v = .bytes(Array(b[i..<i + Int(n)])); i += Int(n)
            case 5: guard let x = fixed(4) else { return nil }; v = .int(Int64(x))
            default: return nil
            }
            fields[Int(tag >> 3), default: []].append(v)
        }
    }

    func has(_ f: Int) -> Bool { fields[f] != nil }
    func long(_ f: Int) -> Int64 { if case .int(let x) = fields[f]?.last { return x }; return 0 }
    func bytes(_ f: Int) -> [UInt8]? { if case .bytes(let x) = fields[f]?.last { return x }; return nil }
    func str(_ f: Int) -> String { bytes(f).map { String(decoding: $0, as: UTF8.self) } ?? "" }
    func msg(_ f: Int) -> Msg? { bytes(f).flatMap(Msg.init) }
    /// Every value of a repeated length-delimited field.
    func all(_ f: Int) -> [[UInt8]] { (fields[f] ?? []).compactMap { if case .bytes(let x) = $0 { return x }; return nil } }
    /// A float field (fixed32 bits).
    func float(_ f: Int) -> Float? { has(f) ? Float(bitPattern: UInt32(truncatingIfNeeded: long(f))) : nil }
}

func base64url(_ b: [UInt8]) -> String {
    Data(b).base64EncodedString().replacingOccurrences(of: "+", with: "-").replacingOccurrences(of: "/", with: "_")
        .replacingOccurrences(of: "=", with: "")
}

/// Channel settings inside a meshtastic.org/e/#... invite link (a base64url-encoded ChannelSet).
func inviteRooms(_ link: String) -> [[UInt8]] {
    let trimmed = link.trimmingCharacters(in: .whitespacesAndNewlines)
    guard let hash = trimmed.firstIndex(of: "#") else { return [] }
    var s = trimmed[trimmed.index(after: hash)...].replacingOccurrences(of: "-", with: "+").replacingOccurrences(of: "_", with: "/")
    if s.isEmpty { return [] }
    s += String(repeating: "=", count: (4 - s.count % 4) % 4)
    guard let data = Data(base64Encoded: s), let set = Msg(Array(data)) else { return [] }
    return set.all(1)
}
