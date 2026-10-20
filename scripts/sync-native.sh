#!/usr/bin/env bash
# Copy the native PushGenius SDK sources into this package, so the npm package
# builds on its own without depending on the native SDK repos.
#
# Expects pushgenius-ios and pushgenius-android checked out next to this repo
# (as ../ios and ../android); override with PUSHGENIUS_IOS_SDK / PUSHGENIUS_ANDROID_SDK.
# Run it after changing either native SDK, then commit the result.
set -euo pipefail

root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
ios_sdk="${PUSHGENIUS_IOS_SDK:-$root/../ios}"
android_sdk="${PUSHGENIUS_ANDROID_SDK:-$root/../android}"

ios_src="$ios_sdk/Sources/PushGenius"
android_src="$android_sdk/pushgenius/src/main/java/us/pushgeni/sdk"
[[ -d "$ios_src" ]] || { echo "error: iOS SDK sources not found at $ios_src" >&2; exit 1; }
[[ -d "$android_src" ]] || { echo "error: Android SDK sources not found at $android_src" >&2; exit 1; }

rsync -a --delete "$ios_src/" "$root/ios/PushGeniusCore/"
rsync -a --delete "$android_src/" "$root/android/src/main/java/us/pushgeni/sdk/"
cp "$android_sdk/pushgenius/src/main/AndroidManifest.xml" "$root/android/src/main/AndroidManifest.xml"
echo "Synced iOS SDK ($(git -C "$ios_sdk" rev-parse --short HEAD 2>/dev/null || echo "unknown")) and Android SDK ($(git -C "$android_sdk" rev-parse --short HEAD 2>/dev/null || echo "unknown"))."
