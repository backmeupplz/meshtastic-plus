# Releasing Mesh+

Publishing a GitHub release `vX.Y.Z` is the whole release: `.github/workflows/release.yml` attaches the fdroid-flavor APK as `meshplus.apk` (what meshplus.app downloads) and uploads the play-flavor bundle to Play production; F-Droid builds each new `v*` tag on its own. Before publishing, in one commit on `main`:

1. Bump `versionCode` (+1; it must be higher than any code already on Play, or the Play upload fails) and `versionName` (`X.Y.Z`, matching the tag) in `app/build.gradle.kts`.
2. Add `fastlane/metadata/android/en-US/changelogs/<versionCode>.txt`: one or two plain sentences for users (≤500 chars). F-Droid and Play both show it as the release notes.
3. Build both flavors (`./gradlew assemblePlayRelease assembleFdroidRelease`) and push.

Then `gh release create vX.Y.Z --target main --title "Mesh+ X.Y.Z" --notes-file fastlane/metadata/android/en-US/changelogs/<versionCode>.txt` and check the Release workflow run.
