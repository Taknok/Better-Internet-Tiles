#!/bin/bash
set -e

# Decrypt keystore
echo "$KEY_STORE" | base64 --decode > release.jks

# Find APK in the whole outputs/apk directory
# We look for any APK that isn't already signed or aligned by us
APK_PATH=$(find app/build/outputs/apk -type f -name "*-unsigned.apk" | head -n 1)

if [ -z "$APK_PATH" ]; then
  echo "ERROR: No APK found in app/build/outputs/apk."
  echo "Contents of app/build/outputs/apk:"
  find app/build/outputs/apk -maxdepth 3 -type f -print || true
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
# We use the same production keystore for both release and prerelease (debug branch)
# as per common practice for GitHub releases to allow easy updates.
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
rm -f release.jks
rm *-aligned.apk

echo "Signed APK created: better-internet-tiles-signed.apk"
