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
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.graphics.Bitmap
import android.hardware.usb.UsbManager
import android.os.Bundle
import android.os.ParcelUuid
import android.text.format.DateUtils
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts.RequestMultiplePermissions
import androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
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
import androidx.compose.material.icons.automirrored.outlined.Logout
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.outlined.Badge
import androidx.compose.material.icons.outlined.BatteryFull
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.ContentPaste
import androidx.compose.material.icons.outlined.Hub
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material.icons.outlined.QrCodeScanner
import androidx.compose.material.icons.outlined.Router
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.SignalCellularAlt
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Bluetooth
import androidx.compose.material.icons.rounded.CellTower
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Done
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.PersonAdd
import androidx.compose.material.icons.rounded.QrCode2
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.Usb
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.codescanner.GmsBarcodeScannerOptions
import com.google.mlkit.vision.codescanner.GmsBarcodeScanning
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import kotlinx.coroutines.delay
import java.text.DateFormat
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.Date

private val Green = Color(0xFF2E9E50)
private val Amber = Color(0xFFE8A400)

/** A meshtastic.org/e/ invite the app was opened with, waiting for the user to confirm. */
val pendingInvite = mutableStateOf<String?>(null)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        Mesh.init(this)
        handle(intent)
        setContent {
            val ctx = LocalContext.current
            MaterialTheme(if (isSystemInDarkTheme()) dynamicDarkColorScheme(ctx) else dynamicLightColorScheme(ctx)) {
                App()
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handle(intent)
    }

    private fun handle(intent: Intent?) {
        when (intent?.action) {
            // Opened by plugging a node in: Android already granted USB access, so just connect.
            UsbManager.ACTION_USB_DEVICE_ATTACHED -> Mesh.usbAttached()
            Intent.ACTION_VIEW -> intent.dataString?.let { pendingInvite.value = it }
        }
    }
}

@Composable
fun App() {
    var editingName by remember { mutableStateOf(false) }
    var editingRegion by remember { mutableStateOf(false) }
    var open by rememberSaveable { mutableStateOf<String?>(null) }
    var profile by rememberSaveable { mutableStateOf<Long?>(null) }
    var nodeSettings by rememberSaveable { mutableStateOf(false) }
    var firmware by rememberSaveable { mutableStateOf<Boolean?>(null) } // true = rescue a node stuck in update mode
    when {
        firmware != null -> FirmwareScreen(firmware!!) { firmware = null }
        editingName -> NameScreen(editing = true) { editingName = false }
        !Mesh.ready -> SetupScreen { firmware = true }
        Mesh.region == 0 || editingRegion -> RegionScreen(editing = editingRegion) { editingRegion = false }
        Mesh.askName -> NameScreen(editing = false) {}
        nodeSettings -> NodeSettingsScreen({ nodeSettings = false }) { firmware = false }
        open != null -> ConversationScreen(open!!, onBack = { open = null }, onProfile = { profile = it })
        else -> HomeScreen(
            onOpen = { open = it },
            onProfile = { profile = it },
            onEditName = { editingName = true },
            onEditRegion = { editingRegion = true },
            onNodeSettings = { nodeSettings = true },
        )
    }
    if (Mesh.ready) {
        profile?.let { num ->
            ProfileSheet(num, onDismiss = { profile = null }, onMessage = { profile = null; open = convoKey(0, num) })
        }
        pendingInvite.value?.let { link -> JoinDialog(link) { joined -> pendingInvite.value = null; joined?.let { open = it } } }
    }
}

// ---------- Setup: blocks the app until a node is connected ----------

/** Asks for Bluetooth permission (and to turn Bluetooth on), then calls [onReady]. */
@Composable
fun rememberBluetooth(onReady: () -> Unit): () -> Unit {
    val ctx = LocalContext.current
    val enable = rememberLauncherForActivityResult(StartActivityForResult()) {
        if (it.resultCode == Activity.RESULT_OK) onReady()
    }
    val ask = rememberLauncherForActivityResult(RequestMultiplePermissions()) { granted ->
        if (!granted.values.all { it }) return@rememberLauncherForActivityResult
        if (ctx.getSystemService(BluetoothManager::class.java).adapter?.isEnabled == true) onReady()
        else enable.launch(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE))
    }
    return { ask.launch(arrayOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT)) }
}

/** Devices used before, with the current one checked. Tapping another one switches to it. */
@Composable
fun DeviceRows(onPick: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Mesh.saved.toList().forEach { (key, name) ->
            if (key != USB && !Mesh.canUseBluetooth()) return@forEach
            val current = key == Mesh.current
            DeviceRow(
                if (key == USB) Icons.Rounded.Usb else Icons.Rounded.Bluetooth,
                name.ifEmpty { key },
                if (current) Mesh.status else if (key == USB) "USB cable" else "Bluetooth",
                highlighted = current,
                onClick = { if (!current) onPick(key) },
            ) {
                if (current) Icon(Icons.Rounded.Check, "Connected", Modifier.padding(end = 12.dp), tint = MaterialTheme.colorScheme.primary)
                else IconButton({ Mesh.forgetSaved(key) }) {
                    Icon(Icons.Rounded.Close, "Forget this device", Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
fun DeviceRow(
    icon: ImageVector, title: String, subtitle: String, highlighted: Boolean = false,
    onClick: () -> Unit, trailing: @Composable () -> Unit = {},
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        color = if (highlighted) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainerHighest,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(Modifier.heightIn(min = 64.dp).padding(start = 16.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null)
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f).padding(vertical = 10.dp)) {
                Text(title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
            }
            trailing()
        }
    }
}

@Composable
fun SetupScreen(onRescue: () -> Unit) {
    var scanning by remember { mutableStateOf(false) }
    val askBluetooth = rememberBluetooth { scanning = true }
    val scroll = rememberScrollState()
    // The scan list sits below the buttons, off screen on most phones: bring it into view.
    LaunchedEffect(scanning) { if (scanning) { delay(100); scroll.animateScrollTo(scroll.maxValue) } }

    Surface(Modifier.fillMaxSize()) {
        Column(
            Modifier.fillMaxSize().systemBarsPadding().verticalScroll(scroll).padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(56.dp))
            HeroIcon(Icons.Rounded.CellTower)
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
                return@Column
            }
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
            if (Mesh.saved.isNotEmpty()) {
                Text("Your devices", style = MaterialTheme.typography.titleMedium, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(8.dp))
                DeviceRows { Mesh.connect(it) }
                Spacer(Modifier.height(24.dp))
            }
            Button(askBluetooth, Modifier.fillMaxWidth().height(56.dp)) {
                Icon(Icons.Rounded.Bluetooth, null)
                Spacer(Modifier.width(10.dp))
                Text(if (Mesh.saved.isEmpty()) "Connect with Bluetooth" else "Find another node")
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
            Spacer(Modifier.height(24.dp))
            TextButton(onRescue) { Text("Node stuck in update mode?") }
        }
    }
}

@Composable
fun HeroIcon(icon: ImageVector) {
    Box(
        Modifier.size(104.dp).background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, null, Modifier.size(52.dp), tint = MaterialTheme.colorScheme.onPrimaryContainer)
    }
}

@SuppressLint("MissingPermission")
@Composable
fun NearbyNodes(onPick: (BluetoothDevice) -> Unit) {
    val known = Mesh.saved.map { it.first }.toSet()
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
        // nRF52 and ESP32 nodes derive their node number from the Bluetooth address's last 4 bytes.
        val usbNum = if (Mesh.saved.any { it.first == USB }) Mesh.usbNum else 0L // only while it's still one of your devices
        fun isUsbNode(d: BluetoothDevice) = usbNum != 0L && d.address.replace(":", "").takeLast(8).toLongOrNull(16) == usbNum
        val fresh = found.values.filter { it.first.address !in known }.sortedByDescending { isUsbNode(it.first) }
        if (fresh.isEmpty()) {
            Text(
                "Looking… make sure the node is on and not connected to another phone.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            fresh.forEach { (device, name) ->
                val subtitle = if (isUsbNode(device)) "Your USB node · tap to use Bluetooth" else "Tap to connect"
                DeviceRow(Icons.Outlined.Router, name, subtitle, highlighted = isUsbNode(device), onClick = { onPick(device) }) {
                    Icon(Icons.Rounded.ChevronRight, null, Modifier.padding(end = 12.dp))
                }
            }
        }
    }
}

// ---------- Region: a fresh node stays silent until it knows which radio band is legal here ----------

/** Meshtastic RegionCode values (config.proto), minus the amateur-radio bands that need a license. */
val REGIONS = listOf(
    1 to "United States, Canada, Mexico", 3 to "Europe & UK 868 MHz", 2 to "Europe & UK 433 MHz",
    6 to "Australia / New Zealand", 11 to "New Zealand 865 MHz", 22 to "Australia / New Zealand 433 MHz",
    26 to "Brazil", 4 to "China", 10 to "India", 5 to "Japan", 24 to "Kazakhstan 863 MHz", 23 to "Kazakhstan 433 MHz",
    7 to "Korea", 17 to "Malaysia 919 MHz", 16 to "Malaysia 433 MHz", 25 to "Nepal", 21 to "Philippines 915 MHz",
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

fun regionName(code: Int) = REGIONS.firstOrNull { it.first == code }?.second ?: "Not set"

@Composable
fun RegionScreen(editing: Boolean, onDone: () -> Unit) {
    val ctx = LocalContext.current
    var picked by rememberSaveable {
        val tm = ctx.getSystemService(android.telephony.TelephonyManager::class.java)
        val country = (tm?.networkCountryIso?.ifEmpty { null } ?: java.util.Locale.getDefault().country).uppercase()
        mutableIntStateOf(if (editing) Mesh.region else suggestedRegion(country))
    }
    if (editing) BackHandler(onBack = onDone)
    Scaffold(
        bottomBar = {
            Surface(tonalElevation = 3.dp) {
                Row(Modifier.navigationBarsPadding().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (editing) TextButton(onDone) { Text("Cancel") }
                    Button(
                        { if (picked != Mesh.region) Mesh.saveRegion(picked); onDone() },
                        Modifier.weight(1f).height(56.dp),
                        enabled = picked != 0 && Mesh.connected,
                    ) {
                        Icon(Icons.Rounded.Check, null)
                        Spacer(Modifier.width(10.dp))
                        Text("Use this region")
                    }
                }
            }
        },
    ) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(bottom = 8.dp)) {
            item {
                Column(Modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Spacer(Modifier.height(24.dp))
                    HeroIcon(Icons.Outlined.Public)
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
    if (editing) BackHandler(onBack = onClose)
    Surface(Modifier.fillMaxSize()) {
        Column(
            Modifier.fillMaxSize().systemBarsPadding().imePadding().verticalScroll(rememberScrollState()).padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(56.dp))
            HeroIcon(Icons.Outlined.Badge)
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

// ---------- Home: chats + nodes ----------

@Composable
fun rememberNow(): Long {
    val now by produceState(System.currentTimeMillis()) {
        while (true) { delay(30_000); value = System.currentTimeMillis() }
    }
    return now
}

fun nodeColor(num: Long) = Color.hsl((num % 360).toFloat(), 0.55f, 0.48f)

@Composable
fun StatusTitle(title: String, subtitle: String, ok: Boolean) {
    Column {
        Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.titleMedium)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(8.dp).background(if (ok) Green else Amber, CircleShape))
            Spacer(Modifier.width(6.dp))
            Text(
                subtitle, maxLines = 1, overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(onOpen: (String) -> Unit, onProfile: (Long) -> Unit, onEditName: () -> Unit, onEditRegion: () -> Unit, onNodeSettings: () -> Unit) {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var settings by remember { mutableStateOf(false) }
    var devices by remember { mutableStateOf(false) }
    var newRoom by remember { mutableStateOf(false) }
    val now = rememberNow()
    val online = Mesh.nodes.values.count { it.num != Mesh.myNum && it.online(now) }
    val unread = Mesh.messages.filter { !it.mine }.map { it.convo }.distinct().sumOf { Mesh.unread(it) }
    Scaffold(
        topBar = {
            Column {
                TopAppBar(
                    title = {
                        Row(Modifier.clickable { devices = true }, verticalAlignment = Alignment.CenterVertically) {
                            StatusTitle(Mesh.myLong, Mesh.status, Mesh.connected)
                            Icon(Icons.Rounded.ExpandMore, "Switch device", Modifier.padding(start = 4.dp))
                        }
                    },
                    actions = { IconButton({ settings = true }) { Icon(Icons.Outlined.Settings, "Settings") } },
                )
                PrimaryTabRow(selectedTabIndex = tab) {
                    Tab(tab == 0, { tab = 0 }, text = { Text("Chats") }, icon = {
                        BadgedBox({ if (unread > 0) Badge { Text("$unread") } }) { Icon(Icons.AutoMirrored.Outlined.Chat, null) }
                    })
                    Tab(tab == 1, { tab = 1 }, text = { Text("Nodes") }, icon = {
                        BadgedBox({ if (online > 0) Badge(containerColor = Green, contentColor = Color.White) { Text("$online") } }) {
                            Icon(Icons.Outlined.Hub, null)
                        }
                    })
                }
            }
        },
        floatingActionButton = {
            if (tab == 0) ExtendedFloatingActionButton(text = { Text("Room") }, icon = { Icon(Icons.Rounded.Add, null) }, onClick = { newRoom = true })
        },
    ) { padding ->
        if (tab == 0) ChatsTab(padding, onOpen) else NodesTab(padding, now, onProfile)
    }
    if (settings) SettingsSheet({ settings = false }, onEditName, onEditRegion, onNodeSettings) { devices = true }
    if (devices) DevicesSheet { devices = false }
    if (newRoom) NewRoomSheet({ newRoom = false }, onOpen)
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

fun LazyListScope.header(text: String) = item(text) {
    Text(
        text, Modifier.padding(start = 16.dp, top = 16.dp, bottom = 4.dp),
        style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary,
    )
}

@Composable
fun ConversationRow(icon: ImageVector, title: String, convo: String, onOpen: (String) -> Unit) {
    val last = Mesh.messages.lastOrNull { it.convo == convo }
    val unread = Mesh.unread(convo)
    ListItem(
        leadingContent = { Icon(icon, null) },
        headlineContent = { Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = if (unread > 0) FontWeight.SemiBold else null) },
        supportingContent = {
            Text(
                last?.let { (if (it.mine) "You" else it.name) + ": " + it.text } ?: "No messages yet",
                maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
        },
        trailingContent = {
            Column(horizontalAlignment = Alignment.End) {
                last?.let { Text(shortTime(it.time), style = MaterialTheme.typography.labelSmall) }
                if (unread > 0) Badge(Modifier.padding(top = 4.dp)) { Text("$unread") }
            }
        },
        modifier = Modifier.clickable { onOpen(convo) },
    )
}

fun shortTime(t: Long): String =
    DateUtils.formatSameDayTime(t, System.currentTimeMillis(), DateFormat.SHORT, DateFormat.SHORT).toString()

@Composable
fun ChatsTab(padding: PaddingValues, onOpen: (String) -> Unit) {
    val rooms = Mesh.rooms.values.sortedBy { it.index }
    // DMs: everyone we've talked to, plus favorites so they're one tap away.
    val peers = (Mesh.messages.filter { it.peer != 0L }.sortedByDescending { it.time }.map { it.peer } +
        Mesh.nodes.values.filter { it.favorite && it.num != Mesh.myNum }.map { it.num }).distinct()
    LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(bottom = 96.dp)) {
        header("Rooms")
        items(rooms, key = { "room${it.index}" }) { r ->
            ConversationRow(if (r.public) Icons.Outlined.Public else Icons.Outlined.Lock, r.title, convoKey(r.index, 0), onOpen)
        }
        header("Direct messages")
        if (peers.isEmpty()) {
            item {
                Text(
                    "Open someone in Nodes to message them privately.",
                    Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        items(peers, key = { "dm$it" }) { num ->
            val n = Mesh.nodes[num]
            ConversationRow(if (n?.favorite == true) Icons.Rounded.Star else Icons.Outlined.Person, n?.name ?: "!%08x".format(num), convoKey(0, num), onOpen)
        }
    }
}

@Composable
fun NodesTab(padding: PaddingValues, now: Long, onProfile: (Long) -> Unit) {
    val nodes = Mesh.nodes.values.filter { it.num != Mesh.myNum }
        .sortedWith(compareByDescending<Node> { it.online(now) }.thenByDescending { it.lastHeard })
    if (nodes.isEmpty()) {
        return EmptyState(padding, Icons.Outlined.Hub, "No nodes yet", "Nodes show up here as your radio hears them.")
    }
    val (favorites, others) = nodes.partition { it.favorite }
    LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(bottom = 8.dp)) {
        if (favorites.isNotEmpty()) {
            header("Favorites")
            items(favorites, key = { "f${it.num}" }) { NodeRow(it, now, onProfile) }
            if (others.isNotEmpty()) header("Everyone")
        }
        items(others, key = { it.num }) { NodeRow(it, now, onProfile) }
    }
}

fun heard(n: Node, now: Long) = when {
    n.lastHeard == 0L -> "Never heard"
    now - n.lastHeard < 60_000 -> "Heard just now"
    else -> "Heard " + DateUtils.getRelativeTimeSpanString(n.lastHeard, now, DateUtils.MINUTE_IN_MILLIS)
}

fun hops(n: Node) = n.hops?.let { if (it == 0) "Direct" else if (it == 1) "1 hop away" else "$it hops away" }

@Composable
fun NodeRow(n: Node, now: Long, onProfile: (Long) -> Unit) {
    ListItem(
        headlineContent = { Text(n.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        supportingContent = { Text(listOfNotNull(heard(n, now), hops(n)).joinToString(" · ")) },
        trailingContent = { StatusPill(n.online(now)) },
        modifier = Modifier.clickable { onProfile(n.num) },
    )
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

// ---------- Sheets ----------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileSheet(num: Long, onDismiss: () -> Unit, onMessage: () -> Unit) {
    val n = Mesh.nodes[num] ?: Node(num)
    val now = rememberNow()
    ModalBottomSheet(onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.padding(horizontal = 24.dp).padding(bottom = 24.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(n.name, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
                    Text(
                        listOf(n.short, n.id).filter { it.isNotEmpty() }.joinToString(" · "),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                StatusPill(n.online(now))
            }
            Spacer(Modifier.height(16.dp))
            Detail(Icons.Outlined.Schedule, heard(n, now))
            hops(n)?.let { Detail(Icons.Outlined.Hub, it) }
            n.snr?.let { Detail(Icons.Outlined.SignalCellularAlt, "Signal %.1f dB SNR".format(it)) }
            n.power?.let { Detail(Icons.Outlined.BatteryFull, it) }
            Spacer(Modifier.height(24.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(onMessage, Modifier.weight(1f).height(52.dp)) {
                    Icon(Icons.AutoMirrored.Outlined.Chat, null)
                    Spacer(Modifier.width(8.dp))
                    Text("Message")
                }
                OutlinedButton({ Mesh.setFavorite(num, !n.favorite) }, Modifier.weight(1f).height(52.dp), enabled = Mesh.connected) {
                    Icon(if (n.favorite) Icons.Rounded.Star else Icons.Outlined.StarBorder, null)
                    Spacer(Modifier.width(8.dp))
                    Text(if (n.favorite) "Favorited" else "Favorite", maxLines = 1)
                }
            }
        }
    }
}

@Composable
fun Detail(icon: ImageVector, text: String) {
    Row(Modifier.padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.width(12.dp))
        Text(text)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsSheet(onDismiss: () -> Unit, onEditName: () -> Unit, onEditRegion: () -> Unit, onNodeSettings: () -> Unit, onDevices: () -> Unit) {
    ModalBottomSheet(onDismiss) {
        Column(Modifier.padding(bottom = 24.dp)) {
            ListItem(
                leadingContent = { Icon(Icons.Outlined.Person, null) },
                headlineContent = { Text("Name") },
                supportingContent = { Text("${Mesh.myLong} (${Mesh.myShort})") },
                modifier = Modifier.clickable { onDismiss(); onEditName() },
            )
            ListItem(
                leadingContent = { Icon(Icons.Outlined.Public, null) },
                headlineContent = { Text("Region") },
                supportingContent = { Text(regionName(Mesh.region)) },
                modifier = Modifier.clickable { onDismiss(); onEditRegion() },
            )
            ListItem(
                leadingContent = { Icon(Icons.Outlined.Tune, null) },
                headlineContent = { Text("Node settings") },
                supportingContent = { Text(listOfNotNull(currentBoard()?.name, "role, radio, Bluetooth, firmware").joinToString(" · ")) },
                modifier = Modifier.clickable { onDismiss(); onNodeSettings() },
            )
            ListItem(
                leadingContent = { Icon(if (Mesh.transport == "Bluetooth") Icons.Rounded.Bluetooth else Icons.Rounded.Usb, null) },
                headlineContent = { Text("Your devices") },
                supportingContent = { Text("Connected over ${Mesh.transport} · tap to switch") },
                modifier = Modifier.clickable { onDismiss(); onDevices() },
            )
            ListItem(
                leadingContent = { Icon(Icons.AutoMirrored.Outlined.Logout, null, tint = MaterialTheme.colorScheme.error) },
                headlineContent = { Text("Disconnect", color = MaterialTheme.colorScheme.error) },
                modifier = Modifier.clickable { onDismiss(); Mesh.forget() },
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DevicesSheet(onDismiss: () -> Unit) {
    var adding by remember { mutableStateOf(false) }
    val askBluetooth = rememberBluetooth { adding = true }
    val switch = { key: String -> onDismiss(); Mesh.connect(key) }
    ModalBottomSheet(onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.padding(horizontal = 24.dp).padding(bottom = 24.dp).verticalScroll(rememberScrollState())) {
            Text(if (adding) "Add a device" else "Your devices", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
            if (adding) {
                Text(
                    "Turn the node on and keep it close. Devices you already have aren't listed.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                NearbyNodes { onDismiss(); Mesh.connectBle(it) }
                TextButton({ adding = false }, Modifier.padding(top = 8.dp)) { Text("Back to your devices") }
            } else {
                Spacer(Modifier.height(8.dp))
                DeviceRows(switch)
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedButton(askBluetooth, Modifier.weight(1f).height(52.dp)) {
                        Icon(Icons.Rounded.Add, null)
                        Spacer(Modifier.width(8.dp))
                        Text("Add device")
                    }
                    if (Mesh.saved.none { it.first == USB }) {
                        OutlinedButton({ switch(USB) }, Modifier.weight(1f).height(52.dp)) {
                            Icon(Icons.Rounded.Usb, null)
                            Spacer(Modifier.width(8.dp))
                            Text("USB cable")
                        }
                    }
                }
            }
        }
    }
}

fun scanQr(ctx: android.content.Context, onLink: (String) -> Unit) {
    val options = GmsBarcodeScannerOptions.Builder().setBarcodeFormats(Barcode.FORMAT_QR_CODE).build()
    GmsBarcodeScanning.getClient(ctx, options).startScan()
        .addOnSuccessListener { it.rawValue?.let(onLink) }
        .addOnFailureListener { Toast.makeText(ctx, "Couldn't open the QR scanner", Toast.LENGTH_SHORT).show() }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NewRoomSheet(onDismiss: () -> Unit, onOpen: (String) -> Unit) {
    val ctx = LocalContext.current
    var name by remember { mutableStateOf("") }
    var error by remember { mutableStateOf("") }
    val join = { link: String ->
        val (message, index) = Mesh.join(link)
        if (index != null) { onDismiss(); onOpen(convoKey(index, 0)) } else error = message
    }
    val create = {
        val index = Mesh.createRoom(name.trim())
        if (index != null) { onDismiss(); onOpen(convoKey(index, 0)) } else error = "Your node already has 8 rooms. Leave one first."
    }
    ModalBottomSheet(onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.padding(horizontal = 24.dp).padding(bottom = 24.dp).imePadding()) {
            Text("Create a room", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(4.dp))
            Text(
                "Private and encrypted. Only people you invite can read it.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    name, { name = it.replace(" ", "").fitBytes(11) }, Modifier.weight(1f),
                    label = { Text("Room name") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { if (name.isNotBlank()) create() }),
                )
                Spacer(Modifier.width(8.dp))
                FilledIconButton(create, Modifier.size(56.dp), enabled = name.isNotBlank() && Mesh.connected) {
                    Icon(Icons.Rounded.Add, "Create")
                }
            }
            Spacer(Modifier.height(28.dp))
            Text("Join a room", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(4.dp))
            Text(
                "Rooms are encrypted, so you can't browse them. Scan someone's invite QR code or paste their link.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton({ scanQr(ctx) { join(it) } }, Modifier.weight(1f).height(52.dp)) {
                    Icon(Icons.Outlined.QrCodeScanner, null)
                    Spacer(Modifier.width(8.dp))
                    Text("Scan QR")
                }
                OutlinedButton({
                    val clip = ctx.getSystemService(ClipboardManager::class.java).primaryClip
                    join(clip?.getItemAt(0)?.coerceToText(ctx)?.toString().orEmpty())
                }, Modifier.weight(1f).height(52.dp)) {
                    Icon(Icons.Outlined.ContentPaste, null)
                    Spacer(Modifier.width(8.dp))
                    Text("Paste link")
                }
            }
            if (error.isNotEmpty()) {
                Spacer(Modifier.height(12.dp))
                Text(error, color = MaterialTheme.colorScheme.error)
            }
        }
    }
}

@Composable
fun JoinDialog(link: String, onDone: (String?) -> Unit) {
    val names = inviteRooms(link).map { Msg(it).str(3).ifEmpty { "Public" } }
    if (names.isEmpty()) {
        AlertDialog({ onDone(null) }, { TextButton({ onDone(null) }) { Text("OK") } },
            title = { Text("Invalid invite") }, text = { Text("This link doesn't contain a Meshtastic room.") })
        return
    }
    AlertDialog(
        { onDone(null) },
        confirmButton = {
            TextButton({
                val (message, index) = Mesh.join(link)
                if (index == null) Toast.makeText(Mesh.appContext, message, Toast.LENGTH_LONG).show()
                onDone(index?.let { convoKey(it, 0) })
            }, enabled = Mesh.connected) { Text("Join") }
        },
        dismissButton = { TextButton({ onDone(null) }) { Text("Cancel") } },
        icon = { Icon(Icons.Rounded.PersonAdd, null) },
        title = { Text("Join ${names.joinToString(", ")}?") },
        text = { Text("You'll be able to read and write in this room.") },
    )
}

fun qrBitmap(text: String, size: Int = 640): ImageBitmap {
    val m = QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, size, size)
    val pixels = IntArray(size * size) { if (m[it % size, it / size]) android.graphics.Color.BLACK else android.graphics.Color.WHITE }
    return Bitmap.createBitmap(pixels, size, size, Bitmap.Config.ARGB_8888).asImageBitmap()
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InviteSheet(room: Room, onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    val link = remember(room) { Mesh.inviteLink(room) }
    val qr = remember(link) { qrBitmap(link) }
    ModalBottomSheet(onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp).padding(bottom = 24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text("Invite to ${room.title}", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(4.dp))
            Text(
                "Anyone who scans this can read and write in the room.",
                color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(16.dp))
            Image(qr, "Invite QR code", Modifier.size(260.dp).background(Color.White, RoundedCornerShape(16.dp)).padding(8.dp))
            Spacer(Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button({
                    ctx.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, link), null))
                }, Modifier.weight(1f).height(52.dp)) {
                    Icon(Icons.Outlined.Share, null)
                    Spacer(Modifier.width(8.dp))
                    Text("Share link")
                }
                OutlinedButton({
                    ctx.getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText("Invite", link))
                }, Modifier.weight(1f).height(52.dp)) {
                    Icon(Icons.Outlined.ContentCopy, null)
                    Spacer(Modifier.width(8.dp))
                    Text("Copy")
                }
            }
        }
    }
}

// ---------- Conversation ----------

private fun day(t: Long) = Instant.ofEpochMilli(t).atZone(ZoneId.systemDefault()).toLocalDate()

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConversationScreen(convo: String, onBack: () -> Unit, onProfile: (Long) -> Unit) {
    BackHandler(onBack = onBack)
    val peer = convo.removePrefix("dm:").takeIf { convo.startsWith("dm:") }?.toLong() ?: 0L
    val channel = convo.removePrefix("ch:").takeIf { convo.startsWith("ch:") }?.toInt() ?: 0
    val room = if (peer == 0L) Mesh.rooms[channel] else null
    val node = Mesh.nodes[peer]
    val now = rememberNow()
    var invite by remember { mutableStateOf(false) }
    var leave by remember { mutableStateOf(false) }
    val messages = Mesh.messages.filter { it.convo == convo }
    LaunchedEffect(messages.size) { Mesh.markRead(convo) }

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = { IconButton(onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back") } },
                title = {
                    if (peer != 0L) {
                        Box(Modifier.clickable { onProfile(peer) }) {
                            StatusTitle(node?.name ?: "!%08x".format(peer), node?.let { heard(it, now) } ?: "", node?.online(now) == true)
                        }
                    } else {
                        Column {
                            Text(room?.title ?: "Room", style = MaterialTheme.typography.titleMedium)
                            Text(
                                if (room?.public != false) "Public room, anyone nearby can read it" else "Private room",
                                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                },
                actions = {
                    if (peer != 0L && node != null) {
                        IconButton({ Mesh.setFavorite(peer, !node.favorite) }, enabled = Mesh.connected) {
                            Icon(if (node.favorite) Icons.Rounded.Star else Icons.Outlined.StarBorder, "Favorite")
                        }
                    }
                    if (room != null) IconButton({ invite = true }) { Icon(Icons.Rounded.QrCode2, "Invite") }
                    if (room != null && !room.primary) {
                        IconButton({ leave = true }) { Icon(Icons.AutoMirrored.Outlined.Logout, "Leave room") }
                    }
                },
            )
        },
        bottomBar = { Composer(if (peer != 0L) "Message ${node?.name ?: ""}" else "Message ${room?.title ?: ""}") { Mesh.send(it, channel, peer) } },
    ) { padding ->
        if (messages.isEmpty()) {
            EmptyState(
                padding, Icons.AutoMirrored.Outlined.Chat, "No messages yet",
                if (peer != 0L) "Messages here are end-to-end encrypted." else "Say hi! Everyone in this room will see it.",
            )
        } else {
            MessageList(padding, messages, showNames = peer == 0L)
        }
    }
    if (invite && room != null) InviteSheet(room) { invite = false }
    if (leave && room != null) {
        AlertDialog(
            { leave = false },
            confirmButton = { TextButton({ Mesh.leaveRoom(room.index); leave = false; onBack() }) { Text("Leave") } },
            dismissButton = { TextButton({ leave = false }) { Text("Cancel") } },
            title = { Text("Leave ${room.title}?") },
            text = { Text("You'll need a new invite to come back.") },
        )
    }
}

@Composable
fun MessageList(padding: PaddingValues, messages: List<Message>, showNames: Boolean) {
    val list = rememberLazyListState()
    LaunchedEffect(messages.size) { list.animateScrollToItem(messages.lastIndex) }
    LazyColumn(
        Modifier.fillMaxSize().padding(padding),
        state = list,
        contentPadding = PaddingValues(12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        itemsIndexed(messages) { i, m ->
            val prev = messages.getOrNull(i - 1)
            val newDay = prev == null || day(prev.time) != day(m.time)
            if (newDay) DayChip(m.time)
            Bubble(m, showName = showNames && (newDay || prev.from != m.from || prev.mine != m.mine))
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
fun Bubble(m: Message, showName: Boolean) {
    val time = remember(m.time) { DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(m.time)) }
    val failed = m.mine && m.status == "✗"
    Column(
        Modifier.fillMaxWidth().padding(top = if (showName) 6.dp else 0.dp),
        horizontalAlignment = if (m.mine) Alignment.End else Alignment.Start,
    ) {
        if (!m.mine && showName) {
            Text(
                Mesh.nodes[m.from]?.name ?: m.name,
                Modifier.padding(start = 4.dp, bottom = 2.dp),
                style = MaterialTheme.typography.labelMedium,
                color = nodeColor(m.from),
            )
        }
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = if (m.mine) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHigh,
            modifier = Modifier.widthIn(max = 300.dp).clickable(enabled = failed && Mesh.connected) { Mesh.retry(m) },
        ) {
            Column(Modifier.padding(start = 14.dp, end = 14.dp, top = 8.dp, bottom = 6.dp)) {
                SelectionContainer { Text(m.text, style = MaterialTheme.typography.bodyLarge) }
                Row(Modifier.align(Alignment.End), verticalAlignment = Alignment.CenterVertically) {
                    val faded = LocalContentColor.current.copy(alpha = 0.7f)
                    Text(time, style = MaterialTheme.typography.labelSmall, color = faded)
                    if (m.mine) {
                        Spacer(Modifier.width(4.dp))
                        val (icon, label) = when (m.status) {
                            "✓" -> Icons.Rounded.Done to if (m.peer != 0L) "Delivered" else "Relayed by the mesh"
                            "✗" -> Icons.Rounded.ErrorOutline to if (m.peer != 0L) "Not delivered" else "Not confirmed"
                            else -> Icons.Rounded.Schedule to "Sending"
                        }
                        Icon(icon, label, Modifier.size(14.dp), tint = faded)
                    }
                }
            }
        }
        if (failed) {
            Text(
                // Room messages have no delivery receipts, only "someone relayed it"; missing that isn't proof of failure.
                if (m.peer != 0L) "Not delivered · Tap to retry" else "Not confirmed · Tap to resend",
                Modifier.padding(top = 2.dp, end = 4.dp),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.error,
            )
        }
    }
}

@Composable
fun Composer(placeholder: String, onSend: (String) -> Unit) {
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
                placeholder = { Text(if (Mesh.connected) placeholder else "Waiting for the node…", maxLines = 1, overflow = TextOverflow.Ellipsis) },
                shape = RoundedCornerShape(28.dp),
                maxLines = 5,
                isError = tooLong,
                supportingText = if (tooLong) ({ Text("Too long for one mesh message") }) else null,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
            )
            Spacer(Modifier.width(8.dp))
            FilledIconButton({ onSend(text.trim()); text = "" }, Modifier.size(52.dp), enabled = canSend) {
                Icon(Icons.AutoMirrored.Rounded.Send, "Send")
            }
        }
    }
}
