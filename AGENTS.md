# Releasing Mesh+

Publishing a GitHub release `vX.Y.Z` is the whole release, for every store. `.github/workflows/release.yml` then:

- sends the play-flavor bundle to Google Play production;
- sends the iPhone app (`ios/`) to App Review, released automatically once approved. Its build number is the Android `versionCode`;
- attaches the fdroid-flavor APK as `meshplus.apk`, the file meshplus.app downloads through `releases/latest/download/meshplus.apk`;
- brings the F-Droid merge request (!51979) up to the release while it's open. Once it's merged, F-Droid builds each new `v*` tag itself and ships `meshplus.apk` only if its own build matches it.

A previous version still waiting for review or in review is replaced on both stores.

Before publishing, in one commit on `main`:

1. Bump `versionCode` (+1; it must be higher than any code already on Play, or the Play upload fails) and `versionName` (`X.Y.Z`, matching the tag) in `app/build.gradle.kts`.
2. Add `fastlane/metadata/android/en-US/changelogs/<versionCode>.txt`: one or two plain sentences for users (≤500 chars), with no mention of Android or iPhone. Play, the App Store (What's New) and F-Droid all show it.

Then run `gh release create vX.Y.Z --target main --title "Mesh+ X.Y.Z" --notes-file fastlane/metadata/android/en-US/changelogs/<versionCode>.txt` and check the Release workflow run.

To try a release without publishing anything, run the Release workflow by hand (Actions → Release → Run workflow) with mode `dry-run`. It builds both apps and shows what it would do. Mode `testflight-only` uploads the iPhone build to TestFlight without submitting it.

`.github/scripts` is shared with ABS+ and Todorant, so keep it identical in all three repos. The repo-specific values live in the workflow's `env`.
