#!/usr/bin/env sh

set -eu

repo_root="$(CDPATH= cd -- "$(dirname "$0")/.." && pwd)"
cd "$repo_root"

maestro_bin="${HOME}/.maestro/bin/maestro"
flow_path=".maestro/visual-regression-ios.yaml"
record_flow_path=".maestro/visual-regression-record-ios.yaml"
baseline_path=".maestro/visual-regression/baselines/ios/blank-toolbar-layout.png"
app_id="${APP_ID:-app.capgo.inappbrowser}"
log_file="/tmp/maestro-ios-visual-regression.log"

run_flow() {
  set +e
  "$maestro_bin" test -e "APP_ID=${app_id}" "$flow_path" >"$log_file" 2>&1
  status=$?
  cat "$log_file"
  return "$status"
}

baseline_bytes=0
if [ -f "$baseline_path" ]; then
  baseline_bytes="$(wc -c <"$baseline_path" | tr -d ' ')"
fi

if [ ! -f "$baseline_path" ] || [ "$baseline_bytes" -lt 500 ]; then
  echo "Baseline missing or placeholder at $baseline_path, recording from the current simulator..."
  "$maestro_bin" test -e "APP_ID=${app_id}" "$record_flow_path" >"$log_file" 2>&1 || {
    cat "$log_file"
    exit 1
  }
fi

set +e
run_flow
first_status=$?
set -e

if [ "$first_status" -eq 0 ]; then
  exit 0
fi

if ! grep -q 'IOSDriverTimeoutException' "$log_file"; then
  exit "$first_status"
fi

echo "Maestro iOS visual regression failed with IOSDriverTimeoutException. Retrying once..."
sleep 5
set +e
run_flow
retry_status=$?
set -e
exit "$retry_status"