# Mesh+

A tiny Android app for [Meshtastic](https://meshtastic.org) radios: connect to your node, send and receive messages, keep it set up and updated. Download it at **[meshplus.app](https://meshplus.app)**.

1. **Connect**: plug the node into your phone with a USB-C cable (the app opens by itself and the phone powers the node), or tap *Connect with Bluetooth*, pick your node and type the PIN from its screen.
2. **Region**: a brand-new node stays silent until it knows its radio band, so the app asks once (pre-selected from your phone's country).
3. **Name**: pick how you show up on the mesh; initials become your short name. Change it later with the pencil.
4. **Chats**: rooms and direct messages in one list with unread badges. *Public* is the open channel everyone nearby shares. Tap **+ Room** to create a private, encrypted room and invite people with a QR code or link, or to join one by scanning or pasting an invite (links from the official Meshtastic apps work too). DMs are end-to-end encrypted by the firmware. A check under your message means another node relayed it (rooms) or the recipient got it (DMs).
5. **Nodes**: everyone your radio has heard, favorites first, with online/offline badges (online = heard in the last 2 hours). Tap one for their profile (last heard, hops, signal, battery) to message or favorite them; favorites are stored on your node.
6. **Settings** (gear): name, region, disconnect / switch node. Nodes you've used over Bluetooth are listed on the connect screen for one-tap reconnecting.
7. **Node settings**: which board the node is (e.g. Heltec Mesh Node T114, nRF52840) and its firmware, plus role, relaying, radio preset, hops, transmit power, location sharing, power saving, screen and Bluetooth pairing. Every setting has an ⓘ that explains it. Changes are saved together and the node restarts once. At the bottom: clear the node list, reset settings (keeps identity and pairing) or a full factory reset (re-pair with the new PIN afterwards).
8. **Firmware updates** (nRF52 boards over USB): when a newer stable release exists, Node settings shows *Update to x.y.z* (otherwise *Up to date*). It downloads the latest stable release for that exact board, reboots the node into its bootloader and installs it over the cable (~1.5 min). Settings, rooms and messages stay. If an update is interrupted, *Node stuck in update mode?* on the connect screen finishes it. ESP32 boards: use flasher.meshtastic.org.

The node restarts when its region or name changes; the app reconnects by itself. History is saved on the phone.

Speaks the Meshtastic BLE and serial APIs from firmware 2.x (tested on a Heltec Mesh Node T114 over USB; ESP32 boards with CP210x/CH340 USB chips like the Heltec V3 use the same path but are untested). Requires Android 12+.

## Build

```sh
./gradlew assembleRelease   # app/build/outputs/apk/release/app-release.apk (~2 MB)
adb install app/build/outputs/apk/release/app-release.apk
```

The protocol code is a hand-rolled minimal protobuf codec (`Proto.kt`), so there's no protobuf toolchain. `./gradlew test` checks it.

The landing page lives in `docs/` and is served by GitHub Pages at meshplus.app.

Mesh+ is an independent project, not affiliated with Meshtastic LLC. Meshtastic® is a registered trademark of Meshtastic LLC.
