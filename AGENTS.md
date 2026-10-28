# Releasing Mesh+

Publishing a release `vX.Y.Z` on git.borodutch.com is the whole release, for every store. `.forgejo/workflows/release.yml` (Forgejo Actions) then:

- sends the play-flavor bundle to Google Play production;
- sends the iPhone app (`ios/`) to App Review, released automatically once approved. Its build number is the Android `versionCode`;
- attaches the fdroid-flavor APK as `meshplus.apk` to the release and to the same release on the GitHub mirror, which it creates with the same title and notes (releases don't mirror). meshplus.app downloads it from GitHub through `releases/latest/download/meshplus.apk`;
- brings the F-Droid merge request (!51979) up to the release while it's open. Once it's merged, F-Droid builds each new `v*` tag itself and ships `meshplus.apk` only if its own build matches it.

A previous version still waiting for review or in review is replaced on both stores.

Before publishing, in one commit on `main`:

1. Bump `versionCode` (+1; it must be higher than any code already on Play, or the Play upload fails) and `versionName` (`X.Y.Z`, matching the tag) in `app/build.gradle.kts`.
2. Add `fastlane/metadata/android/en-US/changelogs/<versionCode>.txt`: one or two plain sentences for users (≤500 chars), with no mention of Android or iPhone. Play, the App Store (What's New) and F-Droid all show it.

Then run `tea release create --repo backmeupplz/meshtastic-plus --tag vX.Y.Z --target main --title "Mesh+ X.Y.Z" --note-file fastlane/metadata/android/en-US/changelogs/<versionCode>.txt` and check the Release workflow run on git.borodutch.com.

To try a release without publishing anything, run the Release workflow by hand (Actions → Release → Run workflow on git.borodutch.com) with mode `dry-run`. It builds both apps and shows what it would do. Mode `testflight-only` uploads the iPhone build to TestFlight without submitting it.

`.github/scripts` is shared with ABS+ and Todorant, so keep it identical in all three repos. The repo-specific values live in the workflow's `env`.

## Store texts

Each language's App Store listing (name, subtitle, keywords, promotional text, description, screenshot captions and the Play feature graphic tagline) is in `appstore/<App Store locale>/listing.md`, with en-US in `appstore/listing.md`. Its Play and F-Droid texts are in `fastlane/metadata/android/<Play code>/`. The App Store texts describe the iPhone app, which has no SMS relay, USB or firmware updates, and never name another platform. `node scripts/store-copy.mjs` checks every language against the stores' limits and keyword rules, and the Store texts workflow runs it on every change. The keyword research behind them is in prime-radiant `projects/meshplus/drafts/aso-<locale>.md`.

## Store screenshots

Every language has its own screenshots, taken from release builds on the demo node and put under that language's captions:

1. `ios/screenshots.sh` (env `DEVICE`, `LANGS`, `OUT`) takes the five App Store shots in each app language: `OUT=<raw dir>/ios` on an iPhone 18 Pro Max simulator (iOS 27.0, 1320×2868), and `OUT=<raw dir>/duo` on an iPhone Duo for its cover display.
2. `python3 scripts/shots/android.py <play release APK> <raw dir>/<phone|tab7|tab10> [language ...]` takes the seven Play shots on an emulator (`ANDROID_SERIAL`, API 34 with a hardware keyboard, plus [ADB Keyboard](https://github.com/senzhk/ADBKeyBoard) in `ADB_KEYBOARD_APK` to type non-ASCII text). It shows the demo's SMS room, where Base Camp's phone relays texts and a missed call.
3. `python3 scripts/shots/compose.py <raw dir> [locale ...]` (Google Chrome and the Python `websockets` package) captions them and writes the feature graphics: `appstore/screenshots/<locale>/` (6.9"; Duo cover in `duo-outer/`, not committed until #249 fixes the cover-display room layout) and `fastlane/metadata/android/<Play code>/images/` (F-Droid reads these too). It fails if a caption takes more than two lines or any box comes within 24 px of an edge or another box.

The Listings workflow (Actions → Listings, dry-run by default) sends the texts and screenshots to Play, where they go to review, and to the App Store version being prepared; a release sends the App Store ones right before it submits. App Store Connect has no iPhone Duo screenshot type yet, so the Duo frames only stay in the repo (from #249 on).
