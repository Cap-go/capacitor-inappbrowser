#!/usr/bin/env sh

set -eu

platform="${1:-}"
script_dir="$(CDPATH= cd -- "$(dirname "$0")" && pwd)"

case "$platform" in
  android)
    sh "$script_dir/prepare-android-maestro-device.sh"
    adb shell settings put global sysui_demo_allowed 1 >/dev/null 2>&1 || true
    adb shell am broadcast -a com.android.systemui.demo -e command enter >/dev/null 2>&1 || true
    adb shell am broadcast -a com.android.systemui.demo -e command clock -e hhmm 0900 >/dev/null 2>&1 || true
    adb shell am broadcast -a com.android.systemui.demo -e command network -e wifi show -e level 4 >/dev/null 2>&1 || true
    adb shell am broadcast -a com.android.systemui.demo -e command battery -e level 100 -e plugged false >/dev/null 2>&1 || true
    adb shell am broadcast -a com.android.systemui.demo -e command notifications -e visible false >/dev/null 2>&1 || true
    ;;
  ios)
    if [ -z "${IOS_SIMULATOR_UDID:-}" ]; then
      echo "IOS_SIMULATOR_UDID is not set"
      exit 1
    fi
    xcrun simctl status_bar "$IOS_SIMULATOR_UDID" override \
      --time "9:41" \
      --batteryState charged \
      --batteryLevel 100 \
      --wifiBars 3 \
      --cellularBars 4 \
      --cellularMode active
    ;;
  *)
    echo "Expected platform android or ios"
    exit 1
    ;;
esac