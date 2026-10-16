# Mesh+ — Google Play submission kit

Package: `com.borodutch.meshplus` · Bundle: `app/build/outputs/bundle/playRelease/app-play-release.aab` (signed with the upload key in `~/.android/meshplus-upload.jks`; use **Play App Signing**).

## Store listing

**App name** (≤30): `Mesh+ for Meshtastic`

**Short description** (≤80) and **full description** (≤4000): `fastlane/metadata/android/en-US/short_description.txt` and `full_description.txt`, the same text F-Droid shows. The SMS relay leads, because Play only allows SMS and call log permissions for an app's core functionality.

**Category**: Communication · **Contact email**: (yours) · **Website**: https://meshplus.app · **Privacy policy**: https://meshplus.app/privacy.html

**Graphics**: `fastlane/metadata/android/en-US/images/` (icon.png, featureGraphic.png, phoneScreenshots/*.png; shared with F-Droid).

## App access (for reviewers)

"All functionality is available without login. The app talks to a Meshtastic LoRa radio; reviewers without one can tap **No radio yet? Try the demo** on the first screen, which simulates a connected radio (rooms, direct messages with replies, nodes, all settings). Firmware installs need real hardware."

## Ads: No ads.

## Content rating questionnaire
Category: Communication / messaging. User-to-user communication: **Yes** (users can message other users over the radio mesh; no moderation, no accounts). Shares user location: **No** (the app doesn't read the phone's location; radios may broadcast their own GPS position if the user's radio has GPS). Violence/sexual content/gambling/drugs: **No**.

## Target audience: 18+ (or 13+). Not designed for children.

## Data safety
- Data collected: **None** (nothing is sent to the developer or any server).
- Data shared: **None** with third parties.
- Encrypted in transit: Yes (mesh room/DM encryption). Users can delete data: Yes (uninstall / clear app data; history is on-device only).
- Internet use: only to fetch the firmware version list (api.meshtastic.org) and firmware files (GitHub), without personal data.

## SMS relay on Play (permissions declaration)
v0.4 (code 4) was rejected 2026-10-10 under the SMS/Call Log policy: the review couldn't verify "Cross-device synchronization or transfer of SMS or calls" from the demo video. v0.4.2 to 0.4.4 shipped to Play without the relay. The relay came back to the play flavor on branch `play-sms-relay` (PR: merge only once Play approves; if Play rejects, leave it unmerged so regular releases keep shipping):
- The listing leads with the relay (fastlane texts above), and screenshots 2 to 4 show it.
- In the app, turning the relay on first shows a disclosure dialog (what's read, posted and sent, and that nothing goes to a server), then Android's permission prompts.
- App content → SMS and Call log permissions: **Connected device companion apps** and **Cross-device synchronization or transfer of SMS or calls**. Permissions: RECEIVE_SMS, SEND_SMS, READ_CALL_LOG (READ_PHONE_STATE isn't restricted). Instructions for review (≤500):
  > Mesh+ is the companion app for Meshtastic radios (Bluetooth/USB). Core feature: SMS relay (Settings > SMS relay, off by default). The phone forwards SMS it receives (RECEIVE_SMS) and incoming caller numbers (READ_CALL_LOG) to the user's private, encrypted radio room, read on the user's other mesh devices, and sends replies written there as SMS (SEND_SMS). Data goes only over the user's radios, never to a server. Testing needs a SIM and two Meshtastic radios; see the video.
- Video (unlisted YouTube): a phone with a SIM and Mesh+ on radio A turns the relay on (disclosure, permission prompts); an SMS from another phone shows up in the room on a second phone on radio B; the reply written there goes out as an SMS; a call shows up as a call notice.
- Data safety stays "no data collected": relayed texts travel only over the user's private room, encrypted with a key only its members hold (Play's end-to-end encryption exemption).

Foreground service declaration (connectedDevice): "Keeps the Bluetooth/USB connection to the user's Meshtastic radio alive so messages arrive while the app is in the background; started when the user connects a radio, stopped when they disconnect; a notification shows while running."

## Note on testers
New *personal* developer accounts must run a closed test with at least 12 testers for 14 days before production access. Organization accounts are exempt.
