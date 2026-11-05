#!/usr/bin/env bash
set -euo pipefail

REF="${1:?git ref required}"
OUTPUT_REL="${2:?output path relative to repo root required}"

SIMULATOR_NAME="${SIMULATOR_NAME:-iPhone 16}"
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/../.." && pwd)"
HELPER_MJS="$REPO_ROOT/.github/pr775/status-bar-auto-open.mjs"
OUTPUT_PATH="$REPO_ROOT/$OUTPUT_REL"
WORKTREE="$(mktemp -d "${TMPDIR:-/tmp}/pr775-worktree.XXXXXX")"

cleanup() {
  git -C "$REPO_ROOT" worktree remove --force "$WORKTREE" 2>/dev/null || true
  rm -rf "$WORKTREE"
}
trap cleanup EXIT

if [[ ! -f "$HELPER_MJS" ]]; then
  echo "Missing helper module: $HELPER_MJS"
  exit 1
fi

git -C "$REPO_ROOT" fetch origin --tags --force
git -C "$REPO_ROOT" worktree add --detach "$WORKTREE" "$REF"

cp "$HELPER_MJS" "$WORKTREE/example-app/src/js/status-bar-auto-open.mjs"

INDEX_HTML="$WORKTREE/example-app/src/index.html"
if ! grep -q 'status-bar-auto-open.mjs' "$INDEX_HTML"; then
  perl -0pi -e 's|<script src="\./js/capacitor-welcome\.js" type="module"></script>|<script src="./js/capacitor-welcome.js" type="module"></script>\n    <script src="./js/status-bar-auto-open.mjs" type="module"></script>|' "$INDEX_HTML"
fi

CAP_CONFIG="$WORKTREE/example-app/capacitor.config.ts"
perl -pi -e "s/autoUpdate: true/autoUpdate: false/g; s/directUpdate: 'always'/directUpdate: false/g" "$CAP_CONFIG"

run_with_retries() {
  local max_attempts=3
  local attempt=1
  while true; do
    if "$@"; then
      return 0
    fi
    echo "'$*' failed on attempt $attempt/$max_attempts"
    if [[ "$attempt" -eq "$max_attempts" ]]; then
      return 1
    fi
    attempt=$((attempt + 1))
    sleep $((attempt * 10))
  done
}

DERIVED="$WORKTREE/ios-derived"
SCREENSHOT_TMP="$WORKTREE/pr775-screenshot.png"

cd "$WORKTREE"
run_with_retries bun install --frozen-lockfile
bun run build
cd example-app
run_with_retries bun install --frozen-lockfile
bun run build
bunx cap sync ios

xcodebuild \
  -project ios/App/App.xcodeproj \
  -scheme App \
  -configuration Debug \
  -destination "platform=iOS Simulator,name=${SIMULATOR_NAME},OS=latest" \
  -derivedDataPath "$DERIVED" \
  CODE_SIGNING_ALLOWED=NO \
  build

APP_PATH="$(find "$DERIVED" -type d -name 'App.app' -path '*iphonesimulator*' | head -n 1)"
if [[ -z "$APP_PATH" || ! -d "$APP_PATH" ]]; then
  echo "Could not locate built App.app for iOS Simulator"
  exit 1
fi

DEVICE_UDID="$(xcrun simctl list devices available | grep -F "${SIMULATOR_NAME} (" | head -n 1 | sed -E 's/^[[:space:]]*//; s/.* \(([0-9A-F-]+)\).*/\1/')"
if [[ -z "$DEVICE_UDID" ]]; then
  echo "No available simulator named ${SIMULATOR_NAME}"
  xcrun simctl list devices available
  exit 1
fi

xcrun simctl shutdown all 2>/dev/null || true
xcrun simctl boot "$DEVICE_UDID"
xcrun simctl bootstatus "$DEVICE_UDID" -b
xcrun simctl ui "$DEVICE_UDID" appearance dark
xcrun simctl install "$DEVICE_UDID" "$APP_PATH"
xcrun simctl launch "$DEVICE_UDID" app.capgo.inappbrowser
sleep 12
xcrun simctl io "$DEVICE_UDID" screenshot "$SCREENSHOT_TMP"

FILE_SIZE="$(stat -f%z "$SCREENSHOT_TMP")"
if [[ "$FILE_SIZE" -lt 10000 ]]; then
  echo "Screenshot looks too small (${FILE_SIZE} bytes): $SCREENSHOT_TMP"
  exit 1
fi

mkdir -p "$(dirname "$OUTPUT_PATH")"
cp "$SCREENSHOT_TMP" "$OUTPUT_PATH"
echo "Wrote ${OUTPUT_PATH} (${FILE_SIZE} bytes) from ref ${REF}"
