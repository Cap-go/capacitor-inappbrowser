#!/bin/sh

set -eu

APP_ID="${1:-app.capgo.inappbrowser}"
TIMEOUT_SECONDS="${2:-120}"
ADB_SERIAL="${ADB_SERIAL:-}"

adb_cmd() {
  if [ -n "$ADB_SERIAL" ]; then
    adb -s "$ADB_SERIAL" "$@"
  else
    adb "$@"
  fi
}

adb_cmd wait-for-device
adb_cmd shell am force-stop "$APP_ID" >/dev/null 2>&1 || true
adb_cmd shell pm clear "$APP_ID" >/dev/null
adb_cmd shell monkey -p "$APP_ID" -c android.intent.category.LAUNCHER 1 >/dev/null 2>&1

deadline=$(( $(date +%s) + TIMEOUT_SECONDS ))
last_dump=""

while [ "$(date +%s)" -lt "$deadline" ]; do
  last_dump="$(adb_cmd exec-out uiautomator dump /dev/tty 2>/dev/null || true)"
  case "$last_dump" in
    *"Run Proxy Regression (Maestro)"*)
      exit 0
      ;;
  esac
  sleep 2
done

echo "Timed out waiting for Android Maestro overlay in $APP_ID" >&2
printf '%s\n' "$last_dump" >&2
exit 1
