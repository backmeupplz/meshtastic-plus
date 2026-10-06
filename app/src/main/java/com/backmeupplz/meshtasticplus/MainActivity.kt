package com.backmeupplz.meshtasticplus

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Intent
import android.hardware.usb.UsbManager
import android.os.Bundle
import android.os.ParcelUuid
import android.text.format.DateUtils
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts.RequestMultiplePermissions
import androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Chat
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.outlined.Badge
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Hub
import androidx.compose.material.icons.outlined.LinkOff
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material.icons.outlined.Router
import androidx.compose.material.icons.rounded.Bluetooth
import androidx.compose.material.icons.rounded.CellTower
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Done
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Usb
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import java.text.DateFormat
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.Date

private val Green = Color(0xFF2E9E50)
private val Amber = Color(0xFFE8A400)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        Mesh.init(this)
        onUsbAttached(intent)
        setContent {
            val ctx = LocalContext.current
            MaterialTheme(if (isSystemInDarkTheme()) dynamicDarkColorScheme(ctx) else dynamicLightColorScheme(ctx)) {
                App()
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        onUsbAttached(intent)
    }

    // Opened by plugging a node in: Android already granted USB access, so just connect.
    private fun onUsbAttached(intent: Intent?) {
        if (intent?.action == UsbManager.ACTION_USB_DEVICE_ATTACHED) Mesh.usbAttached()
    }
}

@Composable
fun App() {
    var editingName by remember { mutableStateOf(false) }
    when {
        editingName -> NameScreen(editing = true) { editingName = false }
        !Mesh.ready -> SetupScreen()
        Mesh.region == 0 -> RegionScreen()
        Mesh.askName -> NameScreen(editing = false) {}
        else -> MainScreen(onEditName = { editingName = true })
    }
}

// ---------- Setup: blocks the app until a node is connected ----------

@Composable
fun SetupScreen() {
    val ctx = LocalContext.current
    var scanning by remember { mutableStateOf(false) }
    val enableBluetooth = rememberLauncherForActivityResult(StartActivityForResult()) {
        if (it.resultCode == Activity.RESULT_OK) scanning = true
    }
    val askBluetooth = rememberLauncherForActivityResult(RequestMultiplePermissions()) { granted ->
        if (!granted.values.all { it }) return@rememberLauncherForActivityResult
        val adapter = ctx.getSystemService(BluetoothManager::class.java).adapter
        if (adapter?.isEnabled == true) scanning = true
        else enableBluetooth.launch(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE))
    }

    Surface(Modifier.fillMaxSize()) {
        Column(
            Modifier.fillMaxSize().systemBarsPadding().verticalScroll(rememberScrollState()).padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(56.dp))
            Box(
                Modifier.size(104.dp).background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Rounded.CellTower, null, Modifier.size(52.dp), tint = MaterialTheme.colorScheme.onPrimaryContainer)
            }
            Spacer(Modifier.height(24.dp))
            Text("Meshtastic+", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(8.dp))
            Text(
                "Connect your Meshtastic node to start chatting, no internet or cell service needed.",
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(40.dp))

            if (Mesh.active) {
                CircularProgressIndicator()
                Spacer(Modifier.height(16.dp))
                Text(Mesh.status, textAlign = TextAlign.Center, style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(16.dp))
                TextButton({ Mesh.forget() }) { Text("Cancel") }
            } else {
                if (Mesh.status.isNotEmpty()) {
                    Surface(color = MaterialTheme.colorScheme.errorContainer, shape = RoundedCornerShape(16.dp)) {
                        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Rounded.ErrorOutline, null, tint = MaterialTheme.colorScheme.onErrorContainer)
                            Spacer(Modifier.width(12.dp))
                            Text(Mesh.status, color = MaterialTheme.colorScheme.onErrorContainer)
                        }
                    }
                    Spacer(Modifier.height(24.dp))
                }
                Button(
                    { askBluetooth.launch(arrayOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT)) },
                    Modifier.fillMaxWidth().height(56.dp),
                ) {
                    Icon(Icons.Rounded.Bluetooth, null)
                    Spacer(Modifier.width(10.dp))
                    Text("Connect with Bluetooth")
                }
                Spacer(Modifier.height(12.dp))
                OutlinedButton({ scanning = false; Mesh.connectUsb() }, Modifier.fillMaxWidth().height(56.dp)) {
                    Icon(Icons.Rounded.Usb, null)
                    Spacer(Modifier.width(10.dp))
                    Text("Connect with USB cable")
                }
                Spacer(Modifier.height(12.dp))
                Text(
                    "Over a cable your phone also powers the node, so it doesn't need a battery.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
                if (scanning) NearbyNodes { scanning = false; Mesh.connectBle(it) }
            }
        }
    }
}

@SuppressLint("MissingPermission")
@Composable
fun NearbyNodes(onPick: (BluetoothDevice) -> Unit) {
    val ctx = LocalContext.current
    val found = remember { mutableStateMapOf<String, Pair<BluetoothDevice, String>>() }
    DisposableEffect(Unit) {
        val scanner = ctx.getSystemService(BluetoothManager::class.java).adapter?.bluetoothLeScanner
        val callback = object : ScanCallback() {
            override fun onScanResult(type: Int, r: ScanResult) {
                found[r.device.address] = r.device to (r.scanRecord?.deviceName ?: r.device.name ?: r.device.address)
            }
        }
        val filter = ScanFilter.Builder().setServiceUuid(ParcelUuid(SERVICE)).build()
        val settings = ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build()
        scanner?.startScan(listOf(filter), settings, callback)
        onDispose { runCatching { scanner?.stopScan(callback) } }
    }
    Column(Modifier.fillMaxWidth().padding(top = 32.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Nearby nodes", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
        }
        Spacer(Modifier.height(8.dp))
        if (found.isEmpty()) {
            Text(
                "Looking… make sure the node is on and not connected to another phone.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        found.values.forEach { (device, name) ->
            ElevatedCard({ onPick(device) }, Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                ListItem(
                    leadingContent = { Icon(Icons.Outlined.Router, null) },
                    headlineContent = { Text(name) },
                    supportingContent = { Text("Tap to connect") },
                    trailingContent = { Icon(Icons.Rounded.ChevronRight, null) },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                )
            }
        }
    }
}

// ---------- Region: a fresh node stays silent until it knows which radio band is legal here ----------

/** Meshtastic RegionCode values (config.proto), minus the amateur-radio bands that need a license. */
private val REGIONS = listOf(
    1 to "United States, Canada, Mexico", 3 to "Europe & UK 868 MHz", 2 to "Europe & UK 433 MHz", 6 to "Australia / New Zealand",
    11 to "New Zealand 865 MHz", 22 to "Australia / New Zealand 433 MHz", 26 to "Brazil", 4 to "China",
    10 to "India", 5 to "Japan", 24 to "Kazakhstan 863 MHz", 23 to "Kazakhstan 433 MHz", 7 to "Korea",
    17 to "Malaysia 919 MHz", 16 to "Malaysia 433 MHz", 25 to "Nepal", 21 to "Philippines 915 MHz",
    20 to "Philippines 868 MHz", 19 to "Philippines 433 MHz", 9 to "Russia", 18 to "Singapore", 8 to "Taiwan",
    12 to "Thailand", 15 to "Ukraine 868 MHz", 14 to "Ukraine 433 MHz", 13 to "2.4 GHz (worldwide)",
)

private val EU = setOf(
    "AT", "BE", "BG", "HR", "CY", "CZ", "DK", "EE", "FI", "FR", "DE", "GR", "HU", "IE", "IT", "LV", "LT", "LU",
    "MT", "NL", "PL", "PT", "RO", "SK", "SI", "ES", "SE", "GB", "NO", "CH", "IS", "RS", "ME", "MK", "AL", "BA",
    "MD", "GE", "AM", "TR", "LI", "MC", "AD", "SM",
)

/** Best guess from the phone's country; the user confirms it. */
fun suggestedRegion(country: String) = when (country) {
    in EU -> 3
    "US", "CA", "MX" -> 1
    "AU" -> 6; "NZ" -> 6; "BR" -> 26; "CN" -> 4; "IN" -> 10; "JP" -> 5; "KZ" -> 24; "KR" -> 7; "MY" -> 17
    "NP" -> 25; "PH" -> 21; "RU", "BY" -> 9; "SG" -> 18; "TW" -> 8; "TH" -> 12; "UA" -> 15
    else -> 0
}

@Composable
fun RegionScreen() {
    val ctx = LocalContext.current
    var picked by rememberSaveable {
        val tm = ctx.getSystemService(android.telephony.TelephonyManager::class.java)
        val country = (tm?.networkCountryIso?.ifEmpty { null } ?: java.util.Locale.getDefault().country).uppercase()
        mutableIntStateOf(suggestedRegion(country))
    }
    Scaffold(
        bottomBar = {
            Surface(tonalElevation = 3.dp) {
                Button(
                    { Mesh.saveRegion(picked) },
                    Modifier.fillMaxWidth().navigationBarsPadding().padding(16.dp).height(56.dp),
                    enabled = picked != 0 && Mesh.connected,
                ) {
                    Icon(Icons.Rounded.Check, null)
                    Spacer(Modifier.width(10.dp))
                    Text("Use this region")
                }
            }
        },
    ) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(bottom = 8.dp)) {
            item {
                Column(Modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Spacer(Modifier.height(24.dp))
                    Box(
                        Modifier.size(88.dp).background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(Icons.Outlined.Public, null, Modifier.size(44.dp), tint = MaterialTheme.colorScheme.onPrimaryContainer)
                    }
                    Spacer(Modifier.height(20.dp))
                    Text("Where are you?", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Your node stays silent until it knows which radio band is legal where you are. Pick the one for your country.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                }
            }
            items(REGIONS, key = { it.first }) { (code, label) ->
                ListItem(
                    headlineContent = { Text(label) },
                    leadingContent = { RadioButton(picked == code, { picked = code }) },
                    modifier = Modifier.clickable { picked = code },
                )
            }
        }
    }
}

// ---------- Identity ----------

/** Trims to fit a protobuf string field of [max] UTF-8 bytes (firmware limits). */
fun String.fitBytes(max: Int): String {
    var s = this
    while (s.toByteArray().size > max) s = s.dropLast(1)
    return s
}

fun initials(name: String): String {
    val words = name.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
    val s = if (words.size > 1) words.joinToString("") { it.take(1) } else words.firstOrNull().orEmpty()
    return s.uppercase().fitBytes(4)
}

@Composable
fun NameScreen(editing: Boolean, onClose: () -> Unit) {
    var long by remember { mutableStateOf(if (editing) Mesh.myLong else "") }
    var short by remember { mutableStateOf(if (editing) Mesh.myShort else "") }
    var shortEdited by remember { mutableStateOf(editing) }
    val canSave = long.isNotBlank() && short.isNotBlank() && Mesh.connected
    val save = { Mesh.setOwner(long.trim(), short.trim()); onClose() }
    Surface(Modifier.fillMaxSize()) {
        Column(
            Modifier.fillMaxSize().systemBarsPadding().imePadding().verticalScroll(rememberScrollState()).padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(56.dp))
            Avatar(short.ifEmpty { "?" }, Mesh.myNum, 104.dp)
            Spacer(Modifier.height(24.dp))
            Text(
                if (editing) "Your name" else "What should people call you?",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "This is how you show up for everyone on the mesh.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(32.dp))
            OutlinedTextField(
                long,
                { long = it.fitBytes(39); if (!shortEdited) short = initials(long) },
                Modifier.fillMaxWidth(),
                label = { Text("Name") },
                leadingIcon = { Icon(Icons.Outlined.Person, null) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Next),
            )
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                short,
                { short = it.fitBytes(4); shortEdited = true },
                Modifier.fillMaxWidth(),
                label = { Text("Short name") },
                leadingIcon = { Icon(Icons.Outlined.Badge, null) },
                supportingText = { Text("Up to 4 characters, shown on node screens") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { if (canSave) save() }),
            )
            Spacer(Modifier.height(24.dp))
            Button(save, Modifier.fillMaxWidth().height(56.dp), enabled = canSave) {
                Icon(Icons.Rounded.Check, null)
                Spacer(Modifier.width(10.dp))
                Text("Save")
            }
            TextButton({ if (!editing) Mesh.nameAsked(); onClose() }) { Text(if (editing) "Cancel" else "Skip for now") }
        }
    }
}

// ---------- Main: chat + nodes ----------

@Composable
fun rememberNow(): Long {
    val now by produceState(System.currentTimeMillis()) {
        while (true) { delay(30_000); value = System.currentTimeMillis() }
    }
    return now
}

fun nodeColor(num: Long) = Color.hsl((num % 360).toFloat(), 0.55f, 0.48f)

@Composable
fun Avatar(text: String, num: Long, size: Dp) {
    Box(Modifier.size(size).background(nodeColor(num), CircleShape), contentAlignment = Alignment.Center) {
        Text(text, color = Color.White, fontSize = (size.value * 0.34f).sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(onEditName: () -> Unit) {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    val now = rememberNow()
    val online = Mesh.nodes.values.count { it.num != Mesh.myNum && it.online(now) }
    Scaffold(
        topBar = {
            Column {
                TopAppBar(
                    title = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Avatar(Mesh.myShort, Mesh.myNum, 40.dp)
                            Spacer(Modifier.width(12.dp))
                            Column {
                                Text(Mesh.myLong, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.titleMedium)
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(Modifier.size(8.dp).background(if (Mesh.connected) Green else Amber, CircleShape))
                                    Spacer(Modifier.width(6.dp))
                                    Text(
                                        Mesh.status, maxLines = 1, overflow = TextOverflow.Ellipsis,
                                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }
                    },
                    actions = {
                        IconButton(onEditName) { Icon(Icons.Outlined.Edit, "Change name") }
                        IconButton({ Mesh.forget() }) { Icon(Icons.Outlined.LinkOff, "Disconnect") }
                    },
                )
                PrimaryTabRow(selectedTabIndex = tab) {
                    Tab(tab == 0, { tab = 0 }, text = { Text("Chat") }, icon = { Icon(Icons.AutoMirrored.Outlined.Chat, null) })
                    Tab(tab == 1, { tab = 1 }, text = { Text("Nodes") }, icon = {
                        BadgedBox({ if (online > 0) Badge(containerColor = Green, contentColor = Color.White) { Text("$online") } }) {
                            Icon(Icons.Outlined.Hub, null)
                        }
                    })
                }
            }
        },
        bottomBar = { if (tab == 0) Composer() },
    ) { padding ->
        if (tab == 0) ChatList(padding) else NodeList(padding, now)
    }
}

@Composable
fun EmptyState(padding: PaddingValues, icon: ImageVector, title: String, body: String) {
    Column(
        Modifier.fillMaxSize().padding(padding).padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(icon, null, Modifier.size(56.dp), tint = MaterialTheme.colorScheme.outline)
        Spacer(Modifier.height(16.dp))
        Text(title, style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(4.dp))
        Text(body, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
    }
}

private fun day(t: Long) = Instant.ofEpochMilli(t).atZone(ZoneId.systemDefault()).toLocalDate()

@Composable
fun ChatList(padding: PaddingValues) {
    val list = rememberLazyListState()
    LaunchedEffect(Mesh.messages.size) {
        if (Mesh.messages.isNotEmpty()) list.animateScrollToItem(Mesh.messages.lastIndex)
    }
    if (Mesh.messages.isEmpty()) {
        return EmptyState(padding, Icons.AutoMirrored.Outlined.Chat, "No messages yet", "Say hi! Everyone on your channel will see it.")
    }
    LazyColumn(
        Modifier.fillMaxSize().padding(padding),
        state = list,
        contentPadding = PaddingValues(12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        itemsIndexed(Mesh.messages) { i, m ->
            val prev = Mesh.messages.getOrNull(i - 1)
            val newDay = prev == null || day(prev.time) != day(m.time)
            if (newDay) DayChip(m.time)
            Bubble(m, showSender = newDay || prev.from != m.from || prev.mine != m.mine)
        }
    }
}

@Composable
fun DayChip(t: Long) {
    val label = when (day(t)) {
        LocalDate.now() -> "Today"
        LocalDate.now().minusDays(1) -> "Yesterday"
        else -> DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(t))
    }
    Box(Modifier.fillMaxWidth().padding(vertical = 8.dp), contentAlignment = Alignment.Center) {
        Surface(color = MaterialTheme.colorScheme.surfaceContainerHigh, shape = CircleShape) {
            Text(label, Modifier.padding(horizontal = 12.dp, vertical = 4.dp), style = MaterialTheme.typography.labelMedium)
        }
    }
}

@Composable
fun Bubble(m: Message, showSender: Boolean) {
    val node = Mesh.nodes[m.from]
    val time = remember(m.time) { DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(m.time)) }
    Row(
        Modifier.fillMaxWidth().padding(top = if (showSender) 6.dp else 0.dp),
        horizontalArrangement = if (m.mine) Arrangement.End else Arrangement.Start,
        verticalAlignment = Alignment.Top,
    ) {
        if (!m.mine) {
            if (showSender) Avatar(node?.initials ?: m.name.take(2), m.from, 34.dp) else Spacer(Modifier.width(34.dp))
            Spacer(Modifier.width(8.dp))
        }
        Column(Modifier.widthIn(max = 290.dp), horizontalAlignment = if (m.mine) Alignment.End else Alignment.Start) {
            if (!m.mine && showSender) {
                Text(
                    (node?.name ?: m.name) + if (m.dm) " · direct" else "",
                    Modifier.padding(start = 4.dp, bottom = 2.dp),
                    style = MaterialTheme.typography.labelMedium,
                    color = nodeColor(m.from),
                )
            }
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = if (m.mine) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHigh,
            ) {
                Column(Modifier.padding(start = 14.dp, end = 14.dp, top = 8.dp, bottom = 6.dp)) {
                    SelectionContainer { Text(m.text, style = MaterialTheme.typography.bodyLarge) }
                    Row(Modifier.align(Alignment.End), verticalAlignment = Alignment.CenterVertically) {
                        val faded = LocalContentColor.current.copy(alpha = 0.7f)
                        Text(time, style = MaterialTheme.typography.labelSmall, color = faded)
                        if (m.mine) {
                            Spacer(Modifier.width(4.dp))
                            val (icon, label) = when (m.status) {
                                "✓" -> Icons.Rounded.Done to "Relayed by the mesh"
                                "✗" -> Icons.Rounded.ErrorOutline to "Nobody relayed it"
                                else -> Icons.Rounded.Schedule to "Sending"
                            }
                            Icon(icon, label, Modifier.size(14.dp), tint = faded)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun Composer() {
    var text by rememberSaveable { mutableStateOf("") }
    val tooLong = text.toByteArray().size > 200 // firmware text payload limit
    val canSend = Mesh.connected && text.isNotBlank() && !tooLong
    Surface(tonalElevation = 3.dp) {
        Row(
            Modifier.navigationBarsPadding().imePadding().padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedTextField(
                text, { text = it }, Modifier.weight(1f),
                placeholder = { Text(if (Mesh.connected) "Message the mesh" else "Waiting for the node…") },
                shape = RoundedCornerShape(28.dp),
                maxLines = 5,
                isError = tooLong,
                supportingText = if (tooLong) ({ Text("Too long for one mesh message") }) else null,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
            )
            Spacer(Modifier.width(8.dp))
            FilledIconButton({ Mesh.send(text.trim()); text = "" }, Modifier.size(52.dp), enabled = canSend) {
                Icon(Icons.AutoMirrored.Rounded.Send, "Send")
            }
        }
    }
}

@Composable
fun NodeList(padding: PaddingValues, now: Long) {
    val nodes = Mesh.nodes.values.filter { it.num != Mesh.myNum }
        .sortedWith(compareByDescending<Node> { it.online(now) }.thenByDescending { it.lastHeard })
    if (nodes.isEmpty()) {
        return EmptyState(padding, Icons.Outlined.Hub, "No nodes yet", "Nodes show up here as your radio hears them.")
    }
    LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(vertical = 8.dp)) {
        items(nodes, key = { it.num }) { n ->
            val heard = when {
                n.lastHeard == 0L -> "Never heard"
                now - n.lastHeard < 60_000 -> "Heard just now"
                else -> "Heard " + DateUtils.getRelativeTimeSpanString(n.lastHeard, now, DateUtils.MINUTE_IN_MILLIS)
            }
            val hops = n.hops?.let { if (it == 0) "direct" else if (it == 1) "1 hop" else "$it hops" }
            ListItem(
                leadingContent = { Avatar(n.initials, n.num, 44.dp) },
                headlineContent = { Text(n.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                supportingContent = { Text(listOfNotNull(heard, hops).joinToString(" · ")) },
                trailingContent = { StatusPill(n.online(now)) },
            )
        }
    }
}

@Composable
fun StatusPill(online: Boolean) {
    val color = if (online) Green else MaterialTheme.colorScheme.outline
    Surface(shape = CircleShape, color = color.copy(alpha = 0.14f)) {
        Row(Modifier.padding(horizontal = 10.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(8.dp).background(color, CircleShape))
            Spacer(Modifier.width(6.dp))
            Text(if (online) "Online" else "Offline", style = MaterialTheme.typography.labelMedium, color = color)
        }
    }
}
