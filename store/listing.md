# Mesh+ — Google Play submission kit

Package: `com.borodutch.meshplus` · Bundle: `app/build/outputs/bundle/playRelease/app-play-release.aab` (signed with the upload key in `~/.android/meshplus-upload.jks`; use **Play App Signing**).

## Store listing

**App name** (≤30): `Mesh+ for Meshtastic`

**Short description** (≤80): `Chat over Meshtastic radios with no internet or cell service. Simple, private.`

**Full description** (≤4000):

Mesh+ is a small, friendly app for Meshtastic radios. Pair your radio over Bluetooth or plug it in with a USB-C cable, and you can chat with people nearby across the mesh without internet or cell service anywhere along the way.

Set up in a minute. Pick your radio, type the PIN from its screen, choose your region and a name, and you're ready to chat.

Rooms and direct messages. The public channel, private rooms you share with a QR code or link, and end-to-end encrypted direct messages all live in one list with unread badges. Mute the ones you don't want notifications from.

Stays connected. Mesh+ keeps running in the background while you're connected and notifies you of new messages.

Settings that explain themselves. Every radio setting has a short note on what it does and when you'd change it, from radio presets and hop limits to Bluetooth pairing, power saving and the screen.

Firmware updates from your phone. nRF52 radios such as the Heltec T114 and RAK4631 update over a USB cable in about a minute, or over Bluetooth. Pick the newest release, a pre-release, or any earlier version.

All your radios in one place. Switch between your radios from the top bar and see which board each one is, the firmware it runs and its battery voltage.

SMS relay (optional). Turn one phone with cell signal into a relay: the texts and calls it receives show up in a private room, and people in that room can send texts through it. Off unless you turn it on.

No radio yet? Try the built-in demo.

Mesh+ has no accounts, ads or analytics, and your messages stay on your phone. It's an independent open-source project and isn't affiliated with or endorsed by Meshtastic LLC. Meshtastic® is a registered trademark of Meshtastic LLC.

**Category**: Communication · **Contact email**: (yours) · **Website**: https://meshplus.app · **Privacy policy**: https://meshplus.app/privacy.html

**Graphics**: `fastlane/metadata/android/en-US/images/` (icon.png, featureGraphic.png, phoneScreenshots/*.png; shared with F-Droid).

## App access (for reviewers)

"All functionality is available without login. The app talks to a Meshtastic LoRa radio; reviewers without one can tap **No radio yet? Try the demo** on the first screen, which simulates a connected radio (rooms, direct messages with replies, nodes, all settings). Firmware installs and the SMS relay need real hardware / a SIM."

## Ads: No ads.

## Content rating questionnaire
Category: Communication / messaging. User-to-user communication: **Yes** (users can message other users over the radio mesh; no moderation, no accounts). Shares user location: **No** (the app doesn't read the phone's location; radios may broadcast their own GPS position if the user's radio has GPS). Violence/sexual content/gambling/drugs: **No**.

## Target audience: 18+ (or 13+). Not designed for children.

## Data safety
- Data collected: **None** (nothing is sent to the developer or any server).
- Data shared: **None** with third parties. Note for the SMS relay: SMS and call data are sent *at the user's direction* to people in a private mesh room the user chose — Google's definition treats user-initiated transfers as not "sharing".
- Encrypted in transit: Yes (mesh room/DM encryption). Users can delete data: Yes (uninstall / clear app data; history is on-device only).
- Internet use: only to fetch the firmware version list (api.meshtastic.org) and firmware files (GitHub), without personal data.

## Permissions declaration (SMS & Call Log)
Core functionality: **Connected device companion / SMS relay** — "Mesh+ is a companion app for Meshtastic LoRa radios. Its optional SMS relay lets a user whose phone has cell service forward the SMS and incoming calls their phone receives to their own private, encrypted radio-mesh group, and send SMS that members of that group request — e.g. for a group off-grid where only one phone has signal. The feature is off by default, enabled explicitly by the user in Settings → SMS relay with an on-screen explanation, shows a persistent notification while active, and can be turned off at any time. RECEIVE_SMS: forward incoming texts to the radio group. SEND_SMS: send texts group members ask for. READ_CALL_LOG + READ_PHONE_STATE: get the caller number of incoming calls to post 'Incoming/Missed call from …'. No SMS or call data is sent to the developer or any server." Video demo may be requested: record the relay screen being enabled and an SMS arriving in the room.

Foreground service declaration (connectedDevice): "Keeps the Bluetooth/USB connection to the user's Meshtastic radio alive so messages arrive while the app is in the background; started when the user connects a radio, stopped when they disconnect; a notification shows while running."

## Note on testers
New *personal* developer accounts must run a closed test with at least 12 testers for 14 days before production access. Organization accounts are exempt.
