package com.backmeupplz.meshtasticplus

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Memory
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material.icons.rounded.SystemUpdate
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp

/**
 * One editable field of the node's Config. [section] is the Config oneof (1 device, 2 position, 3 power, 4 network,
 * 5 display, 6 LoRa, 7 Bluetooth), [field] the field inside it (config.proto).
 * Kind: [options] = pick one, [toggle] = switch ([invert] when the field is a "disabled" flag), [pin] = 6 digits, [text] = string.
 */
class Setting(
    val section: Int, val field: Int, val title: String, val info: String,
    val options: List<Pair<Long, String>>? = null, val toggle: Boolean = false, val invert: Boolean = false,
    val pin: Boolean = false, val text: Boolean = false, val secret: Boolean = false,
    val also: Pair<Int, Long>? = null, // another field of the same section to set along with this one
    val visible: (get: (Int, Int) -> Long) -> Boolean = { true },
) {
    val key get() = "$section/$field"
}

class Group(val title: String, val settings: List<Setting>)

private fun duration(s: Long): String = when {
    s == 0xFFFFFFFFL -> "Always"
    s % 86400 == 0L -> if (s == 86400L) "1 day" else "${s / 86400} days"
    s % 3600 == 0L -> if (s == 3600L) "1 hour" else "${s / 3600} hours"
    s % 60 == 0L -> if (s == 60L) "1 minute" else "${s / 60} minutes"
    else -> "$s seconds"
}

/** Interval choices; 0 means "firmware default" and is labeled with what that default is. */
private fun every(default: String, vararg secs: Long) = listOf(0L to "Default ($default)") + secs.map { it to duration(it) }

private const val BT_WARNING = "\n\nIf you're connected over Bluetooth, changing this can drop the connection; a USB cable always works."

val SETTINGS = listOf(
    Group("Role", listOf(
        Setting(1, 1, "Role",
            "What job this node does on the mesh.\n\n" +
                "• Client: the normal choice for a node you carry and chat from. It relays other people's messages too.\n" +
                "• Client (mute): doesn't relay. Use it when another node of yours nearby already does.\n" +
                "• Client (hidden): stays quiet and only talks when needed, for privacy or battery.\n" +
                "• Client base: a home node that relays eagerly for your favorites.\n" +
                "• Router / Router (late): for nodes in high, well-placed spots that exist to extend the mesh. Late waits for others to relay first.\n" +
                "• Tracker / Sensor: sends its position or sensor readings first.\n" +
                "• Lost and found: keeps announcing its location so you can find it.\n\n" +
                "Router roles may turn on power saving, which switches Bluetooth off most of the time.",
            options = listOf(0L to "Client", 1L to "Client (mute)", 8L to "Client (hidden)", 12L to "Client base",
                2L to "Router", 11L to "Router (late)", 5L to "Tracker", 6L to "Sensor", 9L to "Lost and found",
                7L to "TAK", 10L to "TAK tracker")),
        Setting(1, 6, "Relaying",
            "Which messages this node passes along for others.\n\n" +
                "• Everything: relays all it hears. Best for the mesh, and the default.\n" +
                "• Everything, without reading: same, without trying to decrypt. For dedicated repeaters.\n" +
                "• Local rooms only: ignores traffic from other meshes it can't read.\n" +
                "• Known nodes only: ignores nodes that aren't in its node list.\n" +
                "• Standard messages only: skips unusual app traffic.\n" +
                "• Nothing: never relays. Only allowed for Tracker and Sensor roles.",
            options = listOf(0L to "Everything", 1L to "Everything, without reading", 2L to "Local rooms only",
                3L to "Known nodes only", 5L to "Standard messages only", 4L to "Nothing")),
        Setting(1, 7, "Announce name every",
            "How often the node tells everyone its name and encryption key. Others need this to show your name and to send you direct messages. More often adds radio traffic for everyone; the default suits most people.",
            options = every("3 hours", 3600, 21600, 43200, 86400)),
        Setting(1, 12, "Heartbeat LED",
            "Blinks the node's LED every few seconds to show it's running. Turn off to keep it dark or save a little battery.",
            toggle = true, invert = true),
    )),
    Group("Radio", listOf(
        Setting(6, 7, "Region",
            "The radio band. It has to match your country's rules, and nodes on different regions can't hear each other.",
            options = REGIONS.map { it.first.toLong() to it.second }),
        Setting(6, 2, "Speed vs. range",
            "The radio preset: slower presets reach farther, faster ones carry more messages. Every node has to use the same preset to hear each other, so only change this if your local mesh uses a different one. Almost everyone uses Long range, fast.",
            options = listOf(0L to "Long range, fast", 7L to "Long range, moderate", 9L to "Long range, turbo",
                3L to "Medium range, slow", 4L to "Medium range, fast", 5L to "Short range, slow",
                6L to "Short range, fast", 8L to "Short range, turbo"),
            also = 1 to 1L), // use_preset
        Setting(6, 8, "Max hops",
            "How many times a message may be passed along on its way. 3 is the default and plenty for most meshes; higher values add traffic for everyone.",
            options = listOf(0L to "Default (3)") + (1L..7L).map { it to "$it" }),
        Setting(6, 10, "Transmit power",
            "How strongly the node transmits. Maximum uses the strongest power that's legal in your region and that the board supports. Lower saves battery but shortens range.",
            options = listOf(0L to "Maximum allowed", 10L to "10 dBm", 14L to "14 dBm", 17L to "17 dBm", 20L to "20 dBm",
                22L to "22 dBm", 27L to "27 dBm", 30L to "30 dBm")),
        Setting(6, 9, "Transmit",
            "Lets the radio send. Off makes the node listen only: nothing you send will go out.", toggle = true),
        Setting(6, 13, "Boosted receive",
            "Makes the receiver a bit more sensitive on SX126x radios (most modern boards), for slightly more power use.",
            toggle = true),
        Setting(6, 105, "OK to share via internet",
            "Lets gateways that bridge the mesh to the internet (MQTT) forward your messages there. Off asks them not to.",
            toggle = true),
        Setting(6, 104, "Ignore internet messages",
            "Ignores messages that reached the mesh from the internet through MQTT gateways.", toggle = true),
    )),
    Group("Location", listOf(
        Setting(2, 13, "GPS",
            "Whether the node uses its GPS chip. Off saves a lot of battery. Choose Not present when the board has no GPS.",
            options = listOf(1L to "On", 0L to "Off", 2L to "Not present")),
        Setting(2, 1, "Share location every",
            "How often the node broadcasts its position to the mesh.",
            options = every("1 hour", 300, 900, 1800, 3600, 10800, 21600, 43200)),
        Setting(2, 2, "Smart sharing",
            "Also shares your location early after you've moved a good distance, and skips updates while you stay put.",
            toggle = true),
        Setting(2, 5, "Check GPS every",
            "How often the node wakes the GPS for a fix. Less often saves battery.",
            options = every("2 minutes", 30, 120, 300, 900, 3600)),
    )),
    Group("Power", listOf(
        Setting(3, 1, "Power saving",
            "Sleeps deeply between radio activity, for nodes on small batteries or solar. Saves a lot on ESP32 boards, but turns Bluetooth and the screen off most of the time. nRF52 boards are already efficient." + BT_WARNING,
            toggle = true),
        Setting(3, 2, "Turn off on battery after",
            "Powers the node off this long after it's unplugged from USB or solar. Never keeps it running on battery.",
            options = listOf(0L to "Never", 300L to duration(300), 3600L to duration(3600), 21600L to duration(21600), 86400L to duration(86400))),
    )),
    Group("Screen", listOf(
        Setting(5, 1, "Screen timeout",
            "How long the node's screen stays on after a button press or new message. Only matters for boards with a screen.",
            options = every("10 minutes", 15, 30, 60, 300, 600, 3600) + (0xFFFFFFFFL to "Always on")),
        Setting(5, 5, "Flip screen", "Turns the screen upside down, for cases that mount the board the other way.", toggle = true),
        Setting(5, 6, "Units", "Distances and altitudes on the node's screen.", options = listOf(0L to "Metric", 1L to "Imperial")),
        Setting(5, 12, "12-hour clock", "Shows the time as 1:00 PM instead of 13:00 on the node's screen.", toggle = true),
    )),
    Group("Bluetooth", listOf(
        Setting(7, 1, "Bluetooth",
            "Lets phones connect to the node over Bluetooth. If you turn it off, only a USB cable can reach the node's settings again.",
            toggle = true),
        Setting(7, 2, "Pairing",
            "How a phone proves it's allowed to connect.\n\n• PIN on screen: shows a new random PIN each time (needs a screen).\n• Fixed PIN: always the same PIN, set below. Nodes without a screen use 123456.\n• No PIN: anyone nearby can connect and read your messages." + BT_WARNING,
            options = listOf(0L to "PIN on screen", 1L to "Fixed PIN", 2L to "No PIN")),
        Setting(7, 3, "Fixed PIN", "The 6-digit PIN phones type when pairing.", pin = true, visible = { get -> get(7, 2) == 1L }),
    )),
    Group("Troubleshooting", listOf(
        Setting(8, 6, "Debug log",
            "Streams the node's internal log to this app, for diagnosing problems with a computer attached to the phone (adb logcat, tag NodeLog). Leave it off otherwise: it adds traffic between the node and the phone.",
            toggle = true),
    )),
    Group("Wi-Fi", listOf(
        Setting(4, 1, "Wi-Fi",
            "Connects the node to a Wi-Fi network, for internet gateways. On ESP32 boards Wi-Fi turns Bluetooth off.",
            toggle = true, visible = { Mesh.hasWifi }),
        Setting(4, 3, "Network name", "The Wi-Fi network (SSID) to join.", text = true, visible = { Mesh.hasWifi }),
        Setting(4, 4, "Password", "The Wi-Fi password.", text = true, secret = true, visible = { Mesh.hasWifi }),
    )),
)

class Reset(val kind: Int, val title: String, val summary: String, val details: String, val confirm: String)

private val RESETS = listOf(
    Reset(100, "Clear node list", "Forget every node it has heard; favorites stay",
        "Your node forgets every other node it has heard, except favorites. They reappear as they're heard again. Handy when the list is full of nodes from far away.",
        "Clear"),
    Reset(99, "Reset settings", "Back to factory settings; keeps its identity and pairing",
        "Every setting goes back to factory defaults: role, radio, region, name and private rooms (you'll need new invites). The node keeps its identity and Bluetooth pairing, and messages on this phone stay. It restarts (if its screen doesn't, press its reset button), and you'll pick its region again.",
        "Reset"),
    Reset(94, "Factory reset", "Erase everything, as if it were new",
        "Erases everything on the node: settings, rooms, node list, its encryption keys and Bluetooth pairings. Others will see it as a new node. It restarts (if its screen doesn't within 15 seconds, press its reset button) and leaves Your devices: pair it again like a new node, with the PIN on its screen, then pick its region and name. Messages on this phone stay.",
        "Erase everything"),
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NodeSettingsScreen(onBack: () -> Unit, onUpdate: () -> Unit) {
    BackHandler(onBack = onBack)
    val edits = remember { mutableStateMapOf<String, Any>() }
    var info by remember { mutableStateOf<Setting?>(null) }
    var editing by remember { mutableStateOf<Setting?>(null) }
    var resetting by remember { mutableStateOf<Reset?>(null) }
    val sections = Mesh.configs.mapValues { Msg(it.value) }
    fun value(s: Setting): Any = edits[s.key] ?: sections[s.section]?.let { if (s.text) it.str(s.field) else it.long(s.field) } ?: if (s.text) "" else 0L
    val get = { section: Int, field: Int -> (edits["$section/$field"] ?: sections[section]?.long(field) ?: 0L) as Long }
    fun set(s: Setting, v: Any) {
        val original = sections[s.section]?.let { if (s.text) it.str(s.field) else it.long(s.field) } ?: if (s.text) "" else 0L
        if (v == original) edits.remove(s.key) else edits[s.key] = v
    }
    val save = {
        val changes = HashMap<Int, Pb>()
        SETTINGS.flatMap { it.settings }.filter { it.key in edits }.forEach { s ->
            val pb = changes.getOrPut(s.section) { Pb() }
            when (val v = edits[s.key]) { is String -> pb.str(s.field, v); is Long -> pb.set(s.field, v) }
            s.also?.let { (f, v) -> pb.set(f, v) }
        }
        Mesh.saveSettings(changes)
        onBack()
    }
    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = { IconButton(onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back") } },
                title = { Text("Node settings") },
            )
        },
        bottomBar = {
            if (edits.isNotEmpty()) Surface(tonalElevation = 3.dp) {
                Row(Modifier.navigationBarsPadding().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    TextButton({ edits.clear() }) { Text("Undo") }
                    Button(save, Modifier.weight(1f).height(56.dp), enabled = Mesh.connected) {
                        Icon(Icons.Rounded.Check, null)
                        Spacer(Modifier.width(10.dp))
                        Text(if (edits.size == 1) "Save 1 change" else "Save ${edits.size} changes")
                    }
                }
            }
        },
    ) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(bottom = 24.dp)) {
            item { BoardCard(onUpdate) }
            if (Mesh.configs.isEmpty()) {
                item { Text("Waiting for the node's settings…", Modifier.padding(16.dp), color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
            SETTINGS.forEach { group ->
                val shown = group.settings.filter { it.section in Mesh.configs && it.visible(get) }
                if (shown.isEmpty()) return@forEach
                header(group.title)
                items(shown, key = { it.key }) { s ->
                    val v = value(s)
                    val changed = s.key in edits
                    ListItem(
                        headlineContent = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(s.title, fontWeight = if (changed) FontWeight.SemiBold else null)
                                IconButton({ info = s }, Modifier.size(32.dp)) {
                                    Icon(Icons.Outlined.Info, "About ${s.title}", Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        },
                        supportingContent = if (s.toggle) null else ({
                            Text(
                                when {
                                    s.options != null -> s.options.firstOrNull { it.first == v }?.second ?: duration(v as Long)
                                    s.secret -> if ((v as String).isEmpty()) "Not set" else "••••••••"
                                    else -> v.toString().ifEmpty { "Not set" }
                                },
                                color = if (changed) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }),
                        trailingContent = if (s.toggle) ({
                            Switch((v == 1L) != s.invert, { on -> set(s, if (on != s.invert) 1L else 0L) })
                        }) else null,
                        modifier = Modifier.clickable {
                            if (s.toggle) set(s, if ((v == 1L)) 0L else 1L) else editing = s
                        },
                    )
                }
            }
            if (Mesh.configs.isNotEmpty()) {
                header("Reset")
                items(RESETS, key = { "reset${it.kind}" }) { r ->
                    ListItem(
                        headlineContent = { Text(r.title, color = if (r.kind == 100) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.error) },
                        supportingContent = { Text(r.summary) },
                        modifier = Modifier.clickable(enabled = Mesh.connected) { resetting = r },
                    )
                }
            }
        }
    }
    info?.let { s ->
        AlertDialog({ info = null }, { TextButton({ info = null }) { Text("Got it") } },
            icon = { Icon(Icons.Outlined.Info, null) }, title = { Text(s.title) },
            text = { Text(s.info, Modifier.verticalScroll(rememberScrollState())) })
    }
    resetting?.let { r ->
        AlertDialog(
            { resetting = null },
            confirmButton = { TextButton({ Mesh.reset(r.kind); resetting = null; onBack() }) { Text(r.confirm, color = MaterialTheme.colorScheme.error) } },
            dismissButton = { TextButton({ resetting = null }) { Text("Cancel") } },
            icon = { Icon(Icons.Rounded.RestartAlt, null) },
            title = { Text("${r.title}?") },
            text = { Text(r.details) },
        )
    }
    editing?.let { s -> EditDialog(s, value(s), { editing = null }) { set(s, it); editing = null } }
}

@Composable
fun EditDialog(s: Setting, current: Any, onDismiss: () -> Unit, onPick: (Any) -> Unit) {
    if (s.options != null) {
        AlertDialog(onDismiss, {}, dismissButton = { TextButton(onDismiss) { Text("Cancel") } }, title = { Text(s.title) }, text = {
            LazyColumn {
                items(s.options) { (v, label) ->
                    Row(Modifier.fillMaxWidth().clickable { onPick(v) }.padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(v == current, { onPick(v) })
                        Text(label)
                    }
                }
            }
        })
        return
    }
    var text by remember { mutableStateOf(if (current == 0L) "" else current.toString()) }
    val number = s.pin
    val ok = if (number) text.length == 6 && text.all { it.isDigit() } && text[0] != '0' else text.toByteArray().size <= 64
    AlertDialog(
        onDismiss,
        { TextButton({ onPick(if (number) text.toLong() else text) }, enabled = ok) { Text("OK") } },
        dismissButton = { TextButton(onDismiss) { Text("Cancel") } },
        title = { Text(s.title) },
        text = {
            OutlinedTextField(
                text, { text = it }, singleLine = true,
                visualTransformation = if (s.secret) PasswordVisualTransformation() else androidx.compose.ui.text.input.VisualTransformation.None,
                keyboardOptions = KeyboardOptions(keyboardType = if (number) KeyboardType.NumberPassword else if (s.secret) KeyboardType.Password else KeyboardType.Text),
            )
        },
    )
}

/** Which board this is, its chip and firmware, so you can tell your nodes apart and know if it can update over USB. */
@Composable
fun BoardCard(onUpdate: () -> Unit) {
    val board = currentBoard()
    Surface(Modifier.fillMaxWidth().padding(16.dp), shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surfaceContainerHigh) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.Memory, null)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(board?.name ?: "Unknown board", style = MaterialTheme.typography.titleMedium)
                    Text(
                        listOfNotNull(board?.chip, Mesh.firmware.ifEmpty { null }?.let { "Firmware $it" }).joinToString(" · ") +
                            "\nNode ID !%08x".format(Mesh.myNum) + (Mesh.nodes[Mesh.myNum]?.power?.let { "\n$it" } ?: ""),
                        style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (board != null) {
                Spacer(Modifier.height(12.dp))
                if (board.canUpdateOverUsb) {
                    LaunchedEffect(Unit) { if (Updater.latest.isEmpty()) Updater.checkLatest() }
                    when {
                        Updater.latest.isEmpty() || Mesh.firmware.isEmpty() -> {}
                        isNewer(Updater.latest, Mesh.firmware) -> OutlinedButton(onUpdate, Modifier.fillMaxWidth()) {
                            Icon(Icons.Rounded.SystemUpdate, null)
                            Spacer(Modifier.width(8.dp))
                            Text("Update to ${Updater.latest.substringBeforeLast('.')}")
                        }
                        else -> Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Rounded.Check, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.width(8.dp))
                            Text("Up to date", style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                } else {
                    Text(
                        "Update this board from a computer at flasher.meshtastic.org.",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

/** Firmware update over the USB cable (nRF52 boards). [rescue] = the node is already stuck in update mode. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FirmwareScreen(rescue: Boolean, onClose: () -> Unit) {
    // Leaving after a failed attempt: hand USB back to the app (it reconnects once the node is out of update mode).
    val onBack = { if (Mesh.updating) Mesh.resumeAfterUpdate(); onClose() }
    BackHandler(enabled = !Updater.running, onBack = onBack)
    val saved = remember { Mesh.appContext.getSharedPreferences("mesh", 0).getString("fwTarget", null) }
    var board by remember { mutableStateOf(if (rescue) boards.firstOrNull { it.target == saved } else currentBoard()) }
    LaunchedEffect(Unit) { Updater.error = ""; Updater.done = false; Updater.step = ""; if (Updater.latest.isEmpty()) Updater.checkLatest() }
    val onUsb = Mesh.transport == "USB cable" && Mesh.connected
    val onBle = Mesh.transport == "Bluetooth" && Mesh.connected
    Scaffold(topBar = {
        TopAppBar(
            navigationIcon = { IconButton(onBack, enabled = !Updater.running) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back") } },
            title = { Text("Update firmware") },
        )
    }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(24.dp)) {
            if (board == null) {
                Text("Which board is it?", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(8.dp))
                boards.filter { it.canUpdateOverUsb }.sortedBy { it.name }.forEach { b ->
                    ListItem(headlineContent = { Text(b.name) }, modifier = Modifier.clickable { board = b })
                }
                return@Column
            }
            val b = board!!
            Text(b.name, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
            Text(
                if (rescue) "In update mode. Plug it in, or keep it close if it was updating over Bluetooth." else "Installed: ${Mesh.firmware.ifEmpty { "unknown" }}",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                if (Updater.latest.isEmpty()) "Checking for the latest version…" else "Latest: ${Updater.latest}",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(24.dp))
            when {
                Updater.running || Updater.done -> {
                    Text(Updater.step, style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(12.dp))
                    if (Updater.progress < 0) LinearProgressIndicator(Modifier.fillMaxWidth())
                    else LinearProgressIndicator({ Updater.progress }, Modifier.fillMaxWidth())
                    Spacer(Modifier.height(12.dp))
                    if (Updater.done) Button(onBack, Modifier.fillMaxWidth().height(52.dp)) { Text("Done") }
                    else Text(
                        if (Mesh.transport == "Bluetooth") "Keep the app open and the phone near the node." else "Keep the cable plugged in and the app open. This takes about a minute.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                else -> {
                    if (Updater.error.isNotEmpty()) {
                        Surface(color = MaterialTheme.colorScheme.errorContainer, shape = RoundedCornerShape(16.dp)) {
                            Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Rounded.ErrorOutline, null, tint = MaterialTheme.colorScheme.onErrorContainer)
                                Spacer(Modifier.width(12.dp))
                                Text(Updater.error, color = MaterialTheme.colorScheme.onErrorContainer)
                            }
                        }
                        Spacer(Modifier.height(16.dp))
                    }
                    if (!rescue && !onUsb && !onBle && !Updater.inBootloader) {
                        Text("Connect to the node first, over Bluetooth or a USB cable.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.height(16.dp))
                    }
                    if (onBle || (Updater.inBootloader && Mesh.transport == "Bluetooth")) {
                        Text(
                            "Over Bluetooth this takes about 10 minutes; keep the app open and the phone close. " +
                                "If it's interrupted, the node waits in update mode: plug it in and tap Try again. A USB cable takes about a minute.",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.height(16.dp))
                    }
                    Text(
                        "Your settings, rooms and messages stay on the node. If an update is interrupted the node waits in update mode; just try again.",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(16.dp))
                    Button(
                        {
                            Mesh.appContext.getSharedPreferences("mesh", 0).edit().putString("fwTarget", b.target).apply()
                            Updater.start(b, Updater.latest, rescue)
                        },
                        Modifier.fillMaxWidth().height(56.dp),
                        enabled = Updater.latest.isNotEmpty() && (rescue || onUsb || onBle || Updater.inBootloader),
                    ) {
                        Icon(Icons.Rounded.SystemUpdate, null)
                        Spacer(Modifier.width(10.dp))
                        Text(if (Updater.error.isNotEmpty()) "Try again" else "Install ${Updater.latest}")
                    }
                }
            }
        }
    }
}
