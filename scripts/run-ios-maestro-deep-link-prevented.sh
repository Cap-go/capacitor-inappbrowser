#!/usr/bin/env sh

set -eu

maestro_bin="${HOME}/.maestro/bin/maestro"
flow_path=".maestro/deep-link-prevented-ios.yaml"
app_id="${APP_ID:-app.capgo.inappbrowser}"
log_file="/tmp/maestro-ios-deep-link.log"

run_flow() {
  set +e
  "$maestro_bin" test -e "APP_ID=${app_id}" "$flow_path" >"$log_file" 2>&1
  status=$?
  cat "$log_file"
  return $status
}

set +e
run_flow
first_status=$?
set -e

if [ "$first_status" -eq 0 ]; then
  sh "$(CDPATH= cd -- "$(dirname "$0")" && pwd)/run-maestro-native-ui-engine.sh" ios
  exit 0
fi

if ! grep -q 'IOSDriverTimeoutException' "$log_file"; then
  exit "$first_status"
fi

echo "Maestro iOS deep link run failed with IOSDriverTimeoutException. Retrying once..."
sleep 5
set +e
run_flow
retry_status=$?
set -e
if [ "$retry_status" -eq 0 ]; then
  sh "$(CDPATH= cd -- "$(dirname "$0")" && pwd)/run-maestro-native-ui-engine.sh" ios
fi
exit "$retry_status"