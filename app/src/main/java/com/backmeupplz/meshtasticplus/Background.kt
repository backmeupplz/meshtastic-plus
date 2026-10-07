package com.backmeupplz.meshtasticplus

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.provider.Telephony
import android.telephony.SmsManager
import android.telephony.TelephonyManager
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import androidx.core.content.edit

/** Keeps the process, and with it the node connection, alive while the app is in the background. */
class MeshService : Service() {
    override fun onBind(intent: Intent?) = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Mesh.init(this) // restarted by the system after the process was killed: reconnect
        running = true
        ServiceCompat.startForeground(this, ID, notification(this), ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE)
        return START_STICKY
    }

    override fun onDestroy() {
        running = false
        super.onDestroy()
    }

    companion object {
        private const val ID = 1
        private var running = false

        fun start(ctx: Context) {
            // Android refuses foreground services started from the background; the service then simply isn't running.
            runCatching { ContextCompat.startForegroundService(ctx, Intent(ctx, MeshService::class.java)) }
        }

        fun stop(ctx: Context) = ctx.stopService(Intent(ctx, MeshService::class.java))

        /** Status changed: update the persistent notification. */
        fun refresh(ctx: Context) {
            if (running) ctx.getSystemService(NotificationManager::class.java).notify(ID, notification(ctx))
        }

        private fun notification(ctx: Context): Notification {
            Notify.channels(ctx)
            val node = Mesh.myLong.ifEmpty { "your node" }
            val text = when {
                Relay.enabled -> "Relaying SMS and calls to ${Relay.room} via $node"
                Mesh.connected -> "Connected to $node"
                else -> Mesh.status.ifEmpty { "Not connected" }
            }
            return NotificationCompat.Builder(ctx, Notify.STATUS)
                .setSmallIcon(android.R.drawable.stat_sys_data_bluetooth)
                .setContentTitle("Mesh+")
                .setContentText(text)
                .setOngoing(true)
                .setContentIntent(PendingIntent.getActivity(ctx, 0, Intent(ctx, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE))
                .build()
        }
    }
}

object Notify {
    const val MESSAGES = "messages"
    const val STATUS = "status"

    fun channels(ctx: Context) {
        val nm = ctx.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(MESSAGES, "Messages", NotificationManager.IMPORTANCE_HIGH))
        nm.createNotificationChannel(NotificationChannel(STATUS, "Connection", NotificationManager.IMPORTANCE_LOW))
    }

    /** A message arrived: notify unless its conversation is on screen. One notification per conversation, newest wins. */
    fun message(m: Message) {
        val ctx = Mesh.appContext
        if (Mesh.visible && Mesh.openConvo == m.convo) return
        if (Mesh.isMuted(m.convo, m.me)) return
        if (ctx.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return
        channels(ctx)
        val dm = m.peer != 0L
        val open = Intent(ctx, MainActivity::class.java).putExtra("convo", m.convo).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
        val n = NotificationCompat.Builder(ctx, MESSAGES)
            .setSmallIcon(android.R.drawable.stat_notify_chat)
            .setContentTitle(if (dm) m.name else Mesh.rooms[m.channel]?.title ?: "Room")
            .setContentText(if (dm) m.text else "${m.name}: ${m.text}")
            .setStyle(NotificationCompat.BigTextStyle().bigText(if (dm) m.text else "${m.name}: ${m.text}"))
            .setAutoCancel(true)
            .setContentIntent(PendingIntent.getActivity(ctx, m.convo.hashCode(), open, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT))
            .build()
        ctx.getSystemService(NotificationManager::class.java).notify(m.convo.hashCode(), n)
    }

    fun clear(convo: String) = Mesh.appContext.getSystemService(NotificationManager::class.java).cancel(convo.hashCode())
}

/** Mesh text messages are capped at 200 bytes: longer text goes out as "(1/3) …" parts. */
fun splitForMesh(text: String, max: Int = 200): List<String> {
    if (text.toByteArray().size <= max) return listOf(text)
    val parts = mutableListOf<String>()
    var current = StringBuilder()
    for (c in text) { // "(99/99) " = 8 bytes of prefix
        if ((current.toString() + c).toByteArray().size > max - 8) { parts += current.toString(); current = StringBuilder() }
        current.append(c)
    }
    if (current.isNotEmpty()) parts += current.toString()
    return parts.mapIndexed { i, p -> "(${i + 1}/${parts.size}) $p" }
}

/** Starts the text a client wants the relay phone to send as SMS: "/sms +15551234567 hello". */
const val SMS_COMMAND = "/sms"
private val SMS_COMMAND_RE = Regex("""^/sms\s+(\+?[0-9()-]{3,20})\s+(.+)$""", RegexOption.DOT_MATCHES_ALL)

/** What the relay posts; clients use these to spot a relay room. */
val RELAY_PREFIXES = listOf("SMS from ", "Incoming call from ", "Missed call from ", "Relay on")

/**
 * SMS & call relay: this phone posts its incoming SMS and calls to a private room, and sends SMS that room's members
 * ask for. Anyone holding the room's key can do both, which the relay screen says plainly.
 */
object Relay {
    private val prefs get() = Mesh.appContext.getSharedPreferences("mesh", 0)
    var enabled by mutableStateOf(prefs.getBoolean("relay", false))
        private set
    var room by mutableStateOf(prefs.getString("relayRoom", "").orEmpty()) // room name on this phone's node
        private set
    private val outbox = ArrayDeque<String>() // waits for the node (e.g. the process was just started by an SMS)
    private var ringing: String? = null
    private var answered = false

    fun channel() = Mesh.rooms.values.firstOrNull { it.name == room && !it.public }?.index

    fun useRoom(name: String) {
        room = name
        prefs.edit { putString("relayRoom", name) }
    }

    fun turn(on: Boolean) {
        if (!on) post("Relay off: this phone stopped relaying SMS and calls.")
        enabled = on
        prefs.edit { putBoolean("relay", on) }
        if (on) post("Relay on: SMS and calls to this phone will show up here. To text someone, send $SMS_COMMAND +15551234567 your message")
        MeshService.refresh(Mesh.appContext)
    }

    /** Queues [text] for the relay room; sent right away when the node is connected. */
    fun post(text: String) {
        if (!enabled) return
        splitForMesh(text).forEach(outbox::addLast)
        flush()
    }

    fun flush() {
        val ch = channel() ?: return
        if (!Mesh.connected) return
        while (outbox.isNotEmpty()) Mesh.send(outbox.removeFirst(), ch, 0)
    }

    /** A room message arrived: if it's "/sms <number> <text>" in the relay room, send that SMS. */
    fun command(m: Message) {
        if (!enabled || m.mine || m.peer != 0L || m.channel != channel()) return
        val match = SMS_COMMAND_RE.find(m.text.trim()) ?: return
        val number = match.groupValues[1].filter { it.isDigit() || it == '+' }
        runCatching {
            val sms = Mesh.appContext.getSystemService(SmsManager::class.java)
            sms.sendMultipartTextMessage(number, null, sms.divideMessage(match.groupValues[2]), null, null)
        }.onSuccess { post("SMS to $number sent (asked by ${m.name})") }
            .onFailure { post("SMS to $number failed: ${it.message}") }
    }

    fun sms(from: String, body: String) = post("SMS from $from: $body")

    fun call(state: String?, number: String) {
        when (state) {
            TelephonyManager.EXTRA_STATE_RINGING -> { ringing = number; answered = false; post("Incoming call from $number") }
            TelephonyManager.EXTRA_STATE_OFFHOOK -> if (ringing == number) answered = true
            TelephonyManager.EXTRA_STATE_IDLE -> if (ringing == number) {
                post(if (answered) "Call from $number ended" else "Missed call from $number")
                ringing = null
            }
        }
    }
}

class SmsReceiver : BroadcastReceiver() {
    override fun onReceive(c: Context, i: Intent) {
        Mesh.init(c)
        if (!Relay.enabled) return
        Telephony.Sms.Intents.getMessagesFromIntent(i).groupBy { it.originatingAddress ?: "unknown" }
            .forEach { (from, parts) -> Relay.sms(from, parts.joinToString("") { it.messageBody.orEmpty() }) }
    }
}

class CallReceiver : BroadcastReceiver() {
    override fun onReceive(c: Context, i: Intent) {
        Mesh.init(c)
        if (!Relay.enabled) return
        // Delivered twice per change; only the copy carrying the number (needs READ_CALL_LOG) counts.
        @Suppress("DEPRECATION") val number = i.getStringExtra(TelephonyManager.EXTRA_INCOMING_NUMBER) ?: return
        Relay.call(i.getStringExtra(TelephonyManager.EXTRA_STATE), number)
    }
}
