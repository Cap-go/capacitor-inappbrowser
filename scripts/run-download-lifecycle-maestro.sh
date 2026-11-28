#!/usr/bin/env sh
# Runs the download lifecycle Maestro flow (issue #794) against the local test server.
# Usage: sh ./scripts/run-download-lifecycle-maestro.sh android|ios
# Android expects the debug app to be installed and started (see maestro:test:android:download-lifecycle).
# iOS expects the example app to be installed on the booted simulator.

set -eu

platform="${1:?usage: run-download-lifecycle-maestro.sh android|ios}"
case "$platform" in
  android | ios) ;;
  *)
    echo "Unknown platform: $platform" >&2
    exit 2
    ;;
esac

port="${DOWNLOAD_LIFECYCLE_PORT:-8787}"
app_id="${APP_ID:-app.capgo.inappbrowser}"
maestro_bin="${MAESTRO_BIN:-${HOME}/.maestro/bin/maestro}"
artifacts_dir="${MAESTRO_ARTIFACTS_DIR:-maestro-artifacts}/download-lifecycle-${platform}"
flow_path=".maestro/download-lifecycle-${platform}.yaml"
server_log="${artifacts_dir}/download-lifecycle-server.log"

mkdir -p "$artifacts_dir"

# Hard stop after 9 minutes so the server never outlives the 10 minute job.
DOWNLOAD_LIFECYCLE_EXIT_AFTER_MS="${DOWNLOAD_LIFECYCLE_EXIT_AFTER_MS:-540000}" \
  node example-app/scripts/download-lifecycle-server.mjs "$port" >"$server_log" 2>&1 &
server_pid=$!
trap 'kill "$server_pid" 2>/dev/null || true' EXIT INT TERM

attempt=0
until curl -fsS "http://127.0.0.1:${port}/health" >/dev/null 2>&1; do
  attempt=$((attempt + 1))
  if [ "$attempt" -ge 40 ]; then
    echo "Download lifecycle server did not start" >&2
    cat "$server_log" >&2 || true
    exit 1
  fi
  sleep 0.5
done
if ! kill -0 "$server_pid" 2>/dev/null; then
  echo "Download lifecycle server exited early (is port ${port} already in use?)" >&2
  cat "$server_log" >&2 || true
  exit 1
fi
echo "Download lifecycle server ready on port ${port}"

device_args=""
if [ -n "${MAESTRO_DEVICE:-}" ]; then
  device_args="--device ${MAESTRO_DEVICE}"
fi

set +e
# shellcheck disable=SC2086
"$maestro_bin" $device_args test \
  --format junit \
  --output "${artifacts_dir}/maestro-report.xml" \
  --debug-output "$artifacts_dir" \
  --flatten-debug-output \
  -e "APP_ID=${app_id}" \
  "$flow_path"
status=$?
set -e

echo "--- download lifecycle server log ---"
cat "$server_log" || true
exit "$status"
