#!/usr/bin/env sh

set -eu

script_dir="$(CDPATH= cd -- "$(dirname "$0")" && pwd)"

adb wait-for-device
sh "$script_dir/wait-android-system-ready.sh"

adb shell settings put global window_animation_scale 0.0 >/dev/null 2>&1 || true
adb shell settings put global transition_animation_scale 0.0 >/dev/null 2>&1 || true
adb shell settings put global animator_duration_scale 0.0 >/dev/null 2>&1 || true
adb shell pm grant com.google.android.inputmethod.latin android.permission.READ_CONTACTS >/dev/null 2>&1 || true
adb shell pm grant com.google.android.inputmethod.latin android.permission.GET_ACCOUNTS >/dev/null 2>&1 || true
