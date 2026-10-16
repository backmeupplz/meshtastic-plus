#!/bin/bash
# Builds, signs and uploads the iPhone app to App Store Connect, where TestFlight and App Review pick it up.
# Bump CURRENT_PROJECT_VERSION (every upload) and MARKETING_VERSION (new versions) in MeshPlus.xcodeproj first.
# This Mac has no signing identity: archive unsigned, sign ad hoc with the app's identity (adhoc.entitlements), and
# App Store Connect signs it for distribution during the export (cloud signing with the API key).
set -eo pipefail
cd "$(dirname "$0")"
KEY=3FT23S9NSM
ISSUER=a18f42ff-6c44-410c-badf-50b11de8dbb8
P8=~/.appstoreconnect/private_keys/AuthKey_$KEY.p8
rm -rf build/MeshPlus.xcarchive build/export
xcodebuild -project MeshPlus.xcodeproj -scheme MeshPlus -destination 'generic/platform=iOS' -archivePath build/MeshPlus.xcarchive \
  CODE_SIGNING_ALLOWED=NO archive | grep -E 'error:|ARCHIVE'
codesign -f -s - --entitlements adhoc.entitlements build/MeshPlus.xcarchive/Products/Applications/MeshPlus.app
xcodebuild -exportArchive -archivePath build/MeshPlus.xcarchive -exportPath build/export -exportOptionsPlist ExportOptions.plist \
  -allowProvisioningUpdates -authenticationKeyPath $P8 -authenticationKeyID $KEY -authenticationKeyIssuerID $ISSUER | grep -E 'error:|EXPORT'
xcrun altool --upload-app -f build/export/MeshPlus.ipa -t ios --apiKey $KEY --apiIssuer $ISSUER
