# Meshtastic+

A tiny Android app for Meshtastic: connect to your node, send and receive messages. That's it.

- **Bluetooth**: tap *Bluetooth*, pick your node, and enter the PIN shown on the node's screen the first time. The app reconnects to the same node by itself on launch and when it comes back in range.
- **USB-C cable**: plug the node into the phone and tap *USB*. The phone powers the node over the cable (USB OTG), so you don't need a battery.
- **Identity**: tap *Name* to set the long and short name your node shows to the mesh.

Messages go to the primary channel. Incoming direct messages are shown with a `DM` tag. `✓` means another node relayed your message and `✗` means nobody did. History is saved on the phone.

Speaks the Meshtastic BLE and serial APIs from firmware 2.x (Heltec V3 uses a CP2102 USB chip; newer boards use native USB). Requires Android 12+.

## Build

```sh
./gradlew assembleRelease   # app/build/outputs/apk/release/app-release.apk (~2 MB)
adb install app/build/outputs/apk/release/app-release.apk
```

The protocol code is a hand-rolled minimal protobuf codec (`Proto.kt`), so there's no protobuf toolchain. `./gradlew test` checks it.
