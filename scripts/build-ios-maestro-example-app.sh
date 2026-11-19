#!/usr/bin/env bash
# Build and install the example app on the simulator selected via IOS_SIMULATOR_UDID.
set -euo pipefail

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$repo_root"

if [ -z "${IOS_SIMULATOR_UDID:-}" ]; then
  echo "IOS_SIMULATOR_UDID is not set"
  exit 1
fi

bun run build
cd example-app
bun install --frozen-lockfile
bun run build
bunx cap sync ios

xcrun simctl boot "$IOS_SIMULATOR_UDID" 2>/dev/null || true
xcrun simctl bootstatus "$IOS_SIMULATOR_UDID" -b

xcodebuild \
  -project ios/App/App.xcodeproj \
  -scheme App \
  -destination "id=$IOS_SIMULATOR_UDID" \
  -derivedDataPath ios/DerivedData \
  -quiet \
  build

xcrun simctl install "$IOS_SIMULATOR_UDID" ios/DerivedData/Build/Products/Debug-iphonesimulator/App.app