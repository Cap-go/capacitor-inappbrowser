#!/usr/bin/env sh

set -eu

script_dir="$(CDPATH= cd -- "$(dirname "$0")" && pwd)"

sh "$script_dir/prepare-android-maestro-device.sh"

adb shell settings put global sysui_demo_allowed 1 >/dev/null 2>&1 || true
adb shell am broadcast -a com.android.systemui.demo -e command enter >/dev/null 2>&1 || true
adb shell am broadcast -a com.android.systemui.demo -e command clock -e hhmm 0900 >/dev/null 2>&1 || true
adb shell am broadcast -a com.android.systemui.demo -e command network -e wifi show -e level 4 >/dev/null 2>&1 || true
adb shell am broadcast -a com.android.systemui.demo -e command battery -e level 100 -e plugged false >/dev/null 2>&1 || true
adb shell am broadcast -a com.android.systemui.demo -e command notifications -e visible false >/dev/null 2>&1 || true