#!/usr/bin/env sh

set -eu

script_dir="$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)"
maestro_script="${1:-maestro:test:android}"

run_maestro_target() {
  case "$maestro_script" in
    *.sh)
      if [ -f "$maestro_script" ]; then
        sh "$maestro_script"
      else
        sh "$script_dir/$maestro_script"
      fi
      ;;
    *)
      if [ -f "$script_dir/${maestro_script}.sh" ]; then
        sh "$script_dir/${maestro_script}.sh"
      else
        bun run "$maestro_script"
      fi
      ;;
  esac
}

set +e
run_maestro_target
first_status=$?
set -e

if [ "$first_status" -eq 0 ]; then
  exit 0
fi

echo "Maestro Android run failed or timed out (exit $first_status). Retrying once after emulator warmup..."
timeout 30s adb wait-for-device
sh "$script_dir/wait-android-system-ready.sh"
set +e
run_maestro_target
retry_status=$?
set -e
exit "$retry_status"