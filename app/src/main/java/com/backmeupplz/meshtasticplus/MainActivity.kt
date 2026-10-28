package com.backmeupplz.meshtasticplus

import androidx.compose.ui.res.stringResource
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
import android.net.Uri
import android.hardware.usb.UsbManager
import android.os.Build
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
import androidx.activity.result.contract.ActivityResultContracts.RequestPermission
import android.content.pm.PackageManager
import androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.foundation.text.modifiers.TextAutoSizeLayoutScope
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Chat
import androidx.compose.material.icons.automirrored.outlined.Logout
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.NavigateNext
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.outlined.Badge
import androidx.compose.material.icons.outlined.BatteryFull
import androidx.compose.material.icons.outlined.Block
import androidx.compose.material.icons.outlined.Feedback
import androidx.compose.material.icons.outlined.Flag
import androidx.compose.material.icons.outlined.VerifiedUser
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.ContentPaste
import androidx.compose.material.icons.outlined.Hub
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.NotificationsOff
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material.icons.outlined.QrCodeScanner
import androidx.compose.material.icons.outlined.Router
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.SignalCellularAlt
import androidx.compose.material.icons.outlined.Sms
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Bluetooth
import androidx.compose.material.icons.rounded.CellTower
import androidx.compose.material.icons.rounded.Check
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
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.takeOrElse
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import kotlinx.coroutines.delay
import java.text.DateFormat
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.Date
import android.telephony.PhoneNumberUtils
import java.util.Locale
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.IntOffset
import kotlin.math.roundToInt
import kotlinx.coroutines.launch
import androidx.compose.material.icons.automirrored.rounded.Reply
import androidx.compose.material.icons.automirrored.outlined.CallMissed
import androidx.compose.material.icons.outlined.Call

private val Green = Color(0xFF2E9E50)
private val Amber = Color(0xFFE8A400)

/** A meshtastic.org/e/ invite the app was opened with, waiting for the user to confirm. */
val pendingInvite = mutableStateOf<String?>(null)

/** Conversation a notification was tapped for. */
val pendingConvo = mutableStateOf<String?>(null)

/** The SMS relay screen is open (reached from Settings). */
val relayScreen = mutableStateOf(false)

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

    override fun onStart() {
        super.onStart()
        Mesh.visible = true
        Rating.used()
    }

    override fun onStop() {
        Mesh.visible = false
        super.onStop()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handle(intent)
    }

    private fun handle(intent: Intent?) {
        intent?.getStringExtra("convo")?.let { pendingConvo.value = it }
        when (intent?.action) {
            // Opened by plugging a node in: Android already granted USB access, so just connect.
            UsbManager.ACTION_USB_DEVICE_ATTACHED -> Mesh.usbAttached()
            Intent.ACTION_VIEW -> intent.dataString?.let { pendingInvite.value = it }
        }
    }
}

@Composable
fun App() {
    val activity = LocalContext.current as Activity
    var editingName by remember { mutableStateOf(false) }
    var editingRegion by remember { mutableStateOf(false) }
    var open by rememberSaveable { mutableStateOf<String?>(null) }
    var profile by rememberSaveable { mutableStateOf<Long?>(null) }
    var nodeSettings by rememberSaveable { mutableStateOf(false) }
    var firmware by rememberSaveable { mutableStateOf<Boolean?>(null) } // true = rescue a node stuck in update mode
    when {
        !Mesh.agreed -> TermsScreen()
        firmware != null -> FirmwareScreen(firmware!!) { firmware = null }
        editingName -> NameScreen(editing = true) { editingName = false }
        !Mesh.ready -> SetupScreen { firmware = true }
        Mesh.region == 0 || editingRegion -> RegionScreen(editing = editingRegion) { editingRegion = false }
        Mesh.askName -> NameScreen(editing = false) {}
        nodeSettings -> NodeSettingsScreen({ nodeSettings = false }) { firmware = false }
        relayScreen.value -> RelayScreen { relayScreen.value = false }
        open != null -> ConversationScreen(open!!, onBack = { open = null; Rating.maybeAsk(activity) }, onProfile = { profile = it })
        else -> HomeScreen(
            onOpen = { open = it },
            onProfile = { profile = it },
            onEditName = { editingName = true },
            onEditRegion = { editingRegion = true },
            onNodeSettings = { nodeSettings = true },
        )
    }
    // Messages arrive in the background now; ask once we're connected (Android 13+ needs the user's OK).
    // The connection notification was posted before permission existed and stays hidden until posted again.
    val notifications = rememberLauncherForActivityResult(RequestPermission()) { MeshService.refresh(Mesh.appContext) }
    LaunchedEffect(Mesh.ready) {
        if (Mesh.ready && Mesh.appContext.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            notifications.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
    LaunchedEffect(pendingConvo.value, Mesh.ready) {
        if (Mesh.ready) pendingConvo.value?.let { open = it; pendingConvo.value = null }
    }
    if (Mesh.ready) {
        profile?.let { num ->
            ProfileSheet(num, onDismiss = { profile = null }, onMessage = { profile = null; open = convoKey(0, num) })
        }
        pendingInvite.value?.let { link -> JoinDialog(link) { joined -> pendingInvite.value = null; joined?.let { open = it } } }
    }
}

// ---------- Terms: once, before anything else (chat apps need rules, blocking and reporting) ----------

const val SUPPORT = "support@borodutch.com"

@Composable
fun TermsScreen() {
    val ctx = LocalContext.current
    Surface(Modifier.fillMaxSize()) {
        Column(
            Modifier.fillMaxSize().systemBarsPadding().verticalScroll(rememberScrollState()).padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(56.dp))
            HeroIcon(Icons.Outlined.VerifiedUser)
            Spacer(Modifier.height(24.dp))
            Text(stringResource(R.string.terms_title), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center)
            Spacer(Modifier.height(8.dp))
            Text(
                stringResource(R.string.terms_body),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(16.dp))
            TextButton({ runCatching { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://meshplus.app/terms.html"))) } }) {
                Text(stringResource(R.string.terms_link))
            }
            Spacer(Modifier.height(24.dp))
            Button(Mesh::agree, Modifier.fillMaxWidth().height(56.dp)) {
                Icon(Icons.Rounded.Check, null)
                Spacer(Modifier.width(10.dp))
                Text(stringResource(R.string.agree))
            }
        }
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
    var removing by remember { mutableStateOf<Pair<String, String>?>(null) }
    removing?.let { (key, name) ->
        AlertDialog(
            { removing = null },
            confirmButton = { TextButton({ Mesh.forgetSaved(key); removing = null }) { Text(stringResource(R.string.remove)) } },
            dismissButton = { TextButton({ removing = null }) { Text(stringResource(R.string.cancel)) } },
            title = { Text(stringResource(R.string.remove_device_title, name.ifEmpty { stringResource(R.string.this_device) })) },
            text = {
                Text(
                    if (key == USB) stringResource(R.string.remove_device_usb_body)
                    else stringResource(R.string.remove_device_ble_body),
                )
            },
        )
    }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Mesh.saved.toList().forEach { (key, name) ->
            if (key != USB && !Mesh.canUseBluetooth()) return@forEach
            val current = key == Mesh.current
            DeviceRow(
                if (key == USB) Icons.Rounded.Usb else Icons.Rounded.Bluetooth,
                name.ifEmpty { key },
                if (current) Mesh.status else stringResource(if (key == USB) R.string.usb_cable else R.string.bluetooth),
                highlighted = current,
                onClick = { if (!current) onPick(key) },
            ) {
                if (current) Icon(Icons.Rounded.Check, stringResource(R.string.connected), Modifier.padding(end = 12.dp), tint = MaterialTheme.colorScheme.primary)
                else IconButton({ removing = key to name }) {
                    Icon(Icons.Rounded.Close, stringResource(R.string.forget_device), Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
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
            Text("Mesh+", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(8.dp))
            Text(
                stringResource(R.string.setup_subtitle),
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(40.dp))

            if (Mesh.active) {
                CircularProgressIndicator()
                Spacer(Modifier.height(16.dp))
                Text(Mesh.status, textAlign = TextAlign.Center, style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(16.dp))
                TextButton({ Mesh.forget() }) { Text(stringResource(R.string.cancel)) }
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
                Text(stringResource(R.string.your_devices), style = MaterialTheme.typography.titleMedium, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(8.dp))
                DeviceRows { Mesh.connect(it) }
                Spacer(Modifier.height(24.dp))
            }
            Button(askBluetooth, Modifier.fillMaxWidth().height(56.dp)) {
                Icon(Icons.Rounded.Bluetooth, null)
                Spacer(Modifier.width(10.dp))
                Text(if (Mesh.saved.isEmpty()) stringResource(R.string.connect_bluetooth) else stringResource(R.string.find_another_node))
            }
            Spacer(Modifier.height(12.dp))
            OutlinedButton({ scanning = false; Mesh.connectUsb() }, Modifier.fillMaxWidth().height(56.dp)) {
                Icon(Icons.Rounded.Usb, null)
                Spacer(Modifier.width(10.dp))
                Text(stringResource(R.string.connect_usb))
            }
            Spacer(Modifier.height(12.dp))
            Text(
                stringResource(R.string.usb_powers_node),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            if (scanning) NearbyNodes { scanning = false; Mesh.connectBle(it) }
            Spacer(Modifier.height(24.dp))
            TextButton({ scanning = false; Mesh.connectDemo() }) { Text(stringResource(R.string.try_demo)) }
            TextButton(onRescue) { Text(stringResource(R.string.node_stuck)) }
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
            Text(stringResource(R.string.nearby_nodes), style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
        }
        Spacer(Modifier.height(8.dp))
        // nRF52 and ESP32 nodes derive their node number from the Bluetooth address's last 4 bytes.
        val usbNum = if (Mesh.saved.any { it.first == USB }) Mesh.usbNum else 0L // only while it's still one of your devices
        fun isUsbNode(d: BluetoothDevice) = usbNum != 0L && d.address.replace(":", "").takeLast(8).toLongOrNull(16) == usbNum
        val fresh = found.values.filter { it.first.address !in known }.sortedByDescending { isUsbNode(it.first) }
        if (fresh.isEmpty()) {
            Text(
                stringResource(R.string.scanning_hint),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            fresh.forEach { (device, name) ->
                val subtitle = if (isUsbNode(device)) stringResource(R.string.your_usb_node) else stringResource(R.string.tap_to_connect)
                DeviceRow(Icons.Outlined.Router, name, subtitle, highlighted = isUsbNode(device), onClick = { onPick(device) }) {
                    Icon(Icons.AutoMirrored.Rounded.NavigateNext, null, Modifier.padding(end = 12.dp))
                }
            }
        }
    }
}

// ---------- Region: a fresh node stays silent until it knows which radio band is legal here ----------

/** Meshtastic RegionCode values (config.proto), minus the amateur-radio bands that need a license. */
val REGIONS get() = listOf(
    1 to string(R.string.region_us), 3 to string(R.string.region_eu_868), 2 to string(R.string.region_eu_433),
    6 to string(R.string.region_anz), 11 to string(R.string.region_nz_865), 22 to string(R.string.region_anz_433),
    26 to string(R.string.region_br), 4 to string(R.string.region_cn), 10 to string(R.string.region_in), 5 to string(R.string.region_jp), 24 to string(R.string.region_kz_863), 23 to string(R.string.region_kz_433),
    7 to string(R.string.region_kr), 17 to string(R.string.region_my_919), 16 to string(R.string.region_my_433), 25 to string(R.string.region_np), 21 to string(R.string.region_ph_915),
    20 to string(R.string.region_ph_868), 19 to string(R.string.region_ph_433), 9 to string(R.string.region_ru), 18 to string(R.string.region_sg), 8 to string(R.string.region_tw),
    12 to string(R.string.region_th), 15 to string(R.string.region_ua_868), 14 to string(R.string.region_ua_433), 13 to string(R.string.region_lora_24),
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

fun regionName(code: Int) = REGIONS.firstOrNull { it.first == code }?.second ?: string(R.string.not_set)

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
                    if (editing) TextButton(onDone) { Text(stringResource(R.string.cancel)) }
                    Button(
                        { if (picked != Mesh.region) Mesh.saveRegion(picked); onDone() },
                        Modifier.weight(1f).height(56.dp),
                        enabled = picked != 0 && Mesh.connected,
                    ) {
                        Icon(Icons.Rounded.Check, null)
                        Spacer(Modifier.width(10.dp))
                        Text(stringResource(R.string.use_region))
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
                    Text(stringResource(R.string.region_title), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(8.dp))
                    Text(
                        stringResource(R.string.region_body),
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
                if (editing) stringResource(R.string.your_name) else stringResource(R.string.name_title),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                stringResource(R.string.name_body),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(32.dp))
            OutlinedTextField(
                long,
                { long = it.fitBytes(39); if (!shortEdited) short = initials(long) },
                Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.name)) },
                leadingIcon = { Icon(Icons.Outlined.Person, null) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Next),
            )
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                short,
                { short = it.fitBytes(4); shortEdited = true },
                Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.short_name)) },
                leadingIcon = { Icon(Icons.Outlined.Badge, null) },
                supportingText = { Text(stringResource(R.string.short_name_hint)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { if (canSave) save() }),
            )
            Spacer(Modifier.height(24.dp))
            Button(save, Modifier.fillMaxWidth().height(56.dp), enabled = canSave) {
                Icon(Icons.Rounded.Check, null)
                Spacer(Modifier.width(10.dp))
                Text(stringResource(R.string.save))
            }
            TextButton({ if (!editing) Mesh.nameAsked(); onClose() }) { Text(if (editing) stringResource(R.string.cancel) else stringResource(R.string.skip_for_now)) }
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
                            Icon(Icons.Rounded.ExpandMore, stringResource(R.string.switch_device), Modifier.padding(start = 4.dp))
                        }
                    },
                    actions = { IconButton({ settings = true }) { Icon(Icons.Outlined.Settings, stringResource(R.string.settings)) } },
                )
                PrimaryTabRow(selectedTabIndex = tab) {
                    Tab(tab == 0, { tab = 0 }, text = { Text(stringResource(R.string.chats)) }, icon = {
                        BadgedBox({ if (unread > 0) Badge { Text("$unread") } }) { Icon(Icons.AutoMirrored.Outlined.Chat, null) }
                    })
                    Tab(tab == 1, { tab = 1 }, text = { Text(stringResource(R.string.nodes)) }, icon = {
                        BadgedBox({ if (online > 0) Badge(containerColor = Green, contentColor = Color.White) { Text("$online") } }) {
                            Icon(Icons.Outlined.Hub, null)
                        }
                    })
                }
            }
        },
        floatingActionButton = {
            if (tab == 0) ExtendedFloatingActionButton(text = { Text(stringResource(R.string.room)) }, icon = { Icon(Icons.Rounded.Add, null) }, onClick = { newRoom = true })
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
    val last = Mesh.messages.lastOrNull { it.convo == convo && relayLine(it.text) !is RelayLine.Status } // a send receipt marks its text, it isn't news
    val unread = Mesh.unread(convo)
    ListItem(
        leadingContent = { Icon(icon, null) },
        headlineContent = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(title, Modifier.weight(1f, fill = false), maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = if (unread > 0) FontWeight.SemiBold else null)
                if (Mesh.isMuted(convo)) {
                    Icon(Icons.Outlined.NotificationsOff, stringResource(R.string.muted), Modifier.padding(start = 6.dp).size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        },
        supportingContent = {
            Text(
                last?.let { relayLine(it.text)?.summary() ?: ((if (it.mine) stringResource(R.string.you) else it.name) + ": " + it.text) } ?: stringResource(R.string.no_messages),
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
        Mesh.nodes.values.filter { it.favorite && it.num != Mesh.myNum }.map { it.num }).distinct().filter { it !in Mesh.blocked }
    LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(bottom = 96.dp)) {
        header(string(R.string.rooms))
        items(rooms, key = { "room${it.index}" }) { r ->
            ConversationRow(if (r.public) Icons.Outlined.Public else Icons.Outlined.Lock, r.title, convoKey(r.index, 0), onOpen)
        }
        header(string(R.string.direct_messages))
        if (peers.isEmpty()) {
            item {
                Text(
                    stringResource(R.string.no_dms),
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
        return EmptyState(padding, Icons.Outlined.Hub, stringResource(R.string.no_nodes), stringResource(R.string.no_nodes_body))
    }
    val (favorites, others) = nodes.partition { it.favorite }
    LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(bottom = 8.dp)) {
        if (favorites.isNotEmpty()) {
            header(string(R.string.favorites))
            items(favorites, key = { "f${it.num}" }) { NodeRow(it, now, onProfile) }
            if (others.isNotEmpty()) header(string(R.string.everyone))
        }
        items(others, key = { it.num }) { NodeRow(it, now, onProfile) }
    }
}

fun heard(n: Node, now: Long) = when {
    n.lastHeard == 0L -> string(R.string.heard_never)
    now - n.lastHeard < 60_000 -> string(R.string.heard_now)
    else -> string(R.string.heard_ago, DateUtils.getRelativeTimeSpanString(n.lastHeard, now, DateUtils.MINUTE_IN_MILLIS))
}

fun hops(n: Node) = n.hops?.let { if (it == 0) string(R.string.hops_direct) else plural(R.plurals.hops_away, it, it) }

@Composable
fun NodeRow(n: Node, now: Long, onProfile: (Long) -> Unit) {
    ListItem(
        headlineContent = { Text(n.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        supportingContent = { Text(listOfNotNull(heard(n, now), hops(n)).joinToString(" · ")) },
        trailingContent = { StatusPill(n.online(now)) },
        modifier = Modifier.clickable { onProfile(n.num) },
    )
}

private val HalfButtonPadding = PaddingValues(horizontal = 16.dp)

// Wraps to a second line and shrinks before it truncates, so long translations fit half-width buttons on narrow phones
@Composable
private fun ButtonLabel(text: String) = Text(
    text, maxLines = 2, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center, lineHeight = 1.2.em,
    autoSize = WholeWords(LocalTextStyle.current.fontSize.takeOrElse { 14.sp }),
)

// Largest size (down to 10sp) whose layout neither ellipsizes nor breaks a line inside a word
private class WholeWords(val max: TextUnit) : TextAutoSize {
    override fun TextAutoSizeLayoutScope.getFontSize(constraints: Constraints, text: AnnotatedString): TextUnit {
        var size = max.value
        while (size > 10f) {
            val r = performLayout(constraints, text, size.sp)
            if (!r.isLineEllipsized(r.lineCount - 1) && (0 until r.lineCount - 1).all { r.getWordBoundary(r.getLineEnd(it)).start == r.getLineEnd(it) }) return size.sp
            size -= 0.5f
        }
        return 10.sp
    }
    override fun equals(other: Any?) = other is WholeWords && other.max == max
    override fun hashCode() = max.hashCode()
}

@Composable
fun StatusPill(online: Boolean) {
    val color = if (online) Green else MaterialTheme.colorScheme.outline
    Surface(shape = CircleShape, color = color.copy(alpha = 0.14f)) {
        Row(Modifier.padding(horizontal = 10.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(8.dp).background(color, CircleShape))
            Spacer(Modifier.width(6.dp))
            Text(if (online) stringResource(R.string.online) else stringResource(R.string.offline), style = MaterialTheme.typography.labelMedium, color = color)
        }
    }
}

// ---------- Sheets ----------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileSheet(num: Long, onDismiss: () -> Unit, onMessage: () -> Unit) {
    val n = Mesh.nodes[num] ?: Node(num)
    val now = rememberNow()
    val blocked = num in Mesh.blocked
    var block by remember { mutableStateOf(false) }
    if (block) BlockDialog(num) { block = false }
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
            n.snr?.let { Detail(Icons.Outlined.SignalCellularAlt, stringResource(R.string.signal_snr, "%.1f".format(it))) }
            n.power?.let { Detail(Icons.Outlined.BatteryFull, it) }
            Spacer(Modifier.height(24.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(onMessage, Modifier.weight(1f).height(52.dp), enabled = !blocked, contentPadding = HalfButtonPadding) {
                    Icon(Icons.AutoMirrored.Outlined.Chat, null)
                    Spacer(Modifier.width(8.dp))
                    ButtonLabel(stringResource(R.string.message))
                }
                OutlinedButton({ Mesh.setFavorite(num, !n.favorite) }, Modifier.weight(1f).height(52.dp), enabled = Mesh.connected, contentPadding = HalfButtonPadding) {
                    Icon(if (n.favorite) Icons.Rounded.Star else Icons.Outlined.StarBorder, null)
                    Spacer(Modifier.width(8.dp))
                    ButtonLabel(if (n.favorite) stringResource(R.string.favorited) else stringResource(R.string.favorite))
                }
            }
            Spacer(Modifier.height(8.dp))
            TextButton({ if (blocked) Mesh.setBlocked(num, false) else block = true }, Modifier.fillMaxWidth()) {
                val color = if (blocked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                Icon(Icons.Outlined.Block, null, tint = color)
                Spacer(Modifier.width(8.dp))
                Text(if (blocked) stringResource(R.string.unblock) else stringResource(R.string.block), color = color)
            }
        }
    }
}

@Composable
fun BlockDialog(num: Long, onDone: () -> Unit) {
    val name = (Mesh.nodes[num] ?: Node(num)).name
    AlertDialog(
        onDone,
        confirmButton = { TextButton({ Mesh.setBlocked(num, true); onDone() }) { Text(stringResource(R.string.block)) } },
        dismissButton = { TextButton(onDone) { Text(stringResource(R.string.cancel)) } },
        icon = { Icon(Icons.Outlined.Block, null) },
        title = { Text(stringResource(R.string.block_title, name)) },
        text = { Text(stringResource(R.string.block_body)) },
    )
}

/** The user's email app, addressed to us, with the message, who sent it and when. */
fun reportEmail(m: Message): Intent {
    val sender = "${(Mesh.nodes[m.from] ?: Node(m.from)).name} (${Node(m.from).id})"
    val where = if (m.peer != 0L) "Direct message" else Mesh.rooms[m.channel]?.title ?: "Room ${m.channel}"
    val body = "Reported message:\n${m.text}\n\nFrom: $sender\nSent: ${Instant.ofEpochMilli(m.time)}\nIn: $where\n"
    return Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:$SUPPORT?subject=${Uri.encode("Mesh+ report: $sender")}&body=${Uri.encode(body)}"))
}

@Composable
fun ReportDialog(m: Message, onDone: () -> Unit) {
    val ctx = LocalContext.current
    val sender = (Mesh.nodes[m.from] ?: Node(m.from)).name
    var alsoBlock by remember { mutableStateOf(true) }
    val noEmailApp = stringResource(R.string.no_email_app, SUPPORT)
    AlertDialog(
        onDone,
        confirmButton = {
            TextButton({
                runCatching { ctx.startActivity(reportEmail(m)) }
                    .onFailure { Toast.makeText(ctx, noEmailApp, Toast.LENGTH_LONG).show() }
                if (alsoBlock) Mesh.setBlocked(m.from, true)
                onDone()
            }) { Text(stringResource(R.string.report)) }
        },
        dismissButton = { TextButton(onDone) { Text(stringResource(R.string.cancel)) } },
        icon = { Icon(Icons.Outlined.Flag, null) },
        title = { Text(stringResource(R.string.report_title)) },
        text = {
            Column {
                Text(stringResource(R.string.report_body, SUPPORT))
                Row(Modifier.padding(top = 12.dp).clickable { alsoBlock = !alsoBlock }, verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(alsoBlock, { alsoBlock = it })
                    Text(stringResource(R.string.also_block, sender))
                }
            }
        },
    )
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
    val ctx = LocalContext.current
    var blockedList by remember { mutableStateOf(false) }
    if (blockedList) AlertDialog(
        { blockedList = false },
        confirmButton = { TextButton({ blockedList = false }) { Text(stringResource(R.string.done)) } },
        title = { Text(stringResource(R.string.blocked)) },
        text = {
            Column {
                if (Mesh.blocked.isEmpty()) Text(stringResource(R.string.no_one_blocked))
                Mesh.blocked.toList().forEach { num ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text((Mesh.nodes[num] ?: Node(num)).name, Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        TextButton({ Mesh.setBlocked(num, false) }) { Text(stringResource(R.string.unblock)) }
                    }
                }
            }
        },
    )
    ModalBottomSheet(onDismiss) {
        Column(Modifier.padding(bottom = 24.dp)) {
            ListItem(
                leadingContent = { Icon(Icons.Outlined.Person, null) },
                headlineContent = { Text(stringResource(R.string.name)) },
                supportingContent = { Text("${Mesh.myLong} (${Mesh.myShort})") },
                modifier = Modifier.clickable { onDismiss(); onEditName() },
            )
            ListItem(
                leadingContent = { Icon(Icons.Outlined.Public, null) },
                headlineContent = { Text(stringResource(R.string.region)) },
                supportingContent = { Text(regionName(Mesh.region)) },
                modifier = Modifier.clickable { onDismiss(); onEditRegion() },
            )
            ListItem(
                leadingContent = { Icon(Icons.Outlined.Tune, null) },
                headlineContent = { Text(stringResource(R.string.node_settings)) },
                supportingContent = { Text(listOfNotNull(currentBoard()?.name, stringResource(R.string.node_settings_summary)).joinToString(" · ")) },
                modifier = Modifier.clickable { onDismiss(); onNodeSettings() },
            )
            ListItem(
                leadingContent = { Icon(Icons.Outlined.Sms, null) },
                headlineContent = { Text(stringResource(R.string.sms_relay)) },
                supportingContent = { Text(if (Relay.enabled) stringResource(R.string.relay_on_summary, Relay.room) else stringResource(R.string.relay_off_summary)) },
                modifier = Modifier.clickable { onDismiss(); relayScreen.value = true },
            )
            ListItem(
                leadingContent = { Icon(if (Mesh.transport == "Bluetooth") Icons.Rounded.Bluetooth else Icons.Rounded.Usb, null) },
                headlineContent = { Text(stringResource(R.string.your_devices)) },
                supportingContent = { Text(stringResource(R.string.connected_over, transportName())) },
                modifier = Modifier.clickable { onDismiss(); onDevices() },
            )
            if (Mesh.blocked.isNotEmpty()) ListItem(
                leadingContent = { Icon(Icons.Outlined.Block, null) },
                headlineContent = { Text(stringResource(R.string.blocked)) },
                supportingContent = {
                    Text(stringResource(R.string.blocked_summary, Mesh.blocked.joinToString { (Mesh.nodes[it] ?: Node(it)).name }), maxLines = 1, overflow = TextOverflow.Ellipsis)
                },
                modifier = Modifier.clickable { blockedList = true },
            )
            ListItem(
                leadingContent = { Icon(Icons.Outlined.Feedback, null) },
                headlineContent = { Text(stringResource(R.string.send_feedback)) },
                supportingContent = { Text(SUPPORT) },
                modifier = Modifier.clickable {
                    val version = ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName
                    val body = "\n\nMesh+ $version on Android ${Build.VERSION.RELEASE}"
                    runCatching { ctx.startActivity(Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:$SUPPORT?subject=${Uri.encode("Mesh+ feedback")}&body=${Uri.encode(body)}"))) }
                },
            )
            ListItem(
                leadingContent = { Icon(Icons.AutoMirrored.Outlined.Logout, null, tint = MaterialTheme.colorScheme.error) },
                headlineContent = { Text(stringResource(R.string.disconnect), color = MaterialTheme.colorScheme.error) },
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
            Text(if (adding) stringResource(R.string.add_a_device) else stringResource(R.string.your_devices), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
            if (adding) {
                Text(
                    stringResource(R.string.add_device_hint),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                NearbyNodes { onDismiss(); Mesh.connectBle(it) }
                TextButton({ adding = false }, Modifier.padding(top = 8.dp)) { Text(stringResource(R.string.back_to_devices)) }
            } else {
                Spacer(Modifier.height(8.dp))
                DeviceRows(switch)
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedButton(askBluetooth, Modifier.weight(1f).height(52.dp), contentPadding = HalfButtonPadding) {
                        Icon(Icons.Rounded.Add, null)
                        Spacer(Modifier.width(8.dp))
                        ButtonLabel(stringResource(R.string.add_device))
                    }
                    if (Mesh.saved.none { it.first == USB }) {
                        OutlinedButton({ switch(USB) }, Modifier.weight(1f).height(52.dp), contentPadding = HalfButtonPadding) {
                            Icon(Icons.Rounded.Usb, null)
                            Spacer(Modifier.width(8.dp))
                            ButtonLabel(stringResource(R.string.usb_cable))
                        }
                    }
                }
            }
        }
    }
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
    val scan = rememberQrScanner { join(it) } // per flavor: src/play or src/fdroid
    val create = {
        val index = Mesh.createRoom(name.trim())
        if (index != null) { onDismiss(); onOpen(convoKey(index, 0)) } else error = string(R.string.rooms_full)
    }
    ModalBottomSheet(onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.padding(horizontal = 24.dp).padding(bottom = 24.dp).imePadding()) {
            Text(stringResource(R.string.create_room), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(4.dp))
            Text(
                stringResource(R.string.create_room_body),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    name, { name = it.replace(" ", "").fitBytes(11) }, Modifier.weight(1f),
                    label = { Text(stringResource(R.string.room_name)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { if (name.isNotBlank()) create() }),
                )
                Spacer(Modifier.width(8.dp))
                FilledIconButton(create, Modifier.size(56.dp), enabled = name.isNotBlank() && Mesh.connected) {
                    Icon(Icons.Rounded.Add, stringResource(R.string.create))
                }
            }
            Spacer(Modifier.height(28.dp))
            Text(stringResource(R.string.join_room), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(4.dp))
            Text(
                stringResource(R.string.join_room_body),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(scan, Modifier.weight(1f).height(52.dp), contentPadding = HalfButtonPadding) {
                    Icon(Icons.Outlined.QrCodeScanner, null)
                    Spacer(Modifier.width(8.dp))
                    ButtonLabel(stringResource(R.string.scan_qr))
                }
                OutlinedButton({
                    val clip = ctx.getSystemService(ClipboardManager::class.java).primaryClip
                    join(clip?.getItemAt(0)?.coerceToText(ctx)?.toString().orEmpty())
                }, Modifier.weight(1f).height(52.dp), contentPadding = HalfButtonPadding) {
                    Icon(Icons.Outlined.ContentPaste, null)
                    Spacer(Modifier.width(8.dp))
                    ButtonLabel(stringResource(R.string.paste_link))
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
    val names = inviteRooms(link).map { Msg(it).str(3).ifEmpty { stringResource(R.string.public_) } }
    if (names.isEmpty()) {
        AlertDialog({ onDone(null) }, { TextButton({ onDone(null) }) { Text(stringResource(R.string.ok)) } },
            title = { Text(stringResource(R.string.invalid_invite)) }, text = { Text(stringResource(R.string.invalid_invite_body)) })
        return
    }
    AlertDialog(
        { onDone(null) },
        confirmButton = {
            TextButton({
                val (message, index) = Mesh.join(link)
                if (index == null) Toast.makeText(Mesh.appContext, message, Toast.LENGTH_LONG).show()
                onDone(index?.let { convoKey(it, 0) })
            }, enabled = Mesh.connected) { Text(stringResource(R.string.join)) }
        },
        dismissButton = { TextButton({ onDone(null) }) { Text(stringResource(R.string.cancel)) } },
        icon = { Icon(Icons.Rounded.PersonAdd, null) },
        title = { Text(stringResource(R.string.join_rooms_title, names.joinToString(", "))) },
        text = { Text(stringResource(R.string.join_body)) },
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
            Text(stringResource(R.string.invite_title, room.title), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(4.dp))
            Text(
                stringResource(R.string.invite_body),
                color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(16.dp))
            Image(qr, stringResource(R.string.invite_qr), Modifier.size(260.dp).background(Color.White, RoundedCornerShape(16.dp)).padding(8.dp))
            Spacer(Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button({
                    ctx.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, link), null))
                }, Modifier.weight(1f).height(52.dp), contentPadding = HalfButtonPadding) {
                    Icon(Icons.Outlined.Share, null)
                    Spacer(Modifier.width(8.dp))
                    ButtonLabel(stringResource(R.string.share_link))
                }
                OutlinedButton({
                    ctx.getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText("Invite", link))
                }, Modifier.weight(1f).height(52.dp), contentPadding = HalfButtonPadding) {
                    Icon(Icons.Outlined.ContentCopy, null)
                    Spacer(Modifier.width(8.dp))
                    ButtonLabel(stringResource(R.string.copy))
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
    DisposableEffect(convo) {
        Mesh.openConvo = convo
        Notify.clear(convo)
        onDispose { Mesh.openConvo = null }
    }
    // A room someone's phone relays SMS into (or this phone's own relay room): offer to text through it.
    val ownRelay = room != null && Relay.enabled && channel == Relay.channel()
    val relayed = ownRelay || room != null && messages.any { m -> !m.mine && RELAY_PREFIXES.any { m.text.startsWith(it) } }
    var sms by remember { mutableStateOf(false) }
    var replyTo by rememberSaveable(convo) { mutableStateOf<String?>(null) }
    val send = { text: String -> Rating.sent(); if (ownRelay && text.startsWith("$SMS_COMMAND ")) Relay.sendOwn(text) else Mesh.send(text, channel, peer) }

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = { IconButton(onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, stringResource(R.string.back)) } },
                title = {
                    if (peer != 0L) {
                        Box(Modifier.clickable { onProfile(peer) }) {
                            StatusTitle(node?.name ?: "!%08x".format(peer), node?.let { heard(it, now) } ?: "", node?.online(now) == true)
                        }
                    } else {
                        Column {
                            Text(room?.title ?: stringResource(R.string.room), style = MaterialTheme.typography.titleMedium)
                            Text(
                                if (room?.public != false) stringResource(R.string.public_room) else stringResource(R.string.private_room),
                                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                },
                actions = {
                    val muted = Mesh.isMuted(convo)
                    IconButton({ Mesh.toggleMute(convo) }) {
                        Icon(if (muted) Icons.Outlined.NotificationsOff else Icons.Outlined.Notifications, if (muted) stringResource(R.string.unmute) else stringResource(R.string.mute))
                    }
                    if (peer != 0L && node != null) {
                        IconButton({ Mesh.setFavorite(peer, !node.favorite) }, enabled = Mesh.connected) {
                            Icon(if (node.favorite) Icons.Rounded.Star else Icons.Outlined.StarBorder, stringResource(R.string.favorite))
                        }
                    }
                    if (room != null) IconButton({ invite = true }) { Icon(Icons.Rounded.QrCode2, stringResource(R.string.invite)) }
                    if (room != null && !room.primary) {
                        IconButton({ leave = true }) { Icon(Icons.AutoMirrored.Outlined.Logout, stringResource(R.string.leave_room)) }
                    }
                },
            )
        },
        bottomBar = {
            Composer(
                stringResource(R.string.message_to, if (peer != 0L) node?.name.orEmpty() else room?.title.orEmpty()),
                replyTo = replyTo, onCancelReply = { replyTo = null },
                onNewSms = if (relayed) ({ sms = true }) else null,
            ) { send(it); replyTo = null }
        },
    ) { padding ->
        if (messages.isEmpty()) {
            EmptyState(
                padding, Icons.AutoMirrored.Outlined.Chat, stringResource(R.string.no_messages),
                if (peer != 0L) stringResource(R.string.dm_empty) else stringResource(R.string.room_empty),
            )
        } else {
            MessageList(padding, messages, showNames = peer == 0L, relay = relayed, onReply = { replyTo = it }, onResend = { m, command -> Mesh.remove(m); send(command) })
        }
    }
    if (invite && room != null) InviteSheet(room) { invite = false }
    if (sms) {
        // A new text to any number; answering a text is a swipe on its bubble.
        SmsDialog(onDismiss = { sms = false }) { command -> send(command); sms = false }
    }
    if (leave && room != null) {
        AlertDialog(
            { leave = false },
            confirmButton = { TextButton({ Mesh.leaveRoom(room.index); leave = false; onBack() }) { Text(stringResource(R.string.leave)) } },
            dismissButton = { TextButton({ leave = false }) { Text(stringResource(R.string.cancel)) } },
            title = { Text(stringResource(R.string.leave_room_title, room.title)) },
            text = { Text(stringResource(R.string.leave_room_body)) },
        )
    }
}

@Composable
fun MessageList(
    padding: PaddingValues, all: List<Message>, showNames: Boolean,
    relay: Boolean = false, onReply: (String) -> Unit = {}, onResend: (Message, String) -> Unit = { _, _ -> },
) {
    // In a relay room, "SMS to … sent" lines become the status of the text they answer instead of their own bubble.
    val (messages, lines, sent) = remember(all, relay) {
        val joined = joinParts(all)
        val lines = if (relay) joined.associate { it.id to relayLine(it.text) } else emptyMap()
        val sent = mutableMapOf<Long, Boolean>()
        val waiting = mutableListOf<Pair<Long, String>>()
        val hidden = mutableSetOf<Long>()
        for (m in joined) when (val l = lines[m.id]) {
            is RelayLine.Sms -> if (!l.incoming) waiting += m.id to dialable(l.number)
            // The newest open request to that number: an older one the relay never saw (sent while it was off,
            // or from an app that didn't relay) mustn't take the confirmation.
            is RelayLine.Status -> waiting.lastOrNull { it.second == dialable(l.number) }?.let {
                sent[it.first] = l.sent; waiting.remove(it); hidden += m.id
            }
            else -> {}
        }
        Triple(joined.filter { it.id !in hidden }, lines, sent)
    }
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
            when (val l = lines[m.id]) {
                is RelayLine.Sms -> SmsBubble(m, l, sent[m.id], onReply, onResend)
                is RelayLine.Call -> RelayChip(if (l.what == "Missed call") Icons.AutoMirrored.Outlined.CallMissed else Icons.Outlined.Call, l.summary(), m.time)
                is RelayLine.Status -> RelayChip(Icons.Outlined.Sms, stringResource(if (l.sent) R.string.sms_sent_to else R.string.sms_failed_to, prettyNumber(l.number)), m.time)
                is RelayLine.Info -> RelayChip(Icons.Outlined.Sms, l.summary(), m.time)
                null -> Bubble(m, showName = showNames && (newDay || prev.from != m.from || prev.mine != m.mine))
            }
        }
    }
}

/** How long a text request waits for the relay phone's receipt before it's offered for resending. */
const val RELAY_ANSWER_MS = 2 * 60_000L

/** A call or relay notice: small and centered, it's about the phone, not a message from someone. */
@Composable
fun RelayChip(icon: ImageVector, text: String, t: Long) {
    val time = remember(t) { DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(t)) }
    Box(Modifier.fillMaxWidth().padding(vertical = 4.dp), contentAlignment = Alignment.Center) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.width(6.dp))
            Text("$text · $time", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/**
 * A relayed text: texts from outside numbers on the start side, texts the room sends out on the end side. Swipe an incoming
 * text toward the start (left, or right in RTL languages) to answer it by SMS.
 */
@Composable
fun SmsBubble(m: Message, sms: RelayLine.Sms, sent: Boolean?, onReply: (String) -> Unit, onResend: (Message, String) -> Unit) {
    val time = remember(m.time) { DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(m.time)) }
    val number = dialable(sms.number)
    // Room messages carry no delivery receipt, so silence from the relay phone is the only sign a request got lost.
    val unanswered = !sms.incoming && sent == null && rememberNow() - m.time > RELAY_ANSWER_MS
    val problem = when {
        sent == false -> stringResource(R.string.relay_send_failed)
        unanswered -> stringResource(R.string.relay_no_answer)
        else -> null
    }
    val canResend = problem != null && m.mine && Mesh.connected
    val canReply = sms.incoming && number.count { it.isDigit() } >= 3 // not "Google" and other named senders
    val reach = with(LocalDensity.current) { 72.dp.toPx() }
    val drag = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    val haptics = LocalHapticFeedback.current
    val replyLabel = stringResource(R.string.reply_by_sms)
    // The swipe goes toward the start side: left, or right in Arabic and other right-to-left languages.
    val toStart = if (LocalLayoutDirection.current == LayoutDirection.Rtl) -1 else 1
    Column(Modifier.fillMaxWidth(), horizontalAlignment = if (sms.incoming) Alignment.Start else Alignment.End) {
        Text(
            if (sms.incoming) prettyNumber(sms.number) else stringResource(R.string.sms_to_number, prettyNumber(sms.number)) + if (m.mine) "" else " · ${Mesh.nodes[m.from]?.name ?: m.name}",
            Modifier.padding(start = 4.dp, end = 4.dp, top = 6.dp, bottom = 2.dp),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary,
        )
        Box(contentAlignment = Alignment.CenterEnd) {
            if (canReply && drag.value < 0f) {
                Icon(
                    Icons.AutoMirrored.Rounded.Reply, null,
                    Modifier.padding(end = 8.dp).size(24.dp).graphicsLayer { alpha = (-drag.value / reach).coerceIn(0f, 1f) },
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = if (sms.incoming) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.primary,
                modifier = Modifier.widthIn(max = 300.dp)
                    .clickable(enabled = canResend) { onResend(m, "$SMS_COMMAND $number ${sms.body}") }
                    .offset { IntOffset(drag.value.roundToInt(), 0) } // offset {} already mirrors in right-to-left layouts
                    .then(if (!canReply) Modifier else Modifier
                        .pointerInput(number) {
                            detectHorizontalDragGestures(
                                onDragEnd = {
                                    if (drag.value <= -reach) onReply(number)
                                    scope.launch { drag.animateTo(0f) }
                                },
                                onDragCancel = { scope.launch { drag.animateTo(0f) } },
                            ) { _, dx ->
                                val next = (drag.value + dx * toStart).coerceIn(-reach * 1.3f, 0f)
                                if (drag.value > -reach && next <= -reach) haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                scope.launch { drag.snapTo(next) }
                            }
                        }
                        .semantics { customActions = listOf(CustomAccessibilityAction(replyLabel) { onReply(number); true }) }),
            ) {
                Column(Modifier.padding(start = 14.dp, end = 14.dp, top = 8.dp, bottom = 6.dp)) {
                    SelectionContainer { Text(sms.body, style = MaterialTheme.typography.bodyLarge) }
                    Row(Modifier.align(Alignment.End), verticalAlignment = Alignment.CenterVertically) {
                        val faded = LocalContentColor.current.copy(alpha = 0.7f)
                        Text(time, style = MaterialTheme.typography.labelSmall, color = faded)
                        if (!sms.incoming) {
                            Spacer(Modifier.width(4.dp))
                            val (icon, label) = when {
                                sent == true -> Icons.Rounded.Done to stringResource(R.string.relay_sent)
                                problem != null -> Icons.Rounded.ErrorOutline to problem
                                else -> Icons.Rounded.Schedule to stringResource(R.string.relay_waiting)
                            }
                            Icon(icon, label, Modifier.size(14.dp), tint = faded)
                        }
                    }
                }
            }
        }
        if (problem != null) {
            Text(
                if (m.mine) stringResource(R.string.tap_to_resend, problem) else problem,
                Modifier.padding(top = 2.dp, end = 4.dp),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.error,
            )
        }
    }
}

@Composable
fun DayChip(t: Long) {
    val label = when (day(t)) {
        LocalDate.now() -> stringResource(R.string.today)
        LocalDate.now().minusDays(1) -> stringResource(R.string.yesterday)
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
    val ctx = LocalContext.current
    val time = remember(m.time) { DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(m.time)) }
    val failed = m.mine && m.status == "✗"
    var menu by remember { mutableStateOf(false) }
    var report by remember { mutableStateOf(false) }
    var block by remember { mutableStateOf(false) }
    if (report) ReportDialog(m) { report = false }
    if (block) BlockDialog(m.from) { block = false }
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
      Box {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = if (m.mine) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHigh,
            modifier = Modifier.widthIn(max = 300.dp)
                .combinedClickable(onLongClickLabel = stringResource(R.string.message_options), onLongClick = { menu = true }) { if (failed && Mesh.connected) Mesh.retry(m) },
        ) {
            Column(Modifier.padding(start = 14.dp, end = 14.dp, top = 8.dp, bottom = 6.dp)) {
                Text(m.text, style = MaterialTheme.typography.bodyLarge)
                Row(Modifier.align(Alignment.End), verticalAlignment = Alignment.CenterVertically) {
                    val faded = LocalContentColor.current.copy(alpha = 0.7f)
                    Text(time, style = MaterialTheme.typography.labelSmall, color = faded)
                    if (m.mine) {
                        Spacer(Modifier.width(4.dp))
                        val (icon, label) = when (m.status) {
                            "✓" -> Icons.Rounded.Done to if (m.peer != 0L) stringResource(R.string.delivered) else stringResource(R.string.relayed)
                            "✗" -> Icons.Rounded.ErrorOutline to if (m.peer != 0L) stringResource(R.string.not_delivered) else stringResource(R.string.not_confirmed)
                            else -> Icons.Rounded.Schedule to stringResource(R.string.sending)
                        }
                        Icon(icon, label, Modifier.size(14.dp), tint = faded)
                    }
                }
            }
        }
        DropdownMenu(menu, { menu = false }) {
            DropdownMenuItem({ Text(stringResource(R.string.copy)) }, {
                ctx.getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText("Message", m.text)); menu = false
            }, leadingIcon = { Icon(Icons.Outlined.ContentCopy, null) })
            if (!m.mine) {
                DropdownMenuItem({ Text(stringResource(R.string.report)) }, { report = true; menu = false }, leadingIcon = { Icon(Icons.Outlined.Flag, null) })
                DropdownMenuItem({ Text(stringResource(R.string.block_name, (Mesh.nodes[m.from] ?: Node(m.from)).name)) }, { block = true; menu = false },
                    leadingIcon = { Icon(Icons.Outlined.Block, null) })
            }
        }
      }
        if (failed) {
            Text(
                // Room messages have no delivery receipts, only "someone relayed it"; missing that isn't proof of failure.
                if (m.peer != 0L) stringResource(R.string.not_delivered_retry) else stringResource(R.string.not_confirmed_resend),
                Modifier.padding(top = 2.dp, end = 4.dp),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.error,
            )
        }
    }
}

/** [replyTo]: a phone number; what's typed then goes out as an SMS through the room's relay phone. */
@Composable
fun Composer(
    placeholder: String, replyTo: String? = null, onCancelReply: () -> Unit = {}, onNewSms: (() -> Unit)? = null,
    onSend: (String) -> Unit,
) {
    var text by rememberSaveable { mutableStateOf("") }
    val payload = if (replyTo != null) "$SMS_COMMAND $replyTo ${text.trim()}" else text.trim()
    val tooLong = payload.toByteArray().size > 200 // firmware text payload limit
    val canSend = Mesh.connected && text.isNotBlank() && !tooLong
    Surface(tonalElevation = 3.dp) {
      Column(Modifier.navigationBarsPadding().imePadding()) {
        if (replyTo != null) {
            Row(Modifier.padding(start = 20.dp, end = 8.dp, top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.AutoMirrored.Rounded.Reply, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.sms_to, prettyNumber(replyTo)), Modifier.weight(1f), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                IconButton(onCancelReply) { Icon(Icons.Rounded.Close, stringResource(R.string.cancel_sms_reply)) }
            }
        }
        Row(
            Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (onNewSms != null && replyTo == null) {
                IconButton(onNewSms, enabled = Mesh.connected) { Icon(Icons.Outlined.Sms, stringResource(R.string.text_phone_number)) }
                Spacer(Modifier.width(4.dp))
            }
            OutlinedTextField(
                text, { text = it }, Modifier.weight(1f),
                placeholder = {
                    Text(
                        when { !Mesh.connected -> stringResource(R.string.waiting_for_node); replyTo != null -> stringResource(R.string.text_number, prettyNumber(replyTo)); else -> placeholder },
                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                },
                shape = RoundedCornerShape(28.dp),
                maxLines = 5,
                isError = tooLong,
                supportingText = if (tooLong) ({ Text(stringResource(R.string.too_long)) }) else null,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
            )
            Spacer(Modifier.width(8.dp))
            FilledIconButton({ onSend(payload); text = "" }, Modifier.size(52.dp), enabled = canSend) {
                Icon(Icons.AutoMirrored.Rounded.Send, if (replyTo != null) stringResource(R.string.send_sms) else stringResource(R.string.send))
            }
        }
      }
    }
}
