#!/usr/bin/env sh

set -eu

platform="${1:-}"
mode="${2:-assert}"

case "$platform" in
  android)
    baseline_image=".maestro/visual-regression/baselines/android/blank-toolbar-layout.png"
    ;;
  ios)
    baseline_image=".maestro/visual-regression/baselines/ios/blank-toolbar-layout.png"
    ;;
  *)
    echo "Expected platform android or ios"
    exit 1
    ;;
esac

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

if [ -f "$baseline_image" ]; then
  baseline_for_maestro="$(CDPATH= cd -- "$(dirname "$baseline_image")" && pwd)/$(basename "$baseline_image")"
else
  baseline_for_maestro="$(pwd)/${baseline_image}"
fi

run_flow() {
  set +e
  "$maestro_bin" test -e "APP_ID=${app_id}" -e "BASELINE_PATH=${baseline_for_maestro}" "$flow_path" >"$log_file" 2>&1
  status=$?
  cat "$log_file"
  return "$status"
}

baseline_bytes=0
if [ -f "$baseline_image" ]; then
  baseline_bytes="$(wc -c <"$baseline_image" | tr -d ' ')"
fi

needs_record=0
if [ "$mode" = "record" ]; then
  needs_record=1
elif [ ! -f "$baseline_image" ] || [ "$baseline_bytes" -lt 500 ]; then
  echo "Baseline image $baseline_image is missing or too small (${baseline_bytes} bytes). Assert mode never records; run with mode record locally and commit the baseline."
  exit 1
fi

if [ "$needs_record" -eq 1 ]; then
  echo "Recording baseline at $baseline_image"
  "$maestro_bin" test -e "APP_ID=${app_id}" -e "BASELINE_PATH=${baseline_for_maestro}" "$record_flow_path" >"$log_file" 2>&1 || {
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

repo_root="$(CDPATH= cd -- "$(dirname "$0")/.." && pwd)"
native_ui_flow="${repo_root}/example-app/.maestro/native-ui-engine-off-${platform}.yaml"
if [ "$first_status" -eq 0 ] && [ -f "$native_ui_flow" ]; then
  sh "$(CDPATH= cd -- "$(dirname "$0")" && pwd)/run-maestro-native-ui-engine.sh" "$platform"
fi

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
if [ "$retry_status" -eq 0 ] && [ -f "$native_ui_flow" ]; then
  sh "$(CDPATH= cd -- "$(dirname "$0")" && pwd)/run-maestro-native-ui-engine.sh" "$platform"
fi
exit "$retry_status"