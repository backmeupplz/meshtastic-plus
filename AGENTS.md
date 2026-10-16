# Releasing Mesh+

Publishing a GitHub release `vX.Y.Z` is the whole release: `.github/workflows/release.yml` attaches the fdroid-flavor APK as `meshplus.apk` (what meshplus.app downloads) and uploads the play-flavor bundle to Play production; F-Droid builds each new `v*` tag on its own. Before publishing, in one commit on `main`:

1. Bump `versionCode` (+1; it must be higher than any code already on Play, or the Play upload fails) and `versionName` (`X.Y.Z`, matching the tag) in `app/build.gradle.kts`.
2. Add `fastlane/metadata/android/en-US/changelogs/<versionCode>.txt`: one or two plain sentences for users (≤500 chars). F-Droid and Play both show it as the release notes.
3. Build both flavors (`./gradlew assemblePlayRelease assembleFdroidRelease`) and push.

Then `gh release create vX.Y.Z --target main --title "Mesh+ X.Y.Z" --notes-file fastlane/metadata/android/en-US/changelogs/<versionCode>.txt` and check the Release workflow run.

# Releasing Mesh+ for iPhone

The iPhone app is `ios/` (SwiftUI, no dependencies): App Store app 6823711977, bundle ID `com.borodutch.meshplus`, team ACWP4F58HZ. It's the Android app minus the SMS relay, USB and firmware updates, plus the community rules screen, Block and Report that App Review requires for chat apps. App Review tests it with the demo node, so keep *No radio yet? Try the demo* working. To ship a build:

1. Bump `CURRENT_PROJECT_VERSION` (+1 for every upload) and, for a new version, `MARKETING_VERSION` in `ios/MeshPlus.xcodeproj/project.pbxproj` (Debug and Release of the MeshPlus target).
2. Run `ios/release.sh`. It archives, has App Store Connect sign the build with the API key, and uploads it. Then submit that build in App Store Connect.

`ios/screenshots.sh` retakes the App Store screenshots (6.9", dark) from the demo node into `ios/appstore/screenshots`. CI (`.github/workflows/ios.yml`) runs the unit tests and the demo UI tests on every change under `ios/`.
