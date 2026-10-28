import CoreBluetooth
import Observation

let SERVICE = CBUUID(string: "6ba1b218-15a8-461f-9fa8-5dcae273eafd")
private let TO_RADIO = CBUUID(string: "f75c76d2-129e-4dad-a1dd-7866124401e7")
private let FROM_RADIO = CBUUID(string: "2c55e69e-4993-11ed-b878-0242ac120002")
private let FROM_NUM = CBUUID(string: "ed9da18c-a800-4f66-a670-aa7547e34453")

struct Found: Identifiable {
    let peripheral: CBPeripheral
    let name: String
    var id: UUID { peripheral.identifier }
}

/// The phone's Bluetooth: scanning for nodes, plus the connection events CoreBluetooth reports to the central.
/// Created on first use, so the permission prompt only shows once the user asks to connect.
@Observable final class Bluetooth: NSObject, CBCentralManagerDelegate {
    static let shared = Bluetooth()
    var found: [UUID: Found] = [:]
    @ObservationIgnored private var central: CBCentralManager?
    @ObservationIgnored private var pending: [() -> Void] = [] // work waiting for Bluetooth to be ready
    @ObservationIgnored weak var link: BleLink?

    /// Runs `f` once Bluetooth is on and allowed.
    func whenOn(_ f: @escaping () -> Void) {
        if let central, central.state == .poweredOn { return f() }
        pending.append(f)
        if central == nil { central = CBCentralManager(delegate: self, queue: nil) }
    }

    func centralManagerDidUpdateState(_ c: CBCentralManager) {
        let mesh = Mesh.shared
        switch c.state {
        case .poweredOn:
            let work = pending
            pending = []
            work.forEach { $0() }
            link?.reconnect() // Bluetooth was switched off and on again

        case .unauthorized:
            pending = []
            if mesh.transport == "Bluetooth" { mesh.disconnect() }
            mesh.status = String(localized: "Mesh+ isn't allowed to use Bluetooth. Turn it on in Settings → Apps → Mesh+.")
        case .poweredOff:
            if mesh.transport == "Bluetooth" { mesh.status = String(localized: "Bluetooth is off. Turn it on in Control Center.") }
        case .unsupported:
            pending = []
            mesh.status = String(localized: "This device has no Bluetooth LE.")
        default: break
        }
    }

    func scan() {
        whenOn { [self] in
            found = [:]
            central?.scanForPeripherals(withServices: [SERVICE])
        }
    }

    func stopScan() { central?.stopScan() }

    func centralManager(_ c: CBCentralManager, didDiscover p: CBPeripheral, advertisementData ad: [String: Any], rssi: NSNumber) {
        found[p.identifier] = Found(peripheral: p, name: ad[CBAdvertisementDataLocalNameKey] as? String ?? p.name ?? String(localized: "Meshtastic node"))
    }

    func peripheral(_ id: UUID) -> CBPeripheral? { central?.retrievePeripherals(withIdentifiers: [id]).first }
    func connect(_ p: CBPeripheral) { central?.connect(p) }
    func cancel(_ p: CBPeripheral) { central?.cancelPeripheralConnection(p) }

    func centralManager(_ c: CBCentralManager, didConnect p: CBPeripheral) { link?.connected(p) }
    func centralManager(_ c: CBCentralManager, didDisconnectPeripheral p: CBPeripheral, error: Error?) { link?.disconnected(p) }
    func centralManager(_ c: CBCentralManager, didFailToConnect p: CBPeripheral, error: Error?) { link?.disconnected(p) }
}

/// Meshtastic BLE API: write ToRadio, read FromRadio until empty, FromNum notifies when there's more.
/// The characteristics are encrypted, so the first connection makes iOS ask for the node's PIN.
final class BleLink: NSObject, RadioLink, CBPeripheralDelegate {
    private let peripheral: CBPeripheral
    private var toRadio: CBCharacteristic?
    private var fromRadio: CBCharacteristic?
    private var reading = false
    private var closed = false

    init(_ p: CBPeripheral) {
        peripheral = p
        super.init()
        p.delegate = self
        Bluetooth.shared.link = self
        Bluetooth.shared.connect(p)
    }

    func send(_ bytes: [UInt8]) {
        guard let c = toRadio else { return }
        peripheral.writeValue(Data(bytes), for: c, type: .withResponse)
    }

    func close() {
        closed = true
        Bluetooth.shared.cancel(peripheral)
    }

    func connected(_ p: CBPeripheral) {
        guard p == peripheral, !closed else { return }
        p.discoverServices([SERVICE])
    }

    func reconnect() {
        if !closed && peripheral.state == .disconnected { Bluetooth.shared.connect(peripheral) }
    }

    func disconnected(_ p: CBPeripheral) {
        guard p == peripheral, !closed else { return }
        toRadio = nil; fromRadio = nil; reading = false
        Mesh.shared.linkDown(self, String(localized: "Out of reach, reconnecting…"))
        Bluetooth.shared.connect(p) // never times out: iOS reconnects whenever the node is back in range
    }

    func peripheral(_ p: CBPeripheral, didDiscoverServices error: Error?) {
        guard let s = p.services?.first(where: { $0.uuid == SERVICE }) else {
            return Mesh.shared.failed(self, String(localized: "No Meshtastic service: the node may be waiting in update mode."))
        }
        p.discoverCharacteristics([TO_RADIO, FROM_RADIO, FROM_NUM], for: s)
    }

    func peripheral(_ p: CBPeripheral, didDiscoverCharacteristicsFor s: CBService, error: Error?) {
        let cs = s.characteristics ?? []
        toRadio = cs.first { $0.uuid == TO_RADIO }
        fromRadio = cs.first { $0.uuid == FROM_RADIO }
        guard toRadio != nil, fromRadio != nil, let num = cs.first(where: { $0.uuid == FROM_NUM }) else {
            return Mesh.shared.failed(self, String(localized: "This node doesn't speak the Meshtastic Bluetooth API."))
        }
        p.setNotifyValue(true, for: num) // encrypted: pairing (the PIN prompt) happens here
    }

    func peripheral(_ p: CBPeripheral, didUpdateNotificationStateFor c: CBCharacteristic, error: Error?) {
        if error != nil {
            Mesh.shared.failed(self, String(localized: "Pairing didn't work. Check the PIN and try again. If it keeps failing, forget the node in iOS Settings → Bluetooth first."))
        } else {
            Mesh.shared.linkUp(self)
        }
    }

    func peripheral(_ p: CBPeripheral, didWriteValueFor c: CBCharacteristic, error: Error?) {
        read() // a write usually means the node has something for us (e.g. the config dump)
    }

    func peripheral(_ p: CBPeripheral, didUpdateValueFor c: CBCharacteristic, error: Error?) {
        if c.uuid == FROM_NUM { return read() }
        reading = false
        if error == nil, let v = c.value, !v.isEmpty {
            Mesh.shared.fromRadio(self, Array(v))
            read() // drain until empty
        }
    }

    private func read() {
        guard !reading, let c = fromRadio else { return }
        reading = true
        peripheral.readValue(for: c)
    }
}
