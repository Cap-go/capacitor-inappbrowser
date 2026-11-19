#!/usr/bin/env bash
# Install a prebuilt App.app on the selected simulator and launch it for Maestro.
set -euo pipefail

if [ -z "${IOS_SIMULATOR_UDID:-}" ]; then
  echo "IOS_SIMULATOR_UDID is not set"
  exit 1
fi

app_bundle="${1:-}"
if [ -z "$app_bundle" ]; then
  if [ -d maestro-artifacts/App.app ]; then
    app_bundle="maestro-artifacts/App.app"
  else
    app_bundle="$(find maestro-artifacts -maxdepth 3 -type d -name '*.app' | head -n 1 || true)"
  fi
fi
if [ -z "$app_bundle" ] || [ ! -d "$app_bundle" ]; then
  echo "Simulator app bundle not found (searched maestro-artifacts)"
  exit 1
fi

xcrun simctl boot "$IOS_SIMULATOR_UDID" 2>/dev/null || true
if ! timeout 75 xcrun simctl bootstatus "$IOS_SIMULATOR_UDID" -b; then
  echo "Simulator bootstatus timed out after 75s; continuing for Maestro."
fi

xcrun simctl install "$IOS_SIMULATOR_UDID" "$app_bundle"
open -a Simulator --args -CurrentDeviceUDID "$IOS_SIMULATOR_UDID"