#!/bin/bash
# Reinstall the Android SDK (sandbox resets wipe /home/z/android-sdk).
set -e
SDK=/home/z/android-sdk
mkdir -p "$SDK/cmdline-tools"
if [ ! -d "$SDK/cmdline-tools/latest" ]; then
  echo "[sdk] downloading cmdline-tools..."
  cd /tmp
  if [ ! -f cmdtools.zip ]; then
    curl -fsSL -o cmdtools.zip https://dl.google.com/android/repository/commandlinetools-linux-11076708_latest.zip
  fi
  unzip -q -o cmdtools.zip -d "$SDK/cmdline-tools"
  mv "$SDK/cmdline-tools/cmdline-tools" "$SDK/cmdline-tools/latest"
fi
export ANDROID_HOME="$SDK"
export PATH="$SDK/cmdline-tools/latest/bin:$PATH"
echo "[sdk] accepting licenses + installing packages..."
yes | sdkmanager --licenses > /dev/null 2>&1 || true
sdkmanager --install "platform-tools" "platforms;android-34" "build-tools;34.0.0" > /dev/null
echo "[sdk] DONE"
ls "$SDK"
