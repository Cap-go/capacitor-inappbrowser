#!/usr/bin/env bash
# Build the example app for the iOS Simulator (CI). Requires plugin dist/ to exist.
set -euo pipefail

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$repo_root/example-app"

bun install --frozen-lockfile --ignore-scripts
bun run build
bunx cap sync ios

xcodebuild \
  -project ios/App/App.xcodeproj \
  -scheme App \
  -destination "generic/platform=iOS Simulator" \
  -derivedDataPath ios/DerivedData \
  -quiet \
  build \
  CODE_SIGNING_ALLOWED=NO