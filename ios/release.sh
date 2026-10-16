#!/bin/bash
# Builds the iPhone app, has App Store Connect sign it for distribution and uploads it (it then shows up in TestFlight).
#   ios/release.sh <version> <build> [key-id issuer-id key.p8]
#   ios/release.sh 0.4.4 8 "$ASC_KEY_ID" "$ASC_ISSUER_ID" "$RUNNER_TEMP/AuthKey.p8"
# The key defaults to ~/.appstoreconnect/private_keys/AuthKey_3FT23S9NSM.p8. No signing identity is needed: the archive
# is built unsigned, signed ad hoc with the app's identity (adhoc.entitlements), and the export is cloud-signed.
set -eo pipefail
[ $# -eq 2 ] || [ $# -eq 5 ] || { sed -n '2,6p' "$0"; exit 1; }
VERSION=$1
BUILD=$2
KEY=${3:-3FT23S9NSM}
ISSUER=${4:-a18f42ff-6c44-410c-badf-50b11de8dbb8}
P8=$(realpath "${5:-$HOME/.appstoreconnect/private_keys/AuthKey_$KEY.p8}") # absolute: the script changes directory
cd "$(dirname "$0")"
rm -rf build/MeshPlus.xcarchive build/export
xcodebuild -project MeshPlus.xcodeproj -scheme MeshPlus -destination 'generic/platform=iOS' -archivePath build/MeshPlus.xcarchive \
  MARKETING_VERSION="$VERSION" CURRENT_PROJECT_VERSION="$BUILD" CODE_SIGNING_ALLOWED=NO archive | grep -E 'error:|ARCHIVE'
codesign -f -s - --entitlements adhoc.entitlements build/MeshPlus.xcarchive/Products/Applications/MeshPlus.app
xcodebuild -exportArchive -archivePath build/MeshPlus.xcarchive -exportPath build/export -exportOptionsPlist ExportOptions.plist \
  -allowProvisioningUpdates -authenticationKeyPath "$P8" -authenticationKeyID "$KEY" -authenticationKeyIssuerID "$ISSUER" | grep -E 'error:|EXPORT'
xcrun altool --upload-app -f build/export/MeshPlus.ipa -t ios --apiKey "$KEY" --apiIssuer "$ISSUER" --p8-file-path "$P8"
