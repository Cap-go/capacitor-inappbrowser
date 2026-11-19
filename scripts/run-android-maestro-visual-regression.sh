#!/usr/bin/env sh

set -eu

repo_root="$(CDPATH= cd -- "$(dirname "$0")/.." && pwd)"
cd "$repo_root"

maestro_bin="${HOME}/.maestro/bin/maestro"
flow_path=".maestro/visual-regression-android.yaml"
record_flow_path=".maestro/visual-regression-record-android.yaml"
baseline_path=".maestro/visual-regression/baselines/android/blank-toolbar-layout.png"
app_id="${APP_ID:-app.capgo.inappbrowser}"
log_file="/tmp/maestro-android-visual-regression.log"

if [ ! -x "$maestro_bin" ]; then
  maestro_bin="$(command -v maestro || true)"
fi

if [ -z "$maestro_bin" ] || [ ! -x "$maestro_bin" ]; then
  echo "Maestro CLI is not available"
  exit 1
fi

baseline_bytes=0
if [ -f "$baseline_path" ]; then
  baseline_bytes="$(wc -c <"$baseline_path" | tr -d ' ')"
fi

if [ ! -f "$baseline_path" ] || [ "$baseline_bytes" -lt 500 ]; then
  echo "Baseline missing or placeholder at $baseline_path, recording from the current emulator..."
  "$maestro_bin" test -e "APP_ID=${app_id}" "$record_flow_path" >"$log_file" 2>&1 || {
    cat "$log_file"
    exit 1
  }
fi

set +e
"$maestro_bin" test -e "APP_ID=${app_id}" "$flow_path" >"$log_file" 2>&1
status=$?
set -e
cat "$log_file"
exit "$status"