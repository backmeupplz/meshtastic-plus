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
