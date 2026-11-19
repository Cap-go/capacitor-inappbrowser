#!/usr/bin/env sh

set -eu

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