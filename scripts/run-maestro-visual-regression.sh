#!/usr/bin/env sh

set -eu

platform="${1:-}"
mode="${2:-assert}"

case "$platform" in
  android)
    baseline_stem=".maestro/visual-regression/baselines/android/blank-toolbar-layout"
    ;;
  ios)
    baseline_stem=".maestro/visual-regression/baselines/ios/blank-toolbar-layout"
    ;;
  *)
    echo "Expected platform android or ios"
    exit 1
    ;;
esac

baseline_image="${baseline_stem}.png"

flow_path=".maestro/visual-regression-assert.yaml"
record_flow_path=".maestro/visual-regression-record.yaml"

maestro_bin="${HOME}/.maestro/bin/maestro"
app_id="${APP_ID:-app.capgo.inappbrowser}"
log_file="/tmp/maestro-${platform}-visual-regression.log"

if [ ! -x "$maestro_bin" ]; then
  maestro_bin="$(command -v maestro || true)"
fi

if [ -z "$maestro_bin" ] || [ ! -x "$maestro_bin" ]; then
  echo "Maestro CLI is not available"
  exit 1
fi

run_flow() {
  set +e
  "$maestro_bin" test -e "APP_ID=${app_id}" -e "BASELINE_PATH=${baseline_stem}" "$flow_path" >"$log_file" 2>&1
  status=$?
  cat "$log_file"
  return "$status"
}

baseline_bytes=0
if [ -f "$baseline_image" ]; then
  baseline_bytes="$(wc -c <"$baseline_image" | tr -d ' ')"
fi

needs_record=0
if [ "$mode" = "record" ] || [ ! -f "$baseline_image" ] || [ "$baseline_bytes" -lt 500 ]; then
  needs_record=1
fi

if [ "$needs_record" -eq 1 ]; then
  echo "Recording baseline at $baseline_image"
  "$maestro_bin" test -e "APP_ID=${app_id}" -e "BASELINE_PATH=${baseline_stem}" "$record_flow_path" >"$log_file" 2>&1 || {
    cat "$log_file"
    exit 1
  }
  cat "$log_file"
fi

if [ "$mode" = "record" ]; then
  exit 0
fi

baseline_bytes=0
if [ -f "$baseline_image" ]; then
  baseline_bytes="$(wc -c <"$baseline_image" | tr -d ' ')"
fi

if [ "$baseline_bytes" -lt 500 ]; then
  echo "Baseline image $baseline_image is missing or too small (${baseline_bytes} bytes)"
  exit 1
fi

set +e
run_flow
first_status=$?
set -e

if [ "$platform" != "ios" ] || [ "$first_status" -eq 0 ]; then
  exit "$first_status"
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