package com.backmeupplz.meshtasticplus

import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import android.content.Intent
import android.provider.ContactsContract
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult
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
import androidx.compose.material.icons.outlined.Contacts
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
import androidx.compose.ui.platform.LocalContext
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
    s == 0xFFFFFFFFL -> string(R.string.always)
    s % 86400 == 0L -> plural(R.plurals.days, (s / 86400).toInt(), s / 86400)
    s % 3600 == 0L -> plural(R.plurals.hours, (s / 3600).toInt(), s / 3600)
    s % 60 == 0L -> plural(R.plurals.minutes, (s / 60).toInt(), s / 60)
    else -> plural(R.plurals.seconds, s.toInt(), s)
}

/** Interval choices; 0 means "firmware default" and is labeled with what that default is. */
private fun every(default: Long, vararg secs: Long) = listOf(0L to string(R.string.default_value, duration(default))) + secs.map { it to duration(it) }

private val BT_WARNING get() = "\n\n" + string(R.string.bt_warning)

/** A getter, so labels follow the phone's current language. */
val SETTINGS get() = listOf(
    Group(string(R.string.s_role), listOf(
        Setting(1, 1, string(R.string.s_role),
            string(R.string.s_what_job_this_node_does),
            options = listOf(0L to string(R.string.s_client), 1L to string(R.string.s_client_mute), 8L to string(R.string.s_client_hidden), 12L to string(R.string.s_client_base),
                2L to string(R.string.s_router), 11L to string(R.string.s_router_late), 5L to string(R.string.s_tracker), 6L to string(R.string.s_sensor), 9L to string(R.string.s_lost_and_found),
                7L to "TAK", 10L to string(R.string.s_tak_tracker))),
        Setting(1, 6, string(R.string.s_relaying),
            string(R.string.s_which_messages_this_node_passes),
            options = listOf(0L to string(R.string.s_everything), 1L to string(R.string.s_everything_without_reading), 2L to string(R.string.s_local_rooms_only),
                3L to string(R.string.s_known_nodes_only), 5L to string(R.string.s_standard_messages_only), 4L to string(R.string.s_nothing))),
        Setting(1, 7, string(R.string.s_announce_name_every),
            string(R.string.s_how_often_the_node_tells),
            options = every(10800, 3600, 21600, 43200, 86400)),
        Setting(1, 12, string(R.string.s_heartbeat_led),
            string(R.string.s_blinks_the_node_s_led),
            toggle = true, invert = true),
    )),
    Group(string(R.string.s_radio), listOf(
        Setting(6, 7, string(R.string.s_region),
            string(R.string.s_the_radio_band_it_has),
            options = REGIONS.map { it.first.toLong() to it.second }),
        Setting(6, 2, string(R.string.s_speed_vs_range),
            string(R.string.s_the_radio_preset_slower_presets),
            options = listOf(0L to string(R.string.s_long_range_fast), 7L to string(R.string.s_long_range_moderate), 9L to string(R.string.s_long_range_turbo),
                3L to string(R.string.s_medium_range_slow), 4L to string(R.string.s_medium_range_fast), 5L to string(R.string.s_short_range_slow),
                6L to string(R.string.s_short_range_fast), 8L to string(R.string.s_short_range_turbo)),
            also = 1 to 1L), // use_preset
        Setting(6, 8, string(R.string.s_max_hops),
            string(R.string.s_how_many_times_a_message),
            options = listOf(0L to string(R.string.default_hops, 3)) + (1L..7L).map { it to "$it" }),
        Setting(6, 10, string(R.string.s_transmit_power),
            string(R.string.s_how_strongly_the_node_transmits),
            options = listOf(0L to string(R.string.s_maximum_allowed), 10L to "10 dBm", 14L to "14 dBm", 17L to "17 dBm", 20L to "20 dBm",
                22L to "22 dBm", 27L to "27 dBm", 30L to "30 dBm")),
        Setting(6, 9, string(R.string.s_transmit),
            string(R.string.s_lets_the_radio_send_off), toggle = true),
        Setting(6, 13, string(R.string.s_boosted_receive),
            string(R.string.s_makes_the_receiver_a_bit),
            toggle = true),
        Setting(6, 105, string(R.string.s_ok_to_share_via_internet),
            string(R.string.s_lets_gateways_that_bridge_the),
            toggle = true),
        Setting(6, 104, string(R.string.s_ignore_internet_messages),
            string(R.string.s_ignores_messages_that_reached_the), toggle = true),
    )),
    Group(string(R.string.s_location), listOf(
        Setting(2, 13, string(R.string.gps),
            string(R.string.s_whether_the_node_uses_its),
            options = listOf(1L to string(R.string.s_on), 0L to string(R.string.s_off), 2L to string(R.string.s_not_present))),
        Setting(2, 1, string(R.string.s_share_location_every),
            string(R.string.s_how_often_the_node_broadcasts),
            options = every(3600, 300, 900, 1800, 3600, 10800, 21600, 43200)),
        Setting(2, 2, string(R.string.s_smart_sharing),
            string(R.string.s_also_shares_your_location_early),
            toggle = true),
        Setting(2, 5, string(R.string.s_check_gps_every),
            string(R.string.s_how_often_the_node_wakes),
            options = every(120, 30, 120, 300, 900, 3600)),
    )),
    Group(string(R.string.s_power), listOf(
        Setting(3, 1, string(R.string.s_power_saving),
            string(R.string.s_sleeps_deeply_between_radio_activity) + BT_WARNING,
            toggle = true),
        Setting(3, 2, string(R.string.s_turn_off_on_battery_after),
            string(R.string.s_powers_the_node_off_this),
            options = listOf(0L to string(R.string.s_never), 300L to duration(300), 3600L to duration(3600), 21600L to duration(21600), 86400L to duration(86400))),
    )),
    Group(string(R.string.s_screen), listOf(
        Setting(5, 1, string(R.string.s_screen_timeout),
            string(R.string.s_how_long_the_node_s),
            options = every(600, 15, 30, 60, 300, 600, 3600) + (0xFFFFFFFFL to string(R.string.s_always_on))),
        Setting(5, 5, string(R.string.s_flip_screen), string(R.string.s_turns_the_screen_upside_down), toggle = true),
        Setting(5, 6, string(R.string.s_units), string(R.string.s_distances_and_altitudes_on_the), options = listOf(0L to string(R.string.s_metric), 1L to string(R.string.s_imperial))),
        Setting(5, 12, string(R.string.s_12_hour_clock), string(R.string.s_shows_the_time_as_1), toggle = true),
    )),
    Group(string(R.string.bluetooth), listOf(
        Setting(7, 1, string(R.string.bluetooth),
            string(R.string.s_lets_phones_connect_to_the),
            toggle = true),
        Setting(7, 2, string(R.string.s_pairing),
            string(R.string.s_how_a_phone_proves_it) + BT_WARNING,
            options = listOf(0L to string(R.string.s_pin_on_screen), 1L to string(R.string.s_fixed_pin), 2L to string(R.string.s_no_pin))),
        Setting(7, 3, string(R.string.s_fixed_pin), string(R.string.s_the_6_digit_pin_phones), pin = true, visible = { get -> get(7, 2) == 1L }),
    )),
    Group(string(R.string.s_troubleshooting), listOf(
        Setting(8, 6, string(R.string.s_debug_log),
            string(R.string.s_streams_the_node_s_internal),
            toggle = true),
    )),
    Group(string(R.string.wifi), listOf(
        Setting(4, 1, string(R.string.wifi),
            string(R.string.s_connects_the_node_to_a),
            toggle = true, visible = { Mesh.hasWifi }),
        Setting(4, 3, string(R.string.s_network_name), string(R.string.s_the_wi_fi_network_ssid), text = true, visible = { Mesh.hasWifi }),
        Setting(4, 4, string(R.string.s_password), string(R.string.s_the_wi_fi_password), text = true, secret = true, visible = { Mesh.hasWifi }),
    )),
)

/** Firmware before 2.8.1 deadlocks halfway through a reset on boards with a screen (nested spiLock in MessageStore). */
fun resetFreezes() = Mesh.firmware.isNotEmpty() && isNewer("2.8.1", Mesh.firmware)

val RESET_FREEZE_NOTE get() = "\n\n" + string(R.string.reset_freeze_note)

class Reset(val kind: Int, val title: String, val summary: String, val details: String, val confirm: String)

private val RESETS get() = listOf(
    Reset(100, string(R.string.reset_clear_node_list), string(R.string.reset_forget_every_node_it_has),
        string(R.string.reset_your_node_forgets_every_other),
        string(R.string.reset_clear)),
    Reset(99, string(R.string.reset_reset_settings), string(R.string.reset_back_to_factory_settings_keeps),
        string(R.string.reset_every_setting_goes_back_to),
        string(R.string.reset_reset)),
    Reset(94, string(R.string.reset_factory_reset), string(R.string.reset_erase_everything_as_if_it),
        string(R.string.reset_erases_everything_on_the_node),
        string(R.string.reset_erase_everything)),
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
                navigationIcon = { IconButton(onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, stringResource(R.string.back)) } },
                title = { Text(stringResource(R.string.node_settings)) },
            )
        },
        bottomBar = {
            if (edits.isNotEmpty()) Surface(tonalElevation = 3.dp) {
                Row(Modifier.navigationBarsPadding().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    TextButton({ edits.clear() }) { Text(stringResource(R.string.undo)) }
                    Button(save, Modifier.weight(1f).height(56.dp), enabled = Mesh.connected) {
                        Icon(Icons.Rounded.Check, null)
                        Spacer(Modifier.width(10.dp))
                        Text(pluralStringResource(R.plurals.save_changes, edits.size, edits.size))
                    }
                }
            }
        },
    ) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(bottom = 24.dp)) {
            item { BoardCard(onUpdate) }
            if (Mesh.configs.isEmpty()) {
                item { Text(stringResource(R.string.waiting_for_settings), Modifier.padding(16.dp), color = MaterialTheme.colorScheme.onSurfaceVariant) }
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
                                    Icon(Icons.Outlined.Info, stringResource(R.string.about_setting, s.title), Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        },
                        supportingContent = if (s.toggle) null else ({
                            Text(
                                when {
                                    s.options != null -> s.options.firstOrNull { it.first == v }?.second ?: duration(v as Long)
                                    s.secret -> if ((v as String).isEmpty()) stringResource(R.string.not_set) else "••••••••"
                                    else -> v.toString().ifEmpty { stringResource(R.string.not_set) }
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
                header(string(R.string.reset_header))
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
        AlertDialog({ info = null }, { TextButton({ info = null }) { Text(stringResource(R.string.got_it)) } },
            icon = { Icon(Icons.Outlined.Info, null) }, title = { Text(s.title) },
            text = { Text(s.info, Modifier.verticalScroll(rememberScrollState())) })
    }
    resetting?.let { r ->
        AlertDialog(
            { resetting = null },
            confirmButton = { TextButton({ Mesh.reset(r.kind); resetting = null; onBack() }) { Text(r.confirm, color = MaterialTheme.colorScheme.error) } },
            dismissButton = { TextButton({ resetting = null }) { Text(stringResource(R.string.cancel)) } },
            icon = { Icon(Icons.Rounded.RestartAlt, null) },
            title = { Text(stringResource(R.string.confirm_title, r.title)) },
            text = { Text(r.details + if (r.kind != 100 && resetFreezes()) RESET_FREEZE_NOTE else "") },
        )
    }
    editing?.let { s -> EditDialog(s, value(s), { editing = null }) { set(s, it); editing = null } }
}

@Composable
fun EditDialog(s: Setting, current: Any, onDismiss: () -> Unit, onPick: (Any) -> Unit) {
    if (s.options != null) {
        AlertDialog(onDismiss, {}, dismissButton = { TextButton(onDismiss) { Text(stringResource(R.string.cancel)) } }, title = { Text(s.title) }, text = {
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
        { TextButton({ onPick(if (number) text.toLong() else text) }, enabled = ok) { Text(stringResource(R.string.ok)) } },
        dismissButton = { TextButton(onDismiss) { Text(stringResource(R.string.cancel)) } },
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
                    Text(board?.name ?: stringResource(R.string.unknown_board), style = MaterialTheme.typography.titleMedium)
                    Text(
                        listOfNotNull(board?.chip, Mesh.firmware.ifEmpty { null }?.let { stringResource(R.string.firmware_version, it) }).joinToString(" · ") +
                            "\n" + stringResource(R.string.node_id, "!%08x".format(Mesh.myNum)) + (Mesh.nodes[Mesh.myNum]?.power?.let { "\n$it" } ?: ""),
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
                            Text(stringResource(R.string.update_to, Updater.latest.substringBeforeLast('.')))
                        }
                        else -> TextButton(onUpdate) { // still reachable: pre-releases, reinstall
                            Icon(Icons.Rounded.Check, null, Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(stringResource(R.string.up_to_date))
                        }
                    }
                } else {
                    Text(
                        stringResource(R.string.update_from_computer),
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
    var chosen by remember { mutableStateOf<String?>(null) }
    val version = chosen ?: Updater.latest
    Scaffold(topBar = {
        TopAppBar(
            navigationIcon = { IconButton(onBack, enabled = !Updater.running) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, stringResource(R.string.back)) } },
            title = { Text(stringResource(R.string.firmware)) },
        )
    }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(24.dp)) {
            if (board == null) {
                Text(stringResource(R.string.which_board), style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(8.dp))
                boards.filter { it.canUpdateOverUsb }.sortedBy { it.name }.forEach { b ->
                    ListItem(headlineContent = { Text(b.name) }, modifier = Modifier.clickable { board = b })
                }
                return@Column
            }
            val b = board!!
            Text(b.name, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
            Text(
                if (rescue) stringResource(R.string.rescue_hint) else stringResource(R.string.installed_version, Mesh.firmware.ifEmpty { stringResource(R.string.unknown) }),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (Updater.versions.isEmpty() && Updater.error.isEmpty()) Text(stringResource(R.string.checking_versions), color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(24.dp))
            when {
                Updater.running || Updater.done -> {
                    Text(Updater.step, style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(12.dp))
                    if (Updater.progress < 0) LinearProgressIndicator(Modifier.fillMaxWidth())
                    else LinearProgressIndicator({ Updater.progress }, Modifier.fillMaxWidth())
                    Spacer(Modifier.height(12.dp))
                    if (Updater.done) Button(onBack, Modifier.fillMaxWidth().height(52.dp)) { Text(stringResource(R.string.done)) }
                    else Text(
                        stringResource(if (Mesh.transport == "Bluetooth") R.string.ble_keep_open else R.string.usb_keep_plugged),
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
                    if (Mesh.demo) {
                        Text(stringResource(R.string.demo_no_firmware), color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.height(16.dp))
                    } else if (!rescue && !onUsb && !onBle && !Updater.inBootloader) {
                        Text(stringResource(R.string.connect_first), color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.height(16.dp))
                    }
                    if (onBle || (Updater.inBootloader && Mesh.transport == "Bluetooth")) {
                        Text(
                            stringResource(R.string.ble_update_note),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.height(16.dp))
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(stringResource(R.string.include_prereleases))
                            Text(
                                stringResource(R.string.prereleases_hint),
                                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Switch(Updater.prerelease, { on ->
                            Updater.prerelease = on
                            Mesh.appContext.getSharedPreferences("mesh", 0).edit().putBoolean("fwAlpha", on).apply()
                            Updater.latest = ""
                            chosen = null
                            Updater.checkLatest()
                        })
                    }
                    Spacer(Modifier.height(16.dp))
                    Text(stringResource(R.string.version), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
                    Updater.versions.forEach { (v, pre) ->
                        Row(Modifier.fillMaxWidth().clickable { chosen = v }.padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                            RadioButton(v == version, { chosen = v })
                            Column {
                                Text(v)
                                val tags = listOfNotNull(
                                    stringResource(R.string.tag_newest).takeIf { v == Updater.latest }, stringResource(R.string.tag_prerelease).takeIf { pre },
                                    stringResource(R.string.tag_installed).takeIf { v == Mesh.firmware },
                                )
                                if (tags.isNotEmpty()) Text(tags.joinToString(" · "), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                    if (Mesh.firmware.isNotEmpty() && version.isNotEmpty() && isNewer(Mesh.firmware, version)) {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            stringResource(R.string.older_firmware_warning, version, Mesh.firmware),
                            color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall,
                        )
                    }
                    Spacer(Modifier.height(16.dp))
                    Text(
                        stringResource(R.string.firmware_stays),
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(16.dp))
                    Button(
                        {
                            Mesh.appContext.getSharedPreferences("mesh", 0).edit().putString("fwTarget", b.target).apply()
                            Updater.start(b, version, rescue)
                        },
                        Modifier.fillMaxWidth().height(56.dp),
                        enabled = !Mesh.demo && version.isNotEmpty() && (rescue || onUsb || onBle || Updater.inBootloader),
                    ) {
                        Icon(Icons.Rounded.SystemUpdate, null)
                        Spacer(Modifier.width(10.dp))
                        Text(
                            if (Updater.error.isNotEmpty()) stringResource(R.string.try_again)
                            else stringResource(if (version == Mesh.firmware) R.string.reinstall_version else R.string.install_version, version),
                        )
                    }
                }
            }
        }
    }
}

private val RELAY_PERMISSIONS = listOf(
    android.Manifest.permission.RECEIVE_SMS, android.Manifest.permission.SEND_SMS,
    android.Manifest.permission.READ_PHONE_STATE, android.Manifest.permission.READ_CALL_LOG, // caller numbers
)

/** Turns this phone into an SMS & call relay for one private room. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RelayScreen(onBack: () -> Unit) {
    BackHandler(onBack = onBack)
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val hasPhone = ctx.packageManager.hasSystemFeature(android.content.pm.PackageManager.FEATURE_TELEPHONY)
    val rooms = Mesh.rooms.values.filter { !it.public }.sortedBy { it.index }
    var error by remember { mutableStateOf("") }
    var consent by remember { mutableStateOf(false) }
    val ask = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.RequestMultiplePermissions(),
    ) {
        val granted = RELAY_PERMISSIONS.all { p -> ctx.checkSelfPermission(p) == android.content.pm.PackageManager.PERMISSION_GRANTED }
        if (granted) { error = ""; Relay.turn(true) } else error = string(R.string.relay_permissions_needed)
    }
    Scaffold(topBar = {
        TopAppBar(
            navigationIcon = { IconButton(onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, stringResource(R.string.back)) } },
            title = { Text(stringResource(R.string.sms_relay)) },
        )
    }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(24.dp)) {
            Text(
                stringResource(R.string.relay_intro),
            )
            if (!hasPhone) {
                Spacer(Modifier.height(16.dp))
                Text(stringResource(R.string.no_sms), color = MaterialTheme.colorScheme.error)
                return@Column
            }
            Spacer(Modifier.height(24.dp))
            Text(stringResource(R.string.room), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
            if (rooms.isEmpty()) Text(stringResource(R.string.create_relay_room_first), color = MaterialTheme.colorScheme.onSurfaceVariant)
            rooms.forEach { r ->
                Row(
                    Modifier.fillMaxWidth().clickable(enabled = !Relay.enabled) { Relay.useRoom(r.name) },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RadioButton(Relay.room == r.name, { Relay.useRoom(r.name) }, enabled = !Relay.enabled)
                    Text(r.title)
                }
            }
            if (rooms.none { it.name == "Relay" } && !Relay.enabled) {
                OutlinedButton({ Mesh.createRoom("Relay")?.let { Relay.useRoom("Relay") } }, enabled = Mesh.connected) {
                    Text(stringResource(R.string.create_relay_room))
                }
            }
            Spacer(Modifier.height(24.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.relay_sms_calls), style = MaterialTheme.typography.titleMedium)
                    Text(
                        if (Relay.enabled) stringResource(R.string.relay_on_status, Relay.room) else stringResource(R.string.off),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(
                    Relay.enabled,
                    { on -> if (on) consent = true else Relay.turn(false) },
                    enabled = Relay.enabled || (rooms.any { it.name == Relay.room } && Mesh.connected),
                )
            }
            // Play's prominent disclosure: say what the SMS and call log permissions are for right before Android asks.
            if (consent) AlertDialog(
                { consent = false },
                confirmButton = {
                    TextButton({
                        consent = false
                        ask.launch((RELAY_PERMISSIONS + android.Manifest.permission.POST_NOTIFICATIONS).toTypedArray())
                    }) { Text(stringResource(R.string.turn_on)) }
                },
                dismissButton = { TextButton({ consent = false }) { Text(stringResource(R.string.not_now)) } },
                title = { Text(stringResource(R.string.relay_consent_title)) },
                text = {
                    Text(
                        stringResource(R.string.relay_consent_body, Relay.room),
                    )
                },
            )
            if (error.isNotEmpty()) Text(error, color = MaterialTheme.colorScheme.error)
            Spacer(Modifier.height(16.dp))
            Surface(color = MaterialTheme.colorScheme.errorContainer, shape = RoundedCornerShape(16.dp)) {
                Text(
                    stringResource(R.string.relay_warning, Relay.room.ifEmpty { stringResource(R.string.the_relay_room) }),
                    Modifier.padding(16.dp), color = MaterialTheme.colorScheme.onErrorContainer,
                )
            }
            Spacer(Modifier.height(16.dp))
            Text(
                stringResource(R.string.relay_how_to, SMS_COMMAND),
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** Write an SMS that the room's relay phone sends: becomes "/sms <number> <text>". */
@Composable
fun SmsDialog(onDismiss: () -> Unit, onSend: (String) -> Unit) {
    var number by remember { mutableStateOf("") }
    var text by remember { mutableStateOf("") }
    val digits = dialable(number)
    val ctx = LocalContext.current
    // The system picker hands back just the chosen number, so no contacts permission is needed.
    val pick = rememberLauncherForActivityResult(StartActivityForResult()) { r ->
        val uri = r.data?.data ?: return@rememberLauncherForActivityResult
        ctx.contentResolver.query(uri, arrayOf(ContactsContract.CommonDataKinds.Phone.NUMBER), null, null, null)?.use {
            if (it.moveToFirst()) number = it.getString(0).orEmpty()
        }
    }
    val command = "$SMS_COMMAND $digits ${text.trim()}"
    val tooLong = command.toByteArray().size > 200 // one mesh message
    AlertDialog(
        onDismiss,
        confirmButton = { TextButton({ onSend(command) }, enabled = digits.count { it.isDigit() } >= 3 && text.isNotBlank() && !tooLong) { Text(stringResource(R.string.send)) } },
        dismissButton = { TextButton(onDismiss) { Text(stringResource(R.string.cancel)) } },
        title = { Text(stringResource(R.string.send_sms)) },
        text = {
            Column {
                Text(stringResource(R.string.relay_texts_from_own), color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(number, { number = it }, label = { Text(stringResource(R.string.phone_number)) }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                    trailingIcon = {
                        IconButton({ pick.launch(Intent(Intent.ACTION_PICK, ContactsContract.CommonDataKinds.Phone.CONTENT_URI)) }) {
                            Icon(Icons.Outlined.Contacts, stringResource(R.string.pick_contact))
                        }
                    })
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(text, { text = it }, label = { Text(stringResource(R.string.message)) }, maxLines = 5, isError = tooLong,
                    supportingText = if (tooLong) ({ Text(stringResource(R.string.too_long)) }) else null)
            }
        },
    )
}
