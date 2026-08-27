#!/bin/bash
set -euo pipefail

# Decrypt keystore
echo "$KEY_STORE" | base64 --decode > release.jks

# Find APK
APK_PATH=$(find app/build/outputs/apk/github/release -type f -name "*.apk" | head -n 1)

if [ -z "$APK_PATH" ]; then
  echo "ERROR: No GitHub release APK found."
  echo "Contents of app/build/outputs/apk/github/release:"
  find app/build/outputs/apk/github/release -maxdepth 2 -type f -print || true
  exit 1
fi

echo "Signing APK: $APK_PATH"

# Get latest build-tools
BUILD_TOOLS_PATH=$(ls -d "$ANDROID_HOME"/build-tools/* | sort -V | tail -1)

# Zipalign
"$BUILD_TOOLS_PATH/zipalign" -v 4 \
  "$APK_PATH" \
  app-signed-aligned.apk

# Sign
"$BUILD_TOOLS_PATH/apksigner" sign \
  --ks release.jks \
  --ks-key-alias "$KEY_STORE_ALIAS" \
  --ks-pass "pass:$KEY_STORE_PASSWORD" \
  --key-pass "pass:$KEY_PASSWORD" \
  --out better-internet-tiles-signed.apk \
  app-signed-aligned.apk

# Verify
"$BUILD_TOOLS_PATH/apksigner" verify --verbose better-internet-tiles-signed.apk

# Cleanup
rm -f release.jks app-signed-aligned.apk

echo "Signed APK created: better-internet-tiles-signed.apk"