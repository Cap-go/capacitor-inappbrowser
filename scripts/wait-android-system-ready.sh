#!/usr/bin/env sh

set -eu

attempt=0
max_attempts="${ANDROID_READY_MAX_ATTEMPTS:-90}"

while true; do
  boot_completed="$(adb shell getprop sys.boot_completed 2>/dev/null | tr -d '\r\n' || true)"
  package_ready=0
  package_service_ready=0
  framework_package_ready=0
  settings_ready=0

  if adb shell cmd package list packages >/dev/null 2>&1; then
    package_ready=1
  fi

  if adb shell service check package 2>/dev/null | grep -qi "found"; then
    package_service_ready=1
  fi

  if adb shell pm path android >/dev/null 2>&1; then
    framework_package_ready=1
  fi

  if adb shell settings get global device_provisioned >/dev/null 2>&1; then
    settings_ready=1
  fi

  if [ "$boot_completed" = "1" ] &&
    [ "$package_ready" -eq 1 ] &&
    [ "$package_service_ready" -eq 1 ] &&
    [ "$framework_package_ready" -eq 1 ] &&
    [ "$settings_ready" -eq 1 ]; then
    exit 0
  fi

  attempt=$((attempt + 1))
  if [ "$attempt" -ge "$max_attempts" ]; then
    echo "Android system services did not become ready"
    echo \
      "boot_completed=$boot_completed package_ready=$package_ready package_service_ready=$package_service_ready framework_package_ready=$framework_package_ready settings_ready=$settings_ready"
    exit 1
  fi

  sleep 2
done
