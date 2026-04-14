#!/usr/bin/env sh

set -eu

if [ "$#" -ne 1 ]; then
  echo "Usage: $0 <apk-path>"
  exit 1
fi

apk_path="$1"
max_attempts="${ANDROID_APK_INSTALL_MAX_ATTEMPTS:-5}"
retry_delay_seconds="${ANDROID_APK_INSTALL_RETRY_DELAY_SECONDS:-5}"
script_dir="$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)"
attempt=1

while [ "$attempt" -le "$max_attempts" ]; do
  adb wait-for-device >/dev/null
  sh "$script_dir/wait-android-system-ready.sh"

  if adb install -r "$apk_path"; then
    exit 0
  fi

  echo "Android APK install attempt $attempt failed, retrying after package-manager warmup..."
  adb shell service check package >/dev/null 2>&1 || true
  adb shell cmd package list packages >/dev/null 2>&1 || true
  adb shell pm path android >/dev/null 2>&1 || true

  if [ "$attempt" -lt "$max_attempts" ]; then
    sleep "$retry_delay_seconds"
  fi
  attempt=$((attempt + 1))
done

echo "Failed to install Android APK after $max_attempts attempts: $apk_path"
exit 1
