#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
EXAMPLE_DIR="$ROOT_DIR/example-app"
APK_PATH="$EXAMPLE_DIR/android/app/build/outputs/apk/debug/app-debug.apk"
RESULTS_DIR="$ROOT_DIR/maestro-results"
FLOW_PATH="$ROOT_DIR/.maestro/download-handling-android.yaml"
SKIP_BUILD="${CAPGO_MAESTRO_SKIP_BUILD:-0}"
DEVICE_ID="${CAPGO_MAESTRO_ANDROID_DEVICE_ID:-$(adb devices | awk 'NR > 1 && $2 == "device" { print $1; exit }')}"

if ! command -v adb >/dev/null 2>&1; then
  echo "adb is required to run Android Maestro tests." >&2
  exit 1
fi

if ! command -v maestro >/dev/null 2>&1; then
  echo "maestro is required to run Android Maestro tests." >&2
  exit 1
fi

if [[ -z "$DEVICE_ID" ]]; then
  echo "No Android device is available for Maestro." >&2
  exit 1
fi

wait_for_device() {
  local timeout_seconds="${CAPGO_MAESTRO_EMULATOR_BOOT_TIMEOUT_SECONDS:-180}"
  local deadline=$((SECONDS + timeout_seconds))

  adb -s "$DEVICE_ID" wait-for-device

  while ((SECONDS < deadline)); do
    local boot_completed
    boot_completed="$(adb -s "$DEVICE_ID" shell getprop sys.boot_completed 2>/dev/null | tr -d '\r')"
    if [[ "$boot_completed" == "1" ]]; then
      return 0
    fi
    sleep 2
  done

  echo "Android device $DEVICE_ID did not finish booting within ${timeout_seconds} seconds." >&2
  exit 1
}

if [[ "$SKIP_BUILD" != "1" ]]; then
  if [[ ! -d "$ROOT_DIR/node_modules" ]]; then
    (cd "$ROOT_DIR" && bun install)
  fi

  (
    cd "$ROOT_DIR"
    bun run build
  )

  (
    cd "$EXAMPLE_DIR"
    bun install
    bun run build
    bunx cap sync android
  )

  (
    cd "$EXAMPLE_DIR/android"
    ./gradlew assembleDebug
  )
fi

if [[ ! -f "$APK_PATH" ]]; then
  echo "Expected Android debug APK at $APK_PATH" >&2
  exit 1
fi

wait_for_device

adb -s "$DEVICE_ID" shell settings put global window_animation_scale 0 >/dev/null 2>&1 || true
adb -s "$DEVICE_ID" shell settings put global transition_animation_scale 0 >/dev/null 2>&1 || true
adb -s "$DEVICE_ID" shell settings put global animator_duration_scale 0 >/dev/null 2>&1 || true
adb -s "$DEVICE_ID" install -r "$APK_PATH"
adb -s "$DEVICE_ID" shell am force-stop app.capgo.inappbrowser >/dev/null 2>&1 || true
adb -s "$DEVICE_ID" shell am start -W -n app.capgo.inappbrowser/app.capgo.plugin.InappBrowser.MainActivity >/dev/null

rm -rf "$RESULTS_DIR"
mkdir -p "$RESULTS_DIR"

ANDROID_SERIAL="$DEVICE_ID" MAESTRO_CLI_NO_ANALYTICS=1 maestro test \
  "$FLOW_PATH" \
  --debug-output "$RESULTS_DIR/artifacts" \
  --flatten-debug-output \
  --format junit \
  --output "$RESULTS_DIR/junit.xml"
