package com.backmeupplz.meshtasticplus

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.BluetoothStatusCodes
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.hardware.usb.UsbManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.core.content.IntentCompat
import androidx.core.content.edit
import com.hoho.android.usbserial.driver.UsbSerialPort
import com.hoho.android.usbserial.driver.UsbSerialProber
import com.hoho.android.usbserial.util.SerialInputOutputManager
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID
import kotlin.random.Random

data class Message(
    val id: Long, val name: String, val text: String, val time: Long,
    val mine: Boolean, val status: String = "", val from: Long = 0,
    val channel: Int = 0, val peer: Long = 0, // peer != 0: direct message with that node
) {
    val convo get() = convoKey(channel, peer)
}

fun convoKey(channel: Int, peer: Long) = if (peer != 0L) "dm:$peer" else "ch:$channel"

data class Node(
    val num: Long, val long: String = "", val short: String = "",
    val lastHeard: Long = 0, val snr: Float? = null, val hops: Int? = null,
    val favorite: Boolean = false, val battery: Int? = null,
) {
    val name get() = long.ifEmpty { id }
    val id get() = "!%08x".format(num)
    fun online(now: Long) = now - lastHeard < 2 * 3600_000 // same 2h rule as the official apps
}

/** A Meshtastic channel ("room"): anyone with its name and key can read and write it. */
class Room(val index: Int, val name: String, val psk: ByteArray, val primary: Boolean, val settings: ByteArray) {
    val title get() = name.ifEmpty { if (psk.contentEquals(byteArrayOf(1))) "Public" else "Primary" }
    val public get() = psk.size <= 1 // no key, or one of the well-known default keys
}

interface Link {
    fun send(toRadio: ByteArray)
    fun close()
}

const val BROADCAST = 0xFFFFFFFFL
private const val TEXT_APP = 1L
private const val NODEINFO_APP = 4L
private const val ROUTING_APP = 5L
private const val ADMIN_APP = 6L
private const val TELEMETRY_APP = 67L
private const val USB_PERMISSION = "com.backmeupplz.meshtasticplus.USB_PERMISSION"
private const val INVITE = "https://meshtastic.org/e/#"
private val DEFAULT_NAME = Regex("Meshtastic [0-9a-f]{4}")

/** The whole app state. Lives as long as the process, so rotation/backgrounding keeps the connection. */
@SuppressLint("MissingPermission", "StaticFieldLeak") // permissions are requested in MainActivity; ctx is the app context
object Mesh {
    val messages = mutableStateListOf<Message>()
    val nodes = mutableStateMapOf<Long, Node>()
    val rooms = mutableStateMapOf<Int, Room>() // by channel index, 0 = primary
    val lastRead = mutableStateMapOf<String, Long>() // conversation -> time it was last open
    val saved = mutableStateListOf<Pair<String, String>>() // Bluetooth nodes used before: address to name
    var status by mutableStateOf("")
    var transport by mutableStateOf("") // "Bluetooth" / "USB cable"
    var active by mutableStateOf(false) // a link exists: pairing, connecting or connected
    var ready by mutableStateOf(false) // synced with the node at least once on this link
    var connected by mutableStateOf(false) // link is up right now
    var askName by mutableStateOf(false) // node still has its factory name: offer to pick one
    var region by mutableIntStateOf(-1) // LoRa region code, 0 = unset (radio won't transmit), -1 = not known yet
    var myNum by mutableStateOf(0L)
    var myLong by mutableStateOf("")
    var myShort by mutableStateOf("")

    private lateinit var ctx: Context
    private lateinit var file: File
    private val main = Handler(Looper.getMainLooper())
    private var link: Link? = null
    private var bleAddress: String? = null
    private var lora: ByteArray? = null // the node's LoRaConfig as received, so we can change one field and send it back
    private var viaUsb = false // keep reconnecting over USB when the node re-enumerates (e.g. reboot)

    private val heartbeat = object : Runnable {
        override fun run() {
            link?.send(Pb().msg(7, Pb()).build()) // keeps the serial API session alive
            main.postDelayed(this, 60_000)
        }
    }

    fun init(context: Context) {
        if (::ctx.isInitialized) return
        ctx = context.applicationContext
        file = File(ctx.filesDir, "messages.json")
        if (file.exists()) runCatching {
            val a = JSONArray(file.readText())
            for (i in 0 until a.length()) a.getJSONObject(i).run {
                messages += Message(getLong("id"), getString("name"), getString("text"), getLong("time"),
                    getBoolean("mine"), optString("status"), optLong("from"), optInt("channel"), optLong("peer"))
            }
        }
        runCatching { JSONObject(prefs().getString("read", "{}")!!).run { keys().forEach { lastRead[it] = getLong(it) } } }
        runCatching {
            val a = JSONArray(prefs().getString("saved", "[]"))
            for (i in 0 until a.length()) a.getJSONArray(i).run { saved += getString(0) to getString(1) }
        }
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(c: Context, i: Intent) {
                when (i.action) {
                    UsbManager.ACTION_USB_DEVICE_ATTACHED -> usbAttached()
                    USB_PERMISSION -> if (i.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)) connectUsb()
                    else status = "USB permission denied"
                    BluetoothDevice.ACTION_BOND_STATE_CHANGED -> {
                        val d = IntentCompat.getParcelableExtra(i, BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
                        if (d == null || d.address != prefs().getString("ble", null)) return
                        when (i.getIntExtra(BluetoothDevice.EXTRA_BOND_STATE, 0)) {
                            BluetoothDevice.BOND_BONDED -> if (link == null) connectBle(d)
                            BluetoothDevice.BOND_NONE -> if (link == null) { active = false; status = "Pairing failed, try again" }
                        }
                    }
                }
            }
        }
        val filter = IntentFilter(USB_PERMISSION).apply {
            addAction(BluetoothDevice.ACTION_BOND_STATE_CHANGED)
            addAction(UsbManager.ACTION_USB_DEVICE_ATTACHED)
        }
        ContextCompat.registerReceiver(ctx, receiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)

        // Reconnect on launch: last Bluetooth node, else a node already plugged in.
        val last = prefs().getString("ble", null)
        if (last != null && canUseBluetooth()) connectBle(last) else connectUsb(ask = false)
    }

    val appContext: Context get() = ctx

    private fun prefs() = ctx.getSharedPreferences("mesh", Context.MODE_PRIVATE)

    fun canUseBluetooth() =
        ctx.checkSelfPermission(android.Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED

    fun connectBle(address: String) {
        ctx.getSystemService(BluetoothManager::class.java).adapter?.let { connectBle(it.getRemoteDevice(address)) }
    }

    fun connectBle(device: BluetoothDevice) {
        disconnect()
        active = true
        transport = "Bluetooth"
        bleAddress = device.address
        prefs().edit { putString("ble", device.address) }
        if (device.bondState != BluetoothDevice.BOND_BONDED) {
            status = "Enter the PIN shown on the node's screen"
            device.createBond() // continues in the bond receiver above
            return
        }
        status = "Connecting to ${device.name ?: device.address}…"
        link = BleLink(ctx, device)
    }

    /** [ask] = show the system permission prompt if needed; false for silent auto-connect. */
    fun connectUsb(ask: Boolean = true) {
        val usb = ctx.getSystemService(UsbManager::class.java)
        val driver = UsbSerialProber.getDefaultProber().findAllDrivers(usb).firstOrNull()
        if (driver == null) {
            if (ask) status = "No node found. Check the cable is plugged in on both ends."
            return
        }
        if (!usb.hasPermission(driver.device)) {
            if (!ask) return
            val intent = Intent(USB_PERMISSION).setPackage(ctx.packageName)
            usb.requestPermission(driver.device, PendingIntent.getBroadcast(ctx, 0, intent, PendingIntent.FLAG_MUTABLE))
            return
        }
        closeLink()
        prefs().edit { remove("ble") }
        try {
            val port = driver.ports[0]
            port.open(usb.openDevice(driver.device))
            port.setParameters(115200, 8, UsbSerialPort.STOPBITS_1, UsbSerialPort.PARITY_NONE)
            // Both lines asserted = no reset on ESP32 auto-reset circuits, and native-USB boards see a host.
            port.dtr = true
            port.rts = true
            link = UsbLink(port)
            viaUsb = true
            bleAddress = null
            transport = "USB cable"
            active = true
            status = "Connecting over USB…"
            linkUp(link!!)
        } catch (e: Exception) {
            status = "USB error: ${e.message}"
        }
    }

    /** A node was plugged in (or came back after rebooting): pick it up if we're not busy with another link. */
    fun usbAttached() {
        if (link == null && (viaUsb || !active)) connectUsb(ask = false)
    }

    private fun closeLink() {
        main.removeCallbacks(heartbeat)
        link?.let { runCatching { it.close() } }
        link = null
    }

    fun disconnect() {
        closeLink()
        viaUsb = false
        active = false
        ready = false
        connected = false
        status = ""
        rooms.clear()
    }

    /** User pressed Disconnect: also forget the node so we don't auto-reconnect next launch. */
    fun forget() {
        prefs().edit { remove("ble") }
        disconnect()
    }

    fun forgetSaved(address: String) {
        saved.removeAll { it.first == address }
        saveSaved()
    }

    private fun saveSaved() = prefs().edit { putString("saved", JSONArray(saved.map { JSONArray(listOf(it.first, it.second)) }).toString()) }

    fun linkUp(from: Link) = main.post {
        if (from !== link) return@post
        status = "Syncing…"
        from.send(Pb().uint(3, Random.nextLong(1, 0xFFFFFFFF)).build()) // want_config_id: node replays its state + queued messages
        main.removeCallbacks(heartbeat)
        main.postDelayed(heartbeat, 60_000)
    }

    fun linkDown(from: Link, reason: String) = main.post {
        if (from !== link) return@post
        if (from is UsbLink) closeLink() // dead port; usbAttached() reopens it when the node is back
        connected = false
        status = reason
    }

    fun fromRadio(from: Link, bytes: ByteArray) = main.post {
        if (from !== link) return@post
        try {
            handle(Msg(bytes))
        } catch (e: Exception) {
            Log.w("Mesh", "bad FromRadio", e)
        }
    }

    private fun handle(fr: Msg) {
        fr.msg(3)?.let { myNum = it.long(1) }
        fr.msg(4)?.let { ni ->
            val num = ni.long(1)
            val old = nodes[num] ?: Node(num)
            nodes[num] = old.copy(
                lastHeard = maxOf(old.lastHeard, ni.long(5) * 1000),
                snr = if (ni.has(4)) Float.fromBits(ni.long(4).toInt()) else old.snr,
                hops = if (ni.has(9)) ni.long(9).toInt() else old.hops,
                favorite = ni.long(10) == 1L,
                battery = ni.msg(6)?.takeIf { it.has(1) }?.long(1)?.toInt() ?: old.battery,
            )
            ni.msg(2)?.let { user(num, it) }
        }
        fr.msg(5)?.bytes(6)?.let { lora = it; region = Msg(it).long(7).toInt() } // Config.lora.region
        fr.msg(10)?.let { c ->
            val index = c.long(1).toInt()
            val role = c.long(3)
            if (role == 0L) rooms.remove(index) else rooms[index] = room(index, c.bytes(2) ?: ByteArray(0), role == 1L)
        }
        if (fr.has(7)) {
            connected = true
            status = "Connected"
            if (!ready) askName = DEFAULT_NAME.matches(myLong) && !prefs().getBoolean("named", false)
            ready = true
            bleAddress?.let { address ->
                saved.removeAll { it.first == address }
                saved.add(0, address to myLong)
                saveSaved()
            }
        }
        fr.msg(2)?.let(::packet)
    }

    private fun room(index: Int, settings: ByteArray, primary: Boolean) =
        Msg(settings).let { Room(index, it.str(3), it.bytes(2) ?: ByteArray(0), primary, settings) }

    private fun user(num: Long, u: Msg) {
        nodes[num] = (nodes[num] ?: Node(num)).copy(long = u.str(2), short = u.str(3))
        if (num == myNum) { myLong = u.str(2); myShort = u.str(3) }
    }

    private fun packet(p: Msg) {
        val from = p.long(1)
        if (from != myNum && from != 0L) {
            val hopStart = p.long(15)
            val old = nodes[from] ?: Node(from)
            nodes[from] = old.copy(
                lastHeard = System.currentTimeMillis(),
                snr = if (p.has(8)) Float.fromBits(p.long(8).toInt()) else old.snr,
                hops = if (hopStart > 0) (hopStart - p.long(9)).toInt() else old.hops,
            )
        }
        val d = p.msg(4) ?: return // still encrypted = not on one of our channels
        val payload = d.bytes(2) ?: ByteArray(0)
        when (d.long(1)) {
            TEXT_APP -> {
                val id = p.long(6)
                if (id != 0L && messages.any { it.id == id && !it.mine }) return // node replays its queue on reconnect
                val rx = p.long(7) * 1000
                val time = if (rx > 1_600_000_000_000) rx else System.currentTimeMillis()
                val dm = p.long(2) == myNum && from != myNum
                add(Message(id, nodes[from]?.name ?: "!%08x".format(from), payload.decodeToString(), time,
                    mine = from == myNum, from = from, channel = if (dm) 0 else p.long(3).toInt(), peer = if (dm) from else 0))
            }
            NODEINFO_APP -> user(from, Msg(payload))
            TELEMETRY_APP -> Msg(payload).msg(2)?.takeIf { it.has(1) }?.let { m ->
                nodes[from]?.let { nodes[from] = it.copy(battery = m.long(1).toInt()) }
            }
            ROUTING_APP -> {
                val i = messages.indexOfFirst { it.mine && it.id == d.long(6) }
                if (i >= 0) {
                    // Broadcast: implicit ack = someone rebroadcast it. DM: real ack from the recipient. error_reason != 0 = gave up.
                    messages[i] = messages[i].copy(status = if (Msg(payload).long(3) == 0L) "✓" else "✗")
                    save()
                }
            }
        }
    }

    /** Channel message when [peer] is 0, else a DM (the firmware picks the channel and end-to-end encrypts it). */
    fun send(text: String, channel: Int, peer: Long) {
        val id = Random.nextLong(1, 0xFFFFFFFF)
        val data = Pb().uint(1, TEXT_APP).str(2, text)
        val packet = Pb().fixed32(2, if (peer != 0L) peer else BROADCAST).uint(3, channel.toLong())
            .msg(4, data).fixed32(6, id).uint(10, 1) // want_ack
        link?.send(Pb().msg(1, packet).build())
        add(Message(id, myLong, text, System.currentTimeMillis(), mine = true, status = "…", from = myNum, channel = channel, peer = peer))
        markRead(convoKey(channel, peer))
    }

    fun retry(m: Message) {
        messages.remove(m)
        send(m.text, m.channel, m.peer)
    }

    fun unread(convo: String): Int {
        val since = lastRead[convo] ?: 0
        return messages.count { !it.mine && it.convo == convo && it.time > since }
    }

    fun markRead(convo: String) {
        lastRead[convo] = System.currentTimeMillis()
        prefs().edit { putString("read", JSONObject(lastRead.toMap()).toString()) }
    }

    /** Sends an AdminMessage to our own node. */
    private fun admin(m: Pb) {
        val packet = Pb().fixed32(2, myNum).msg(4, Pb().uint(1, ADMIN_APP).msg(2, m)).fixed32(6, Random.nextLong(1, 0xFFFFFFFF))
        link?.send(Pb().msg(1, packet).build())
    }

    fun setOwner(long: String, short: String) {
        admin(Pb().msg(32, Pb().str(2, long).str(3, short))) // set_owner
        myLong = long; myShort = short
        status = "Saving, the node restarts to apply it…"
        nodes[myNum] = (nodes[myNum] ?: Node(myNum)).copy(long = long, short = short)
        nameAsked()
    }

    fun saveRegion(code: Int) {
        val config = lora ?: return
        // Appending a field overrides the earlier value (protobuf last-one-wins), keeping the rest of the LoRa config.
        admin(Pb().msg(34, Pb().bytes(6, config + Pb().uint(7, code.toLong()).build()))) // set_config
        region = code
        status = "Saving, the node restarts to apply it…"
    }

    fun setFavorite(num: Long, favorite: Boolean) {
        admin(Pb().uint(if (favorite) 39 else 40, num)) // set_favorite_node / remove_favorite_node, stored on the node
        nodes[num]?.let { nodes[num] = it.copy(favorite = favorite) }
    }

    private fun setRoom(index: Int, settings: ByteArray, role: Int) {
        admin(Pb().msg(33, Pb().uint(1, index.toLong()).bytes(2, settings).uint(3, role.toLong()))) // set_channel
        if (role == 0) rooms.remove(index) else rooms[index] = room(index, settings, role == 1)
    }

    private fun freeSlot() = (1..7).firstOrNull { it !in rooms }

    /** New private room with a random 256-bit key. Returns its index, or null when all 8 slots are used. */
    fun createRoom(name: String): Int? {
        val index = freeSlot() ?: return null
        val psk = ByteArray(32).also { java.security.SecureRandom().nextBytes(it) }
        setRoom(index, Pb().bytes(2, psk).str(3, name).build(), 2)
        return index
    }

    fun leaveRoom(index: Int) {
        if (index != 0) setRoom(index, ByteArray(0), 0)
    }

    /** Invite link in the format every Meshtastic app understands (meshtastic.org/e/#ChannelSet). */
    fun inviteLink(room: Room): String {
        val set = Pb().bytes(1, room.settings).also { p -> lora?.let { p.bytes(2, it) } }
        return INVITE + java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(set.build())
    }

    /** Adds the rooms from an invite link. Returns a message for the user and the first joined room, if any. */
    fun join(link: String): Pair<String, Int?> {
        val settings = inviteRooms(link)
        if (settings.isEmpty()) return "That doesn't look like a Meshtastic invite" to null
        var first: Int? = null
        for (s in settings) {
            val r = room(0, s, false)
            val existing = rooms.values.firstOrNull { it.name == r.name && it.psk.contentEquals(r.psk) }
            if (existing != null) { first = first ?: existing.index; continue }
            val index = freeSlot() ?: return "Your node already has 8 rooms. Leave one first." to first
            setRoom(index, s, 2)
            first = first ?: index
        }
        return "Joined" to first
    }

    fun nameAsked() {
        askName = false
        prefs().edit { putBoolean("named", true) }
    }

    private fun add(m: Message) {
        messages += m
        save()
    }

    // ponytail: rewrites the whole file per message; switch to append-only if history gets huge
    private fun save() {
        val a = JSONArray()
        messages.forEach {
            a.put(JSONObject().put("id", it.id).put("name", it.name).put("text", it.text).put("time", it.time)
                .put("mine", it.mine).put("status", it.status).put("from", it.from)
                .put("channel", it.channel).put("peer", it.peer))
        }
        file.writeText(a.toString())
    }
}

val SERVICE: UUID = UUID.fromString("6ba1b218-15a8-461f-9fa8-5dcae273eafd")
private val TO_RADIO = UUID.fromString("f75c76d2-129e-4dad-a1dd-7866124401e7")
private val FROM_RADIO = UUID.fromString("2c55e69e-4993-11ed-b878-0242ac120002")
private val FROM_NUM = UUID.fromString("ed9da18c-a800-4f66-a670-aa7547e34453")
private val CCCD = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")

/**
 * Meshtastic BLE API: write ToRadio, read FromRadio until empty, FromNum notifies when there's more.
 * autoConnect=true makes Android reconnect by itself whenever the node comes back in range.
 */
@SuppressLint("MissingPermission")
@Suppress("DEPRECATION", "OVERRIDE_DEPRECATION") // pre-Android 13 GATT APIs, used only below SDK 33
class BleLink(ctx: Context, device: BluetoothDevice) : BluetoothGattCallback(), Link {
    private val ops = ArrayDeque<(BluetoothGatt) -> Boolean>() // GATT allows one operation in flight
    private var busy = false
    private var readQueued = false
    private var toRadio: BluetoothGattCharacteristic? = null
    private var fromRadio: BluetoothGattCharacteristic? = null
    private val gatt = device.connectGatt(ctx, true, this, BluetoothDevice.TRANSPORT_LE)

    override fun send(toRadio: ByteArray) = enqueue { g -> this.toRadio?.let { write(g, it, toRadio) } ?: false }

    override fun close() {
        gatt.disconnect()
        gatt.close()
    }

    @Synchronized private fun enqueue(op: (BluetoothGatt) -> Boolean) {
        ops.addLast(op)
        if (!busy) next()
    }

    @Synchronized private fun next() {
        busy = false
        while (ops.isNotEmpty()) {
            if (ops.removeFirst()(gatt)) { busy = true; return }
        }
    }

    @Synchronized private fun readFromRadio() {
        if (readQueued) return
        readQueued = true
        enqueue { g -> fromRadio?.let { g.readCharacteristic(it) } ?: false }
    }

    private fun write(g: BluetoothGatt, c: BluetoothGattCharacteristic, v: ByteArray) =
        if (Build.VERSION.SDK_INT >= 33) {
            g.writeCharacteristic(c, v, BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT) == BluetoothStatusCodes.SUCCESS
        } else {
            c.writeType = BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
            c.value = v
            g.writeCharacteristic(c)
        }

    override fun onConnectionStateChange(g: BluetoothGatt, status: Int, state: Int) {
        if (state == BluetoothProfile.STATE_CONNECTED) {
            g.requestMtu(512)
        } else {
            synchronized(this) { ops.clear(); busy = false; readQueued = false }
            Mesh.linkDown(this, "Node out of reach, will reconnect automatically…")
        }
    }

    override fun onMtuChanged(g: BluetoothGatt, mtu: Int, status: Int) {
        g.discoverServices()
    }

    override fun onServicesDiscovered(g: BluetoothGatt, status: Int) {
        val s = g.getService(SERVICE)
        if (s == null) { Mesh.linkDown(this, "Not a Meshtastic device"); return }
        toRadio = s.getCharacteristic(TO_RADIO)
        fromRadio = s.getCharacteristic(FROM_RADIO)
        val num = s.getCharacteristic(FROM_NUM)
        g.setCharacteristicNotification(num, true)
        enqueue { gg ->
            val d = num.getDescriptor(CCCD)
            val on = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
            if (Build.VERSION.SDK_INT >= 33) gg.writeDescriptor(d, on) == BluetoothStatusCodes.SUCCESS
            else { d.value = on; gg.writeDescriptor(d) }
        }
        Mesh.linkUp(this)
    }

    override fun onDescriptorWrite(g: BluetoothGatt, d: BluetoothGattDescriptor, status: Int) = next()

    override fun onCharacteristicWrite(g: BluetoothGatt, c: BluetoothGattCharacteristic, status: Int) {
        readFromRadio() // a write usually means the node has something for us (e.g. the config dump)
        next()
    }

    override fun onCharacteristicRead(g: BluetoothGatt, c: BluetoothGattCharacteristic, value: ByteArray, status: Int) {
        synchronized(this) { readQueued = false }
        if (status == BluetoothGatt.GATT_SUCCESS && value.isNotEmpty()) {
            Mesh.fromRadio(this, value)
            readFromRadio() // drain until empty
        }
        next()
    }

    override fun onCharacteristicRead(g: BluetoothGatt, c: BluetoothGattCharacteristic, status: Int) {
        if (Build.VERSION.SDK_INT < 33) onCharacteristicRead(g, c, c.value ?: ByteArray(0), status)
    }

    override fun onCharacteristicChanged(g: BluetoothGatt, c: BluetoothGattCharacteristic, value: ByteArray) = readFromRadio()

    override fun onCharacteristicChanged(g: BluetoothGatt, c: BluetoothGattCharacteristic) {
        if (Build.VERSION.SDK_INT < 33) readFromRadio()
    }
}

/** Meshtastic serial API over USB (CP210x on Heltec V3, native USB CDC on newer boards). */
class UsbLink(private val port: UsbSerialPort) : Link, SerialInputOutputManager.Listener {
    private val deframer = Deframer { Mesh.fromRadio(this, it) }
    private val io = SerialInputOutputManager(port, this)

    init {
        port.write(ByteArray(32) { 0xC3.toByte() }, 1000) // wake the serial API before the first frame
        io.start()
    }

    override fun send(toRadio: ByteArray) {
        try {
            port.write(frame(toRadio), 1000)
        } catch (e: Exception) {
            Mesh.linkDown(this, "USB error: ${e.message}")
        }
    }

    override fun close() {
        io.stop()
        port.close()
    }

    override fun onNewData(data: ByteArray) = deframer.feed(data)
    override fun onRunError(e: Exception) { Mesh.linkDown(this, "Node unplugged or restarting…") }
}
