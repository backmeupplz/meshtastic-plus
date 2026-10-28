# python3 scripts/shots/android.py <apk> <out dir> [language ...]   (run from the repo root)
# The Play store shots from the demo node, in the order of `## Play screenshot captions`: the chat list, the SMS room with
# relayed texts and a missed call, the send-SMS dialog, the SMS relay settings, a room, the nodes list and node settings,
# with the app in each language (per-app locale, Android 13+), each from a fresh start, to
# <out dir>/<language>/<n>-<name>.png. Drives the emulator in ANDROID_SERIAL with adb and uiautomator, finding controls by
# the app's own strings in app/src/main/res/values-*/strings.xml. Text that isn't ASCII is typed with ADB Keyboard
# (https://github.com/senzhk/ADBKeyBoard, its keyboardservice-debug.apk in ADB_KEYBOARD_APK), since `input text` only
# takes ASCII; the emulator needs a hardware keyboard (hw.keyboard=yes) to hide the stock one for the shots. Languages
# default to every one the app has. Run it once per device size (phone, 7" and 10" tablet) into
# <raw dir>/{phone,tab7,tab10} for compose.py, from the play-flavor release APK (the app Play ships).
import base64, os, re, shutil, subprocess, sys, time
import xml.etree.ElementTree as ET

APP = 'com.borodutch.meshplus'
RES = 'app/src/main/res'
IME = 'com.android.adbkeyboard/.AdbIME'
apk, out = sys.argv[1], os.path.abspath(sys.argv[2])
# app language -> values folder
FOLDERS = {'es-419': 'b+es+419', 'zh-Hans': 'b+zh+Hans', 'zh-Hant': 'b+zh+Hant', 'pt-BR': 'pt-rBR', 'pt-PT': 'pt-rPT', 'en-GB': 'en-rGB'}
ALL = ['en'] + sorted({next((k for k, v in FOLDERS.items() if v == d[7:]), d[7:]) for d in os.listdir(RES)
                       if d.startswith('values-') and os.path.exists(os.path.join(RES, d, 'strings.xml'))} - {'in'})
languages = sys.argv[3:] or ALL
# What the send-SMS dialog shows being typed
DRAFT = {
    'en': 'Running late, see you at the trailhead at 9!', 'en-GB': 'Running late, see you at the trailhead at 9!',
    'de': 'Bin spät dran, wir sehen uns um 9 am Wanderparkplatz!', 'es': '¡Voy tarde, nos vemos al inicio del sendero a las 9!',
    'es-419': '¡Voy tarde, nos vemos al inicio del sendero a las 9!', 'fr': 'Je suis en retard, rendez-vous au départ du sentier à 9 h !',
    'it': "Sono in ritardo, ci vediamo all'inizio del sentiero alle 9!", 'nl': 'Ik ben laat, tot 9 uur bij het begin van het pad!',
    'pl': 'Spóźnię się, widzimy się o 9 na początku szlaku!', 'pt-BR': 'Estou atrasado, te vejo no início da trilha às 9!',
    'pt-PT': 'Estou atrasado, vemo-nos no início do trilho às 9!', 'ru': 'Опаздываю, увидимся у начала тропы в 9!',
    'uk': 'Запізнююся, побачимося на початку стежки о 9!', 'sv': 'Jag är sen, vi ses vid ledens start klockan 9!',
    'tr': "Geç kalıyorum, saat 9'da patikanın başında görüşürüz!", 'ar': 'سأتأخر قليلًا، نلتقي عند بداية الدرب في التاسعة!',
    'ja': '少し遅れるね、9時に登山口で会おう！', 'ko': '좀 늦어, 9시에 등산로 입구에서 보자!', 'zh-Hans': '我晚点到，9点步道入口见！',
    'zh-Hant': '我晚點到，9點步道入口見！', 'hi': 'थोड़ी देर हो रही है, 9 बजे ट्रेल की शुरुआत पर मिलते हैं!',
    'id': 'Aku telat, sampai ketemu di awal jalur jam 9!', 'vi': 'Mình đến muộn, gặp nhau ở đầu đường mòn lúc 9 giờ nhé!',
    'th': 'ไปสายหน่อยนะ เจอกันที่ทางเข้าเส้นทางตอน 9 โมง!',
}
NUMBER = '+14155550199'


def strings(folder):
    path = os.path.join(RES, folder, 'strings.xml')
    return {e.get('name'): (e.text or '').replace("\\'", "'").replace('\\"', '"') for e in ET.parse(path).getroot().iter('string')} if os.path.exists(path) else {}


def channel_name(s):
    """A room name as the demo node has it: no spaces, at most 11 bytes (Demo.kt)"""
    s = s.replace(' ', '')
    while len(s.encode()) > 11:
        s = s[:-1]
    return s


def adb(*args, check=True):
    return subprocess.run(['adb', *args], check=check, capture_output=True).stdout


def nodes():
    for _ in range(5):
        xml = adb('exec-out', 'uiautomator', 'dump', '/dev/tty', check=False).decode('utf8', 'replace')
        xml = xml[xml.find('<?xml'):xml.rfind('>') + 1]
        try:
            return list(ET.fromstring(xml).iter('node'))
        except ET.ParseError:
            time.sleep(1)
    return []


def find(match, timeout=60, last=False, folder=None):
    """The first (or with last, the lowest) on-screen node whose text, content description or hint passes match"""
    deadline = time.time() + timeout
    while time.time() < deadline:
        found = nodes()
        if error_dialog(found):  # a slow emulator's "isn't responding", or System UI's "keeps stopping"
            continue
        hits = [n for n in found if any(match(n.get(a) or '') for a in ('text', 'content-desc', 'hint')) and n.get('bounds') != '[0,0][0,0]']
        if hits:
            return hits[-1] if last else hits[0]
        time.sleep(1)
    open(os.path.join(folder or out, 'zz-fail.png'), 'wb').write(adb('exec-out', 'screencap', '-p'))
    print('\n'.join(f"{n.get('text')!r} {n.get('content-desc')!r} {n.get('bounds')}" for n in nodes() if n.get('text') or n.get('content-desc')))
    raise SystemExit(f'nothing on screen matches {match.__doc__}')


def error_dialog(found):
    """Taps away a "keeps stopping" or "isn't responding" dialog among the dumped nodes, waiting out an ANR and closing a
    crash, and returns its title ('' when there's none)"""
    buttons = {n.get('resource-id'): n for n in found if (n.get('resource-id') or '').startswith('android:id/aerr_')}
    if not buttons:
        return ''
    tap(next(buttons[b] for b in ('android:id/aerr_wait', 'android:id/aerr_close', *buttons) if b in buttons))
    time.sleep(1)
    return next((n.get('text') for n in found if n.get('resource-id') == 'android:id/alertTitle'), None) or 'error dialog'


def tap(n):
    x1, y1, x2, y2 = map(int, re.findall(r'\d+', n.get('bounds')))
    adb('shell', 'input', 'tap', str((x1 + x2) // 2), str((y1 + y2) // 2))


def exactly(s):
    m = lambda v: v == s
    m.__doc__ = repr(s)
    return m


def containing(s):
    m = lambda v: s in v
    m.__doc__ = f'... {s!r} ...'
    return m


def type_text(field, value):
    """Types value into field and checks it arrived (a busy emulator drops keystrokes)"""
    for _ in range(3):
        tap(field)
        time.sleep(0.5)
        adb('shell', 'am', 'broadcast', '-a', 'ADB_CLEAR_TEXT')
        adb('shell', 'am', 'broadcast', '-a', 'ADB_INPUT_B64', '--es', 'msg', base64.b64encode(value.encode()).decode())
        deadline = time.time() + 10  # a loaded emulator can take seconds to show the text
        while time.time() < deadline:
            time.sleep(1)
            if any((n.get('text') or '') == value for n in nodes()):
                return
    raise SystemExit(f'could not type {value!r}')


def status_bar_problems():
    """What keeps the status bar from being the demo one: 9:41, one full wifi and one full cell icon, a full battery that
    isn't charging and no notification icons. System UI can drop demo mode (a loaded tablet did in most languages). Only
    the dump with --windows has the status bar, in the system's English whatever the app's language"""
    for _ in range(5):  # a busy emulator's uiautomator sometimes returns nothing
        xml = adb('exec-out', 'uiautomator', 'dump', '--windows', '/dev/tty', check=False).decode('utf8', 'replace')
        try:
            found = list(ET.fromstring(xml[xml.find('<?xml'):xml.rfind('>') + 1]).iter('node'))
            break
        except ET.ParseError:
            time.sleep(1)
    else:
        return ['no window dump']
    ids = lambda name: [n for n in found if n.get('resource-id') == 'com.android.systemui:id/' + name]
    problems = [f"clock {n.get('text')!r}" for n in ids('clock') if not (n.get('text') or '').startswith('9:41')]
    problems += [] if ids('clock') else ['no clock']
    problems += [f'{len(ids(icon))} {icon}' for icon in ('wifi_signal', 'mobile_signal') if len(ids(icon)) != 1]
    problems += [f'battery {d!r}' for d in [n.get('content-desc') for n in ids('battery')] if d != 'Battery 100 percent.']
    problems += [f"icon {n.get('content-desc')!r}" for area in ids('notificationIcons') for n in list(area.iter('node'))[1:]]
    # System UI crashes on a loaded tablet (and loses demo mode), and its dialog stays over the app until it's tapped away
    problems += [f'dialog {title!r}'] if (title := error_dialog(found)) else []
    return problems


def shot(folder, name):
    time.sleep(2)  # let animations settle
    for attempt in range(4):
        problems = status_bar_problems()
        if not problems:
            png = adb('exec-out', 'screencap', '-p')
            problems = status_bar_problems()  # and still the demo one after the capture
            if not problems:
                open(os.path.join(folder, name + '.png'), 'wb').write(png)
                return
        print(name, 'status bar:', ', '.join(problems), flush=True)
        if attempt == 3:
            break
        adb('shell', 'am', 'broadcast', '-a', 'com.android.systemui.demo', '-e', 'command', 'exit')
        time.sleep(2)
        demo_status_bar()
        time.sleep(5)
    raise SystemExit(f'{name}: the status bar is not the demo one ({", ".join(problems)})')


def use_ime(adb_keyboard):
    """ADB Keyboard to type, or the stock one hidden behind the emulator's hardware keyboard, so the shots show none"""
    adb('shell', 'settings', 'put', 'secure', 'show_ime_with_hard_keyboard', '1' if adb_keyboard else '0')
    ime = IME if adb_keyboard else next(i for i in adb('shell', 'ime', 'list', '-s').decode().split() if i != IME and 'voice' not in i.lower())
    for _ in range(10):  # a fresh install takes a moment to show up as an input method
        adb('shell', 'ime', 'enable', ime, check=False)
        adb('shell', 'ime', 'set', ime, check=False)
        if adb('shell', 'settings', 'get', 'secure', 'default_input_method').decode().strip() == ime:
            return
        time.sleep(3)
    raise SystemExit(f'{ime} did not become the input method')


def demo_status_bar():
    adb('shell', 'settings', 'put', 'global', 'sysui_demo_allowed', '1')
    for extra in (['clock', '-e', 'hhmm', '0941'], ['battery', '-e', 'level', '100', '-e', 'plugged', 'false'],
                  ['network', '-e', 'wifi', 'show', '-e', 'level', '4'], ['network', '-e', 'mobile', 'show', '-e', 'level', '4', '-e', 'datatype', 'none'],
                  ['notifications', '-e', 'visible', 'false']):
        adb('shell', 'am', 'broadcast', '-a', 'com.android.systemui.demo', '-e', 'command', 'enter')
        adb('shell', 'am', 'broadcast', '-a', 'com.android.systemui.demo', '-e', 'command', *extra)


def walk(language):
    folder = os.path.join(out, language)
    shutil.rmtree(folder, ignore_errors=True)  # a set comes from one run, never half an old one
    os.makedirs(folder)
    t = strings('values') | (strings('values-' + FOLDERS.get(language, language)) if language != 'en' else {})
    adb('shell', 'pm', 'clear', APP)  # a fresh start: terms, demo and relay off
    for p in ('RECEIVE_SMS', 'SEND_SMS', 'READ_PHONE_STATE', 'READ_CALL_LOG', 'POST_NOTIFICATIONS'):
        adb('shell', 'pm', 'grant', APP, 'android.permission.' + p, check=False)
    adb('shell', 'cmd', 'locale', 'set-app-locales', APP, '--locales', language)
    adb('shell', 'monkey', '-p', APP, '-c', 'android.intent.category.LAUNCHER', '1')
    f = lambda m, **k: find(m, folder=folder, **k)
    demo_status_bar()

    tap(f(exactly(t['agree']), timeout=180))  # a busy emulator can sit on the splash screen for a while
    tap(f(exactly(t['try_demo'])))
    hiking, sms = channel_name(t['demo_room']), channel_name(t['demo_sms_room'])
    f(exactly(sms))
    time.sleep(3)  # the demo's history comes in after the rooms
    shot(folder, '1-chats')

    tap(f(exactly(sms)))
    f(containing(t['demo_sms_4'][:12]))
    shot(folder, '2-relay')
    tap(f(exactly(t['text_phone_number'])))
    number = f(exactly(t['phone_number']))
    use_ime(True)
    type_text(number, NUMBER)
    type_text(f(exactly(t['message'])), DRAFT[language])
    use_ime(False)
    time.sleep(1)
    shot(folder, '3-sms')
    tap(f(exactly(t['cancel'])))
    adb('shell', 'input', 'keyevent', '4')  # back to the list (the keyboard is gone with the dialog)
    time.sleep(1)
    if any(n.get('content-desc') == t['back'] for n in nodes()):
        adb('shell', 'input', 'keyevent', '4')

    tap(f(exactly(hiking)))
    f(containing(t['demo_history_4'][:12]))
    shot(folder, '5-room')
    adb('shell', 'input', 'keyevent', '4')

    tap(f(exactly(t['nodes'])))
    f(containing(t['demo_maya'][:8]))
    shot(folder, '6-nodes')

    tap(f(exactly(t['settings'])))
    tap(f(exactly(t['node_settings'])))
    f(containing('T114'))
    shot(folder, '7-settings')
    adb('shell', 'input', 'keyevent', '4')

    tap(f(exactly(t['settings'])))
    tap(f(exactly(t['sms_relay'])))
    tap(f(exactly(sms), last=True))
    f(exactly(t['relay_sms_calls']))
    tap([n for n in nodes() if n.get('checkable') == 'true' and not n.get('class', '').endswith('RadioButton')][-1])  # the switch
    tap(f(exactly(t['turn_on'])))
    f(containing(t['relay_on_status'].split('%')[0].strip()[:10]))
    adb('shell', 'input', 'swipe', '500', '600', '500', '1400', '300')  # back to the top
    shot(folder, '4-relay-settings')


adb('install', '-r', os.environ['ADB_KEYBOARD_APK'])
use_ime(True)
# The app takes its colors from the system (Material You): seed them with the brand green, dark, no animations
PALETTE = '{"android.theme.customization.system_palette":"FF67EA94","android.theme.customization.accent_color":"FF67EA94",' \
          '"android.theme.customization.color_source":"preset","android.theme.customization.theme_style":"TONAL_SPOT"}'
if adb('shell', 'settings', 'get', 'secure', 'theme_customization_overlay_packages').decode().strip() != PALETTE:
    adb('shell', 'settings', 'put', 'secure', 'theme_customization_overlay_packages', f"'{PALETTE}'")
    time.sleep(10)  # System UI applies it
adb('shell', 'cmd', 'uimode', 'night', 'yes')
for scale in ('window_animation_scale', 'transition_animation_scale', 'animator_duration_scale'):
    adb('shell', 'settings', 'put', 'global', scale, '0')
adb('shell', 'settings', 'put', 'global', 'heads_up_notifications_enabled', '0')  # demo replies notify
# Tablets pin the launcher's taskbar over every app: without a launcher (Settings' fallback home) there's none until the end
LAUNCHER = 'com.google.android.apps.nexuslauncher'
tablet = int(adb('shell', 'wm', 'size').split(b'x')[0].split()[-1]) >= 1200
if tablet:
    adb('shell', 'pm', 'disable-user', '--user', '0', LAUNCHER, check=False)
adb('install', '-r', apk)
try:
    for language in languages:
        walk(language)
        print(language, ' '.join(sorted(os.listdir(os.path.join(out, language)))), flush=True)
finally:
    if tablet:
        adb('shell', 'pm', 'enable', '--user', '0', LAUNCHER, check=False)
