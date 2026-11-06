# Mesh+

Mesh+ is a small, open-source Android app for [Meshtastic](https://meshtastic.org) radios and an independent alternative to the official Meshtastic app: you connect your node over Bluetooth or a USB-C cable, chat with people nearby in rooms and direct messages, and keep the node set up and its firmware up to date from your phone. Download it at **[meshplus.app](https://meshplus.app)**. How it differs from the official Meshtastic app: [meshplus.app/compare.html](https://meshplus.app/compare.html).

1. **Connect**: plug the node into your phone with a USB-C cable (the app opens by itself and the phone powers the node), or tap *Connect with Bluetooth*, pick your node and type the PIN from its screen.
2. **Region**: a brand-new node stays silent until it knows its radio band, so the app asks once (pre-selected from your phone's country).
3. **Name**: pick how you show up on the mesh; initials become your short name. Change it later with the pencil.
4. **Chats**: rooms and direct messages in one list with unread badges. *Public* is the open channel everyone nearby shares. Tap **+ Room** to create a private, encrypted room and invite people with a QR code or link, or to join one by scanning or pasting an invite (links from the official Meshtastic apps work too). DMs are end-to-end encrypted by the firmware. A check under your message means another node relayed it (rooms) or the recipient got it (DMs).
5. **Nodes**: everyone your radio has heard, favorites first, with online/offline badges (online = heard in the last 2 hours). Tap one for their profile (last heard, hops, signal, battery) to message or favorite them; favorites are stored on your node.
6. **Settings** (gear): name, region, disconnect / switch node. Nodes you've used over Bluetooth are listed on the connect screen for one-tap reconnecting.
7. **Node settings**: which board the node is (e.g. Heltec Mesh Node T114, nRF52840) and its firmware, plus role, relaying, radio preset, hops, transmit power, location sharing, power saving, screen and Bluetooth pairing. Every setting has an ⓘ that explains it. Changes are saved together and the node restarts once. At the bottom: clear the node list, reset settings (keeps identity and pairing) or a full factory reset (re-pair with the new PIN afterwards).
8. **Firmware updates** (nRF52 boards over USB): when a newer stable release exists, Node settings shows *Update to x.y.z* (otherwise *Up to date*). *Firmware options* lists every version Meshtastic publishes for the board (pre-releases optional), so you can also pick a specific one or go back. It downloads the latest stable release for that exact board, reboots the node into its bootloader and installs it over the cable (~1.5 min). Settings, rooms and messages stay. If an update is interrupted, *Node stuck in update mode?* on the connect screen finishes it. ESP32 boards: use flasher.meshtastic.org.
9. **Background & notifications**: while connected, Mesh+ keeps running in the background (a "Connected to …" notification shows it) and notifies you of new messages.
10. **SMS relay** (Settings → SMS relay): one phone with a SIM relays the texts and calls it receives into a private room, and people in that room can send SMS through it (`/sms +15551234567 message`, or the SMS button Mesh+ shows in relay rooms). Anyone with the room's invite can read those texts and send SMS from the relay's number, so invite carefully.

The node restarts when its region or name changes; the app reconnects by itself. History is saved on the phone.

Speaks the Meshtastic BLE and serial APIs from firmware 2.x (tested on a Heltec Mesh Node T114 over USB; ESP32 boards with CP210x/CH340 USB chips like the Heltec V3 use the same path but are untested). Requires Android 12+.

**Demo mode**: *No radio yet? Try the demo* on the connect screen runs the app against a simulated node (rooms, DMs with replies, nodes, settings), for trying it without hardware and for app store review.

**iPhone**: `ios/` is a native SwiftUI version with the same features over Bluetooth, minus the SMS relay, USB and firmware updates. It adds Block and Report on messages and nodes, as App Review requires for chat apps. `ios/release.sh <version> <build>` builds it and uploads it to App Store Connect, `ios/screenshots.sh` retakes the App Store screenshots from the demo node in every language (see AGENTS.md), and the iOS workflow compiles it and its tests on every change (run the unit and demo UI tests locally).

## Build

```sh
./gradlew assemblePlayRelease   # app/build/outputs/apk/play/release/app-play-release.apk (fdroid flavor: no Google code)
adb install app/build/outputs/apk/play/release/app-play-release.apk
```

The protocol code is a hand-rolled minimal protobuf codec (`Proto.kt`), so there's no protobuf toolchain. `./gradlew test` checks it.

The landing page lives in `docs/` and is served by GitHub Pages at meshplus.app.

## How it compares

Checked on November 6, 2026, against the official app's README and Google Play listing (the details are on [meshplus.app/compare.html](https://meshplus.app/compare.html)).

- The **official Meshtastic app** is made by the Meshtastic project and does the most: maps, telemetry and full device configuration. Mesh+ talks to the same radios on firmware 2.x, so you can switch between the two at any time and keep the official app for what Mesh+ leaves out.
- **Mesh+** is built to make chatting simple. Setup takes a few steps, the public channel, private rooms and direct messages share one list, every node setting is explained in plain words, and a demo mode lets you try it without a radio. It also has an SMS relay and installs firmware on nRF52 radios from the phone. The app is about 3 MB and needs Android 12 or newer.
- **MeshCore** is a separate mesh firmware with its own protocol and its own companion apps, so it isn't an app for Meshtastic radios. A radio runs either Meshtastic or MeshCore firmware, and Mesh+ works only with Meshtastic.

## FAQ

**What's the easiest app for using a Meshtastic radio on Android?** Mesh+ aims to be. You plug the radio into your phone with a USB-C cable (the phone powers it) or pair it over Bluetooth with the PIN from its screen, pick your region and a name, and you're chatting in about a minute. If you need maps or telemetry, the official Meshtastic app has them.

**How do I update the firmware on a Meshtastic node?** If the radio has an nRF52 chip, such as the Heltec Mesh Node T114 or the RAK4631, open Node settings in Mesh+ and tap *Update* or *Firmware options*. Over a USB-C cable the update takes about a minute and a half; over Bluetooth it takes about ten minutes, and the app has to stay open near the node. Your settings, rooms and messages stay on the node. Radios with an ESP32 chip, like the Heltec V3, are updated from a computer at [flasher.meshtastic.org](https://flasher.meshtastic.org). The full walkthrough is at [meshplus.app/update-meshtastic-firmware.html](https://meshplus.app/update-meshtastic-firmware.html).

**Are there alternatives to the official Meshtastic app?** Yes. Mesh+ is a free, MIT-licensed alternative for Android, available on [Google Play](https://play.google.com/store/apps/details?id=com.borodutch.meshplus), [F-Droid](https://f-droid.org/packages/com.borodutch.meshplus/) and as an [APK](https://github.com/backmeupplz/meshtastic-plus/releases/latest/download/meshplus.apk), and it works with the same radios and channels as the official app, including room invites made in the official apps.

**Is Mesh+ an official Meshtastic app?** No. It's an independent project made by Nikita Kolmogorov and isn't affiliated with or endorsed by Meshtastic LLC.

Mesh+ is an independent project, not affiliated with Meshtastic LLC. Meshtastic® is a registered trademark of Meshtastic LLC.

Releasing: see `AGENTS.md`.

MIT licensed, see `LICENSE`.
