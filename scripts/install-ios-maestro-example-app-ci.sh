#!/usr/bin/env bash
# Install a prebuilt App.app on the selected simulator and launch it for Maestro.
set -euo pipefail

if [ -z "${IOS_SIMULATOR_UDID:-}" ]; then
  echo "IOS_SIMULATOR_UDID is not set"
  exit 1
fi

app_bundle="${1:-maestro-artifacts/App.app}"
if [ ! -d "$app_bundle" ]; then
  echo "Simulator app bundle not found: $app_bundle"
  exit 1
fi

xcrun simctl boot "$IOS_SIMULATOR_UDID" 2>/dev/null || true
xcrun simctl bootstatus "$IOS_SIMULATOR_UDID" -b

xcrun simctl install "$IOS_SIMULATOR_UDID" "$app_bundle"
open -a Simulator --args -CurrentDeviceUDID "$IOS_SIMULATOR_UDID"
xcrun simctl launch "$IOS_SIMULATOR_UDID" app.capgo.inappbrowser || true