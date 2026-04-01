#!/usr/bin/env bash

set -euo pipefail

readonly app_id="${APP_ID:-app.capgo.inappbrowser}"
readonly app_apk="example-app/android/app/build/outputs/apk/debug/app-debug.apk"
readonly package_manager_timeout_seconds="${ANDROID_PACKAGE_MANAGER_TIMEOUT_SECONDS:-180}"
readonly maestro_timeout_seconds="${MAESTRO_TEST_TIMEOUT_SECONDS:-420}"
readonly maestro_flow="example-app/.maestro/proxy-regression.yaml"

export PATH="$HOME/.maestro/bin:$PATH"
export MAESTRO_DRIVER_STARTUP_TIMEOUT="${MAESTRO_DRIVER_STARTUP_TIMEOUT:-180000}"

wait_for_device() {
  if command -v timeout >/dev/null 2>&1; then
    timeout 60s adb wait-for-device
    return
  fi

  adb wait-for-device
}

wait_for_package_manager() {
  local deadline=$((SECONDS + package_manager_timeout_seconds))

  wait_for_device

  echo "Waiting up to ${package_manager_timeout_seconds}s for Android package manager readiness"
  while ((SECONDS < deadline)); do
    if adb shell cmd package list packages >/dev/null 2>&1; then
      echo "Android package manager is ready"
      return 0
    fi
    sleep 2
  done

  echo "Android package manager did not become ready within ${package_manager_timeout_seconds}s" >&2
  adb shell getprop sys.boot_completed || true
  adb shell getprop dev.bootcomplete || true
  adb shell getprop init.svc.bootanim || true
  adb logcat -d -b main -b system -b crash -t 200 || true
  return 1
}

run_maestro() {
  local status

  if command -v timeout >/dev/null 2>&1; then
    set +e
    timeout "${maestro_timeout_seconds}s" maestro test -e APP_ID="$app_id" "$maestro_flow"
    status=$?
    set -e
    if [ "$status" -eq 124 ]; then
      echo "Maestro test timed out after ${maestro_timeout_seconds}s" >&2
    fi
    return "$status"
  fi

  maestro test -e APP_ID="$app_id" "$maestro_flow"
}

wait_for_package_manager
adb reverse tcp:8000 tcp:8000
adb reverse tcp:8123 tcp:8123
adb install -r "$app_apk"

set +e
run_maestro
status=$?
set -e
if [ "$status" -ne 0 ]; then
  adb shell getprop sys.boot_completed || true
  adb shell getprop dev.bootcomplete || true
  adb shell getprop init.svc.bootanim || true
  adb logcat -d -b main -b system -b crash -t 200 || true
  exit "$status"
fi
