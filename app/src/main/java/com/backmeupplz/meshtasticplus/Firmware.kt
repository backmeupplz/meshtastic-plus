package com.backmeupplz.meshtasticplus

import android.app.PendingIntent
import android.content.Intent
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.hoho.android.usbserial.driver.UsbSerialPort
import com.hoho.android.usbserial.driver.UsbSerialProber
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.net.URL
import java.util.zip.ZipInputStream

/** A board from Meshtastic's hardware list (res/raw/hardware.json, from api.meshtastic.org/resource/deviceHardware). */
data class Board(val hwModel: Int, val target: String, val arch: String, val name: String) {
    val chip get() = when {
        arch.startsWith("nrf52") -> "nRF52840"
        arch == "portduino" -> "Linux"
        else -> arch.uppercase()
    }
    val canUpdateOverUsb get() = arch.startsWith("nrf52")
}

// ponytail: bundled snapshot of the hardware list; refresh res/raw/hardware.json when Meshtastic adds boards
val boards: List<Board> by lazy {
    val a = JSONArray(Mesh.appContext.resources.openRawResource(R.raw.hardware).bufferedReader().readText())
    List(a.length()) { a.getJSONArray(it).run { Board(getInt(0), getString(1), getString(2), getString(3)) } }
}

/** The connected node's board: exact build target when the firmware reports it, else the first board with its model. */
fun currentBoard(): Board? =
    boards.firstOrNull { it.target == Mesh.pioEnv } ?: boards.firstOrNull { it.hwModel == Mesh.hwModel && Mesh.hwModel != 0 }

/** True when [latest] is a higher x.y.z than [installed] (the commit-hash suffix is ignored). */
fun isNewer(latest: String, installed: String): Boolean {
    fun parts(v: String) = v.removePrefix("v").split('.').take(3).map { it.toIntOrNull() ?: 0 } + listOf(0, 0, 0)
    val (a, b) = parts(latest) to parts(installed)
    return (0..2).firstOrNull { a[it] != b[it] }?.let { a[it] > b[it] } ?: false
}

/** Firmware update over USB for nRF52 nodes: download the release's DFU package, reboot into the bootloader, flash. */
object Updater {
    var step by mutableStateOf("")
    var progress by mutableFloatStateOf(-1f) // -1 = indeterminate
    var error by mutableStateOf("")
    var running by mutableStateOf(false)
    var done by mutableStateOf(false)
    var latest by mutableStateOf("") // newest stable version, e.g. 2.7.26.54e0d8d
    var inBootloader = false // we already rebooted the node into update mode (a retry must not ask the firmware again)

    private fun ui(f: () -> Unit) = android.os.Handler(android.os.Looper.getMainLooper()).post(f)

    fun checkLatest() = Thread {
        runCatching {
            val list = JSONObject(URL("https://api.meshtastic.org/github/firmware/list").readText())
            val id = list.getJSONObject("releases").getJSONArray("stable").getJSONObject(0).getString("id")
            ui { latest = id.removePrefix("v") }
        }.onFailure { ui { error = "Couldn't check for updates. Is the phone online?" } }
    }.start()

    /** [rescue] = the node is already in update mode (stuck after an interrupted update, or put there by hand). */
    fun start(board: Board, version: String, rescue: Boolean) {
        running = true; done = false; error = ""; progress = -1f
        val enterBootloader = !rescue && !inBootloader
        val usb = Mesh.appContext.getSystemService(UsbManager::class.java)
        val before = if (enterBootloader) usb.deviceList.keys.toSet() else emptySet()
        Thread {
            try {
                ui { step = "Downloading firmware $version…" }
                val (dat, bin) = download(board.target, version)
                if (enterBootloader) {
                    ui { step = "Restarting the node into update mode…"; Mesh.enterUpdateMode() }
                    inBootloader = true
                } else {
                    ui { Mesh.pauseForUpdate() }
                }
                val device = waitForBootloader(usb, before)
                ui { step = "Waiting for USB permission…" }
                askPermission(usb, device)
                val driver = UsbSerialProber.getDefaultProber().probeDevice(device) ?: error("The node isn't in update mode")
                val port = driver.ports[0]
                port.open(usb.openDevice(device) ?: error("Couldn't open the node"))
                try {
                    port.setParameters(115200, 8, UsbSerialPort.STOPBITS_1, UsbSerialPort.PARITY_NONE)
                    port.dtr = true
                    Dfu(port).flash(dat, bin) { s, p -> ui { step = s; progress = p } }
                } finally {
                    runCatching { port.close() }
                }
                inBootloader = false
                ui { step = "Done! The node is restarting with $version."; progress = 1f; done = true; running = false; Mesh.resumeAfterUpdate() }
            } catch (e: Exception) {
                // Still in the bootloader: keep the port to ourselves so "Try again" can pick it up.
                ui { error = e.message ?: e.toString(); running = false; if (!inBootloader && Mesh.updating) Mesh.resumeAfterUpdate() }
            }
        }.start()
    }

    /** The release's legacy-DFU package for [target]: init packet (.dat) and application image (.bin). */
    private fun download(target: String, version: String): Pair<ByteArray, ByteArray> {
        val url = "https://raw.githubusercontent.com/meshtastic/meshtastic.github.io/master/firmware-$version/firmware-$target-$version-ota.zip"
        val files = HashMap<String, ByteArray>()
        try {
            ZipInputStream(URL(url).openStream()).use { zip ->
                while (true) { val e = zip.nextEntry ?: break; files[e.name] = zip.readBytes() }
            }
        } catch (e: java.io.FileNotFoundException) {
            error("Meshtastic has no USB update package for this board in $version")
        }
        val app = JSONObject(files["manifest.json"]!!.decodeToString()).getJSONObject("manifest").getJSONObject("application")
        return files[app.getString("dat_file")]!! to files[app.getString("bin_file")]!!
    }

    /** After the reboot the node comes back as a new USB device (the bootloader). */
    private fun waitForBootloader(usb: UsbManager, before: Set<String>): UsbDevice {
        ui { step = "Waiting for the node to restart…" }
        Thread.sleep(1500)
        repeat(40) {
            val fresh = usb.deviceList.values.filter { it.deviceName !in before && UsbSerialProber.getDefaultProber().probeDevice(it) != null }
            fresh.firstOrNull()?.let { return it }
            Thread.sleep(500)
        }
        error("The node didn't come back in update mode. Unplug it, double-press its reset button, plug it in and try again.")
    }

    private fun askPermission(usb: UsbManager, device: UsbDevice) {
        if (usb.hasPermission(device)) return
        val intent = Intent("com.backmeupplz.meshtasticplus.USB_PERMISSION_DFU").setPackage(Mesh.appContext.packageName)
        usb.requestPermission(device, PendingIntent.getBroadcast(Mesh.appContext, 1, intent, PendingIntent.FLAG_MUTABLE))
        repeat(120) { if (usb.hasPermission(device)) return; Thread.sleep(500) }
        error("USB permission wasn't granted")
    }
}

/**
 * Nordic legacy serial DFU (SDK 11, HCI + SLIP) as spoken by the Adafruit nRF52 bootloader every Meshtastic nRF52 board
 * uses. A port of adafruit-nrfutil's dfu_transport_serial.py, including its timings.
 */
class Dfu(private val port: UsbSerialPort) {
    private var seq = 0

    fun flash(dat: ByteArray, bin: ByteArray, progress: (String, Float) -> Unit) {
        progress("Erasing the old firmware…", -1f)
        send(int32(3) + int32(4) + int32(0) + int32(0) + int32(bin.size)) // start: application mode, sizes sd/bl/app
        Thread.sleep(eraseTime(bin.size))
        send(int32(1) + dat + byteArrayOf(0, 0)) // init packet, zero-padded
        val chunks = (bin.indices step 512).map { bin.copyOfRange(it, minOf(it + 512, bin.size)) }
        chunks.forEachIndexed { i, c ->
            send(int32(4) + c)
            if (i % 8 == 0) Thread.sleep(PAGE_WRITE_MS)
            progress("Installing… ${100 * (i + 1) / chunks.size}%", (i + 1f) / chunks.size)
        }
        Thread.sleep(PAGE_WRITE_MS)
        send(int32(5)) // stop: bootloader validates and activates
        progress("Finishing…", 1f)
        Thread.sleep(eraseTime(bin.size) + ((bin.size / 4096) + 1) * PAGE_WRITE_MS)
    }

    private fun eraseTime(size: Int) = maxOf(500L, ((size / 4096) + 1) * 90L) // 89.7ms per 4K page

    /** One reliable HCI packet, then wait for the bootloader's ack frame. */
    private fun send(payload: ByteArray) {
        seq = (seq + 1) % 8
        port.write(packet(seq, payload), 5000)
        val buf = ByteArray(64)
        var frameMarks = 0
        val deadline = System.currentTimeMillis() + 1000
        while (frameMarks < 2) {
            if (System.currentTimeMillis() > deadline) error("The node stopped responding during the update. Try again.")
            val n = port.read(buf, 200)
            for (k in 0 until n) if (buf[k] == 0xC0.toByte()) frameMarks++
        }
    }

    companion object {
        const val PAGE_WRITE_MS = 103L // 1024 words × 100µs

    /** HCI packet [seq] (1..7, then wraps), SLIP-framed. */
    fun packet(seq: Int, payload: ByteArray): ByteArray {
        val len = payload.size
        val h0 = seq or (((seq + 1) % 8) shl 3) or (1 shl 6) or (1 shl 7) // seq, ack, data integrity, reliable
        val h1 = 14 or ((len and 0xF) shl 4) // HCI packet type 14
        val h2 = (len and 0xFF0) shr 4
        val h3 = (-(h0 + h1 + h2)) and 0xFF
        val raw = byteArrayOf(h0.toByte(), h1.toByte(), h2.toByte(), h3.toByte()) + payload
        val crc = crc16(raw)
        val body = raw + byteArrayOf(crc.toByte(), (crc shr 8).toByte())
        val out = ByteArrayOutputStream()
        out.write(0xC0)
        for (b in body) when (b.toInt() and 0xFF) { // SLIP escaping
            0xC0 -> { out.write(0xDB); out.write(0xDC) }
            0xDB -> { out.write(0xDB); out.write(0xDD) }
            else -> out.write(b.toInt())
        }
        out.write(0xC0)
        return out.toByteArray()
    }

        fun int32(v: Int) = byteArrayOf(v.toByte(), (v shr 8).toByte(), (v shr 16).toByte(), (v shr 24).toByte())

        /** CRC-16/CCITT-FALSE, as in nrfutil's crc16.py. */
        fun crc16(data: ByteArray): Int {
            var crc = 0xFFFF
            for (b in data) {
                crc = ((crc ushr 8) and 0xFF) or ((crc shl 8) and 0xFF00)
                crc = crc xor (b.toInt() and 0xFF)
                crc = crc xor ((crc and 0xFF) ushr 4)
                crc = crc xor ((crc shl 12) and 0xFFFF)
                crc = crc xor (((crc and 0xFF) shl 5) and 0xFFFF)
            }
            return crc
        }
    }
}
