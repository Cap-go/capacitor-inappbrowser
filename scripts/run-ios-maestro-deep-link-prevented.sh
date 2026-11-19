#!/usr/bin/env sh

set -eu

maestro_bin="${HOME}/.maestro/bin/maestro"
flow_path=".maestro/deep-link-prevented-ios.yaml"
app_id="${APP_ID:-app.capgo.inappbrowser}"

run_flow() {
  "$maestro_bin" test -e "APP_ID=${app_id}" "$flow_path" 2>&1 | tee /tmp/maestro-ios-deep-link.log
}

set +e
run_flow
first_status=$?
set -e

if [ "$first_status" -eq 0 ]; then
  exit 0
fi

if ! grep -q 'IOSDriverTimeoutException' /tmp/maestro-ios-deep-link.log; then
  exit "$first_status"
fi

echo "Maestro iOS deep link run failed with IOSDriverTimeoutException. Retrying once..."
sleep 5
run_flow