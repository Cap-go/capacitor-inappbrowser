#!/usr/bin/env sh

set -eu

script_dir="$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)"
maestro_script="${1:-maestro:test:android}"

set +e
timeout 12m bun run "$maestro_script"
first_status=$?
set -e

if [ "$first_status" -eq 0 ]; then
  exit 0
fi

echo "Maestro Android run failed or timed out (exit $first_status). Retrying once after emulator warmup..."
timeout 30s adb wait-for-device
sh "$script_dir/wait-android-system-ready.sh"
timeout 12m bun run "$maestro_script"
