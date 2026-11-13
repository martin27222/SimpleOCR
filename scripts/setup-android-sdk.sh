#!/usr/bin/env bash
# Installs JDK 17 and the Android SDK pieces needed to build SimpleOCR on a
# fresh Debian/Ubuntu machine (for example a cloud development container).
# Usage: scripts/setup-android-sdk.sh   then   export ANDROID_HOME=/opt/android-sdk
set -euo pipefail

SDK_ROOT="${ANDROID_HOME:-/opt/android-sdk}"
CMDLINE_TOOLS_ZIP="commandlinetools-linux-13114758_latest.zip"

if ! ls /usr/lib/jvm 2>/dev/null | grep -q "java-17"; then
  apt-get update -qq
  apt-get install -y -qq openjdk-17-jdk-headless unzip
fi

if [ ! -x "$SDK_ROOT/cmdline-tools/latest/bin/sdkmanager" ]; then
  mkdir -p "$SDK_ROOT/cmdline-tools"
  tmp="$(mktemp -d)"
  curl -fsSL -o "$tmp/tools.zip" "https://dl.google.com/android/repository/$CMDLINE_TOOLS_ZIP"
  unzip -q "$tmp/tools.zip" -d "$tmp"
  rm -rf "$SDK_ROOT/cmdline-tools/latest"
  mv "$tmp/cmdline-tools" "$SDK_ROOT/cmdline-tools/latest"
  rm -rf "$tmp"
fi

yes | "$SDK_ROOT/cmdline-tools/latest/bin/sdkmanager" --licenses >/dev/null || true
"$SDK_ROOT/cmdline-tools/latest/bin/sdkmanager" --install \
  "platform-tools" "platforms;android-36" "build-tools;36.0.0" >/dev/null

echo "Android SDK ready in $SDK_ROOT"
