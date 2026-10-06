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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.core.content.IntentCompat
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
    val mine: Boolean, val dm: Boolean = false, val status: String = "",
)

interface Link {
    fun send(toRadio: ByteArray)
    fun close()
}

const val BROADCAST = 0xFFFFFFFFL
private const val TEXT_APP = 1L
private const val NODEINFO_APP = 4L
private const val ROUTING_APP = 5L
private const val ADMIN_APP = 6L
private const val USB_PERMISSION = "com.backmeupplz.meshtasticplus.USB_PERMISSION"

/** The whole app state. Lives as long as the process, so rotation/backgrounding keeps the connection. */
@SuppressLint("MissingPermission", "StaticFieldLeak") // permissions are requested in MainActivity; ctx is the app context
object Mesh {
    val messages = mutableStateListOf<Message>()
    var status by mutableStateOf("Not connected")
    var connected by mutableStateOf(false) // config handshake finished, ready to chat
    var active by mutableStateOf(false) // pairing, connecting or connected
    var myLong by mutableStateOf("")
    var myShort by mutableStateOf("")

    private lateinit var ctx: Context
    private lateinit var file: File
    private val main = Handler(Looper.getMainLooper())
    private var link: Link? = null
    private var myNum = 0L
    private val names = HashMap<Long, String>()

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
                    getBoolean("mine"), optBoolean("dm"), optString("status"))
            }
        }
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(c: Context, i: Intent) {
                when (i.action) {
                    USB_PERMISSION -> if (i.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)) connectUsb()
                    else status = "USB permission denied"
                    BluetoothDevice.ACTION_BOND_STATE_CHANGED -> {
                        val d = IntentCompat.getParcelableExtra(i, BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
                        if (d == null || d.address != prefs().getString("ble", null)) return
                        when (i.getIntExtra(BluetoothDevice.EXTRA_BOND_STATE, 0)) {
                            BluetoothDevice.BOND_BONDED -> if (link == null) connectBle(d)
                            BluetoothDevice.BOND_NONE -> if (link == null) status = "Pairing failed, try again"
                        }
                    }
                }
            }
        }
        val filter = IntentFilter(USB_PERMISSION).apply { addAction(BluetoothDevice.ACTION_BOND_STATE_CHANGED) }
        ContextCompat.registerReceiver(ctx, receiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)

        // Reconnect to the last Bluetooth node on launch.
        val last = prefs().getString("ble", null)
        if (last != null && ctx.checkSelfPermission(android.Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED) {
            ctx.getSystemService(BluetoothManager::class.java).adapter?.let { connectBle(it.getRemoteDevice(last)) }
        }
    }

    private fun prefs() = ctx.getSharedPreferences("mesh", Context.MODE_PRIVATE)

    fun connectBle(device: BluetoothDevice) {
        disconnect()
        active = true
        prefs().edit().putString("ble", device.address).apply()
        if (device.bondState != BluetoothDevice.BOND_BONDED) {
            status = "Pairing: enter the PIN shown on the node's screen"
            device.createBond() // continues in the bond receiver above
            return
        }
        status = "Connecting to ${device.name ?: device.address}…"
        link = BleLink(ctx, device)
    }

    fun connectUsb() {
        val usb = ctx.getSystemService(UsbManager::class.java)
        val driver = UsbSerialProber.getDefaultProber().findAllDrivers(usb).firstOrNull()
            ?: run { status = "No USB serial device found"; return }
        if (!usb.hasPermission(driver.device)) {
            val intent = Intent(USB_PERMISSION).setPackage(ctx.packageName)
            usb.requestPermission(driver.device, PendingIntent.getBroadcast(ctx, 0, intent, PendingIntent.FLAG_MUTABLE))
            return
        }
        disconnect()
        prefs().edit().remove("ble").apply()
        try {
            val port = driver.ports[0]
            port.open(usb.openDevice(driver.device))
            port.setParameters(115200, 8, UsbSerialPort.STOPBITS_1, UsbSerialPort.PARITY_NONE)
            // Both lines asserted = no reset on ESP32 auto-reset circuits, and native-USB boards see a host.
            port.dtr = true
            port.rts = true
            link = UsbLink(port)
            active = true
            linkUp(link!!)
        } catch (e: Exception) {
            status = "USB error: ${e.message}"
        }
    }

    fun disconnect() {
        main.removeCallbacks(heartbeat)
        link?.let { runCatching { it.close() } }
        link = null
        active = false
        connected = false
        status = "Not connected"
    }

    /** User pressed Disconnect: also forget the node so we don't auto-reconnect next launch. */
    fun forget() {
        prefs().edit().remove("ble").apply()
        disconnect()
    }

    fun linkUp(from: Link) = main.post {
        if (from !== link) return@post
        status = "Syncing…"
        from.send(Pb().uint(3, Random.nextLong(1, 0xFFFFFFFF)).build()) // want_config_id: node replays its state + queued messages
        main.removeCallbacks(heartbeat)
        main.postDelayed(heartbeat, 60_000)
    }

    fun linkDown(from: Link, reason: String) = main.post {
        if (from !== link) return@post
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
        fr.msg(4)?.let { ni -> ni.msg(2)?.let { user(ni.long(1), it) } }
        if (fr.has(7)) { connected = true; status = "Connected" }
        fr.msg(2)?.let(::packet)
    }

    private fun user(num: Long, u: Msg) {
        names[num] = u.str(2)
        if (num == myNum) { myLong = u.str(2); myShort = u.str(3) }
    }

    private fun packet(p: Msg) {
        val d = p.msg(4) ?: return // still encrypted = not on one of our channels
        val from = p.long(1)
        val payload = d.bytes(2) ?: ByteArray(0)
        when (d.long(1)) {
            TEXT_APP -> {
                val id = p.long(6)
                if (id != 0L && messages.any { it.id == id && !it.mine }) return // node replays its queue on reconnect
                val rx = p.long(7) * 1000
                val time = if (rx > 1_600_000_000_000) rx else System.currentTimeMillis()
                val name = names[from]?.takeIf { it.isNotEmpty() } ?: "!%08x".format(from)
                add(Message(id, name, payload.decodeToString(), time, mine = from == myNum, dm = p.long(2) == myNum))
            }
            NODEINFO_APP -> user(from, Msg(payload))
            ROUTING_APP -> {
                val i = messages.indexOfFirst { it.mine && it.id == d.long(6) }
                if (i >= 0) {
                    // Implicit ack = another node rebroadcast it. error_reason (3) != 0 = gave up.
                    messages[i] = messages[i].copy(status = if (Msg(payload).long(3) == 0L) "✓" else "✗")
                    save()
                }
            }
        }
    }

    fun send(text: String) {
        val id = Random.nextLong(1, 0xFFFFFFFF)
        val data = Pb().uint(1, TEXT_APP).str(2, text)
        val packet = Pb().fixed32(2, BROADCAST).msg(4, data).fixed32(6, id).uint(10, 1) // want_ack
        link?.send(Pb().msg(1, packet).build())
        add(Message(id, myLong, text, System.currentTimeMillis(), mine = true, status = "…"))
    }

    fun setOwner(long: String, short: String) {
        val admin = Pb().msg(32, Pb().str(2, long).str(3, short)) // AdminMessage.set_owner
        val packet = Pb().fixed32(2, myNum).msg(4, Pb().uint(1, ADMIN_APP).msg(2, admin)).fixed32(6, Random.nextLong(1, 0xFFFFFFFF))
        link?.send(Pb().msg(1, packet).build())
        myLong = long; myShort = short; names[myNum] = long
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
                .put("mine", it.mine).put("dm", it.dm).put("status", it.status))
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
    override fun onRunError(e: Exception) { Mesh.linkDown(this, "USB disconnected") }
}
