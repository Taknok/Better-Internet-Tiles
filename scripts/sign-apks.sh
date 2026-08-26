#!/bin/bash
set -e

# Decrypt keystore
echo "$KEY_STORE" | base64 --decode > release.jks

# Find APKs
APK_PATH=$(find app/build/outputs/apk/github/release -name "*.apk" | head -n 1)

# Get build-tools path
BUILD_TOOLS_PATH=$(ls -d $ANDROID_HOME/build-tools/* | tail -1)

# Zipalign the APK
$BUILD_TOOLS_PATH/zipalign -v 4 "$APK_PATH" app-signed-aligned.apk

# Sign the APK
$BUILD_TOOLS_PATH/apksigner sign --ks release.jks \
  --ks-key-alias "$KEY_STORE_ALIAS" \
  --ks-pass pass:"$KEY_STORE_PASSWORD" \
  --key-pass pass:"$KEY_PASSWORD" \
  --out better-internet-tiles-signed.apk app-signed-aligned.apk

# Cleanup
rm release.jks
rm *-aligned.apk
