#!/usr/bin/env sh
# Chained after visual-regression / iOS deep-link Maestro scripts in CI.

set -eu

platform="${1:-}"
maestro_bin="${HOME}/.maestro/bin/maestro"
app_id="${APP_ID:-app.capgo.inappbrowser}"
script_dir="$(CDPATH= cd -- "$(dirname "$0")" && pwd)"
repo_root="$(CDPATH= cd -- "$script_dir/.." && pwd)"
flow_dir="$repo_root/example-app/.maestro"

if [ ! -x "$maestro_bin" ]; then
  maestro_bin="$(command -v maestro || true)"
fi

if [ -z "$maestro_bin" ] || [ ! -x "$maestro_bin" ]; then
  echo "Maestro CLI is not available"
  exit 1
fi

case "$platform" in
  android | ios)
    suffix="$platform"
    ;;
  *)
    echo "Expected platform android or ios"
    exit 1
    ;;
esac

run_flow() {
  flow_path="$1"
  log_file="/tmp/maestro-${platform}-native-ui-engine-$(basename "$flow_path").log"
  set +e
  "$maestro_bin" test -e "APP_ID=${app_id}" "$flow_path" >"$log_file" 2>&1
  status=$?
  cat "$log_file"
  return "$status"
}

for stem in off on fail; do
  flow_path="${flow_dir}/native-ui-engine-${stem}-${suffix}.yaml"
  if [ ! -f "$flow_path" ]; then
    echo "Missing Maestro flow ${flow_path}"
    exit 1
  fi
  echo "Running ${flow_path}"
  run_flow "$flow_path" || exit 1
done