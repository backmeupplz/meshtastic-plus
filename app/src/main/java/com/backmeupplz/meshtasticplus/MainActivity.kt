package com.backmeupplz.meshtasticplus

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Intent
import android.os.Bundle
import android.os.ParcelUuid
import android.text.format.DateUtils
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts.RequestMultiplePermissions
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.ui.Alignment
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import java.text.DateFormat

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        Mesh.init(this)
        setContent {
            val ctx = LocalContext.current
            MaterialTheme(if (isSystemInDarkTheme()) dynamicDarkColorScheme(ctx) else dynamicLightColorScheme(ctx)) {
                App()
            }
        }
    }
}

/** Trims to fit a protobuf string field of [max] UTF-8 bytes (firmware limits). */
fun String.fitBytes(max: Int): String {
    var s = this
    while (s.toByteArray().size > max) s = s.dropLast(1)
    return s
}

@SuppressLint("MissingPermission")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun App() {
    val ctx = LocalContext.current
    var scanning by remember { mutableStateOf(false) }
    var editingName by remember { mutableStateOf(false) }
    val askBluetooth = rememberLauncherForActivityResult(RequestMultiplePermissions()) { granted ->
        if (!granted.values.all { it }) return@rememberLauncherForActivityResult
        val adapter = ctx.getSystemService(BluetoothManager::class.java).adapter
        if (adapter?.isEnabled == true) scanning = true
        else ctx.startActivity(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE))
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(Mesh.myLong.ifEmpty { "Meshtastic+" })
                        Text(Mesh.status, style = MaterialTheme.typography.bodySmall)
                    }
                },
                actions = {
                    if (Mesh.connected) TextButton({ editingName = true }) { Text("Name") }
                    if (Mesh.active) {
                        TextButton({ Mesh.forget() }) { Text("Disconnect") }
                    } else {
                        TextButton({
                            askBluetooth.launch(arrayOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT))
                        }) { Text("Bluetooth") }
                        TextButton({ Mesh.connectUsb() }) { Text("USB") }
                    }
                },
            )
        },
        bottomBar = { Composer() },
    ) { padding ->
        val list = rememberLazyListState()
        LaunchedEffect(Mesh.messages.size) {
            if (Mesh.messages.isNotEmpty()) list.animateScrollToItem(Mesh.messages.size - 1)
        }
        if (Mesh.messages.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(padding).padding(32.dp), contentAlignment = Alignment.Center) {
                Text("Connect your node over Bluetooth or a USB cable, then say hi to the mesh.")
            }
        }
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            state = list,
            contentPadding = PaddingValues(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(Mesh.messages) { Bubble(it) }
        }
    }

    if (scanning) ScanDialog { scanning = false }
    if (editingName) NameDialog { editingName = false }
}

@Composable
fun Bubble(m: Message) {
    val time = remember(m.time) {
        DateUtils.formatSameDayTime(m.time, System.currentTimeMillis(), DateFormat.SHORT, DateFormat.SHORT).toString()
    }
    Column(Modifier.fillMaxWidth(), horizontalAlignment = if (m.mine) Alignment.End else Alignment.Start) {
        if (!m.mine) {
            Text(m.name + if (m.dm) " · DM" else "", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
        }
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = if (m.mine) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
            modifier = Modifier.widthIn(max = 300.dp),
        ) {
            SelectionContainer { Text(m.text, Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) }
        }
        Text("$time ${m.status}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
fun Composer() {
    var text by rememberSaveable { mutableStateOf("") }
    val tooLong = text.toByteArray().size > 200 // firmware text payload limit
    Row(Modifier.navigationBarsPadding().imePadding().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
        OutlinedTextField(
            text, { text = it }, Modifier.weight(1f),
            enabled = Mesh.connected,
            isError = tooLong,
            maxLines = 4,
            placeholder = { Text(if (Mesh.connected) "Message" else "Not connected") },
        )
        Spacer(Modifier.width(8.dp))
        Button({ Mesh.send(text.trim()); text = "" }, enabled = Mesh.connected && text.isNotBlank() && !tooLong) {
            Text("Send")
        }
    }
}

@SuppressLint("MissingPermission")
@Composable
fun ScanDialog(onDismiss: () -> Unit) {
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
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Nearby nodes") },
        text = {
            Column {
                if (found.isEmpty()) Text("Searching… make sure the node is on and not connected to another phone.")
                found.values.forEach { (device, name) ->
                    Text(name, Modifier.fillMaxWidth().clickable { Mesh.connectBle(device); onDismiss() }.padding(vertical = 14.dp))
                }
            }
        },
        confirmButton = { TextButton(onDismiss) { Text("Cancel") } },
    )
}

@Composable
fun NameDialog(onDismiss: () -> Unit) {
    var long by remember { mutableStateOf(Mesh.myLong) }
    var short by remember { mutableStateOf(Mesh.myShort) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Your identity") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(long, { long = it.fitBytes(39) }, label = { Text("Name") }, singleLine = true)
                OutlinedTextField(short, { short = it.fitBytes(4) }, label = { Text("Short name (up to 4)") }, singleLine = true)
            }
        },
        confirmButton = {
            TextButton({ Mesh.setOwner(long.trim(), short.trim()); onDismiss() }, enabled = long.isNotBlank() && short.isNotBlank()) {
                Text("Save")
            }
        },
        dismissButton = { TextButton(onDismiss) { Text("Cancel") } },
    )
}
