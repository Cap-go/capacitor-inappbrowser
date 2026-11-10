#!/usr/bin/env bash
set -euo pipefail


assert_packed_consumer_rules_applied() {
  local tarball="$1"
  local plugin_root="${2:-}"
  if [ ! -f "$tarball" ]; then
    echo "Packed tarball not found: $tarball"
    exit 1
  fi

  local listing
  listing="$(tar -tzf "$tarball")"
  if ! printf '%s\n' "$listing" | grep -E '(^|/)android/consumer-rules\.pro$' >/dev/null; then
    echo "Packed package is missing android/consumer-rules.pro"
    exit 1
  fi

  local config_file=""
  if [ -f "app/build/outputs/mapping/release/configuration.txt" ]; then
    config_file="app/build/outputs/mapping/release/configuration.txt"
  else
    config_file="$(find app/build -path '*release*' -name configuration.txt 2>/dev/null | head -n 1 || true)"
  fi
  if [ -z "${config_file}" ] || [ ! -f "${config_file}" ]; then
    echo "R8 configuration.txt not found after assembleRelease"
    exit 1
  fi

  local extract_dir
  extract_dir="$(mktemp -d)"
  tar -xzf "$tarball" -C "${extract_dir}"
  local rules_file
  rules_file="$(find "${extract_dir}" -name consumer-rules.pro | head -n 1 || true)"
  if [ -z "${rules_file}" ]; then
    echo "Could not extract android/consumer-rules.pro from packed package"
    exit 1
  fi

  local source_rules=""
  if [ -n "${plugin_root}" ] && [ -f "${plugin_root}/android/consumer-rules.pro" ]; then
    source_rules="${plugin_root}/android/consumer-rules.pro"
  elif [ -n "${plugin_root}" ] && [ -f "${plugin_root}/android/src/main/consumer-rules.pro" ]; then
    source_rules="${plugin_root}/android/src/main/consumer-rules.pro"
  fi
  if [ -z "${source_rules}" ]; then
    echo "Repo consumer-rules.pro not found"
    exit 1
  fi

  flatten_rule() {
    local flat
    flat="$(printf '%s' "$1" | tr -s '[:space:]' ' ')"
    flat="${flat# }"
    flat="${flat% }"
    printf '%s' "${flat}"
  }

  collect_keep_blocks() {
    local file="$1"
    local block="" line trimmed flat_block
    flush_keep_block() {
      [ -z "${block}" ] && return 0
      flat_block="$(flatten_rule "${block}")"
      block=""
      [ -z "${flat_block}" ] && return 0
      case "${flat_block}" in
        -keepattributes*)
          return 0
          ;;
      esac
      printf '%s\n' "${flat_block}"
    }
    while IFS= read -r line || [ -n "${line}" ]; do
      trimmed="${line#"${line%%[![:space:]]*}"}"
      if [ -z "${trimmed}" ] || [ "${trimmed#\#}" != "${trimmed}" ]; then
        continue
      fi
      if [ "${trimmed#-keep}" != "${trimmed}" ]; then
        flush_keep_block
        block="${trimmed}"
        if [ "${trimmed%\}}" != "${trimmed}" ]; then
          flush_keep_block
        fi
        continue
      fi
      if [ -n "${block}" ]; then
        block="${block} ${trimmed}"
        if [ "${trimmed%\}}" != "${trimmed}" ]; then
          flush_keep_block
        fi
      fi
    done < "${file}"
    flush_keep_block
  }

  local source_blocks packed_blocks
  source_blocks="$(collect_keep_blocks "${source_rules}")"
  packed_blocks="$(collect_keep_blocks "${rules_file}")"
  if [ -z "${source_blocks}" ]; then
    echo "No plugin keep rules found in ${source_rules}"
    exit 1
  fi

  local missing=0
  local rules_checked=0
  local plugin_method_rules=0
  local flat_block
  while IFS= read -r flat_block || [ -n "${flat_block}" ]; do
    [ -z "${flat_block}" ] && continue
    if ! grep -qF -- "${flat_block}" <<< "${packed_blocks}"; then
      echo "Packed consumer-rules.pro is missing repo keep rule: ${flat_block}"
      missing=1
    fi
  done <<< "${source_blocks}"

  local config_flat
  config_flat="$(tr -s '[:space:]' ' ' < "${config_file}")"
  while IFS= read -r flat_block || [ -n "${flat_block}" ]; do
    [ -z "${flat_block}" ] && continue
    rules_checked=$((rules_checked + 1))
    if ! grep -qF -- "${flat_block}" <<< "${config_flat}"; then
      echo "Merged R8 configuration is missing packed keep rule: ${flat_block}"
      missing=1
    fi
    if grep -qE -- '@([[:alnum:]_]+\.)*PluginMethod' <<< "${flat_block}"; then
      plugin_method_rules=$((plugin_method_rules + 1))
      if ! grep -qE -- '@([[:alnum:]_]+\.)*PluginMethod' <<< "${config_flat}"; then
        echo "Merged R8 configuration is missing the @PluginMethod member directive from packed consumer rules"
        missing=1
      fi
    fi
  done <<< "${packed_blocks}"

  if [ "${rules_checked}" -eq 0 ]; then
    echo "No plugin keep rules found in packed consumer-rules.pro"
    exit 1
  fi
  if [ "${missing}" -ne 0 ]; then
    echo "Packed consumer-rules.pro was not applied in ${config_file}"
    exit 1
  fi
  echo "Packed consumer-rules.pro keep rules are present in merged R8 configuration (${rules_checked} rules, ${plugin_method_rules} with @PluginMethod members)"
}




platform="${1:-}"
case "$platform" in
  android | ios | web) ;;
  *)
    echo "Usage: $0 <android|ios|web>"
    exit 1
    ;;
esac

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
tmp_root="${RUNNER_TEMP:-$(mktemp -d)}"
pack_dir="$tmp_root/plugin-package"
test_app="$tmp_root/plugin-example-app"

cd "$repo_root"

bun run build

rm -rf "$pack_dir" "$test_app"
mkdir -p "$pack_dir" "$test_app"
bun pm pack --destination "$pack_dir" --quiet

shopt -s nullglob
packed_packages=("$pack_dir"/*.tgz)
shopt -u nullglob
if [ "${#packed_packages[@]}" -ne 1 ]; then
  echo "Expected exactly one package tarball, found ${#packed_packages[@]}"
  exit 1
fi

# Any bun command that installs runs the example app's postinstall scripts, and the sharp prebuild
# download flakes on the runners, so retry them all rather than only the plugin install.
run_with_retries() {
  local max_attempts=3
  local attempt=1
  while true; do
    if "$@"; then
      return 0
    fi

    echo "'$*' failed on attempt $attempt/$max_attempts"
    if [ "$attempt" -eq "$max_attempts" ]; then
      return 1
    fi

    rm -rf node_modules
    attempt=$((attempt + 1))
    sleep $((attempt * 15))
  done
}

plugin_name="$(bun -e 'console.log(require("./package.json").name)')"
cp -R example-app/. "$test_app/"
cd "$test_app"
run_with_retries bun remove "$plugin_name"
run_with_retries bun add "${packed_packages[0]}"

bun run build

case "$platform" in
  android)
    if [[ ! -d android ]]; then
      bunx cap add android
    fi
    bunx cap sync android
    cd android
    ./gradlew build test assembleRelease
    assert_packed_consumer_rules_applied "${packed_packages[0]}" "$repo_root"
    ;;
  ios)
    if [[ ! -d ios ]]; then
      bunx cap add ios
    fi
    bunx cap sync ios
    rm -rf "$HOME/Library/Caches/org.swift.swiftpm/artifacts"/https___github_com_ionic_team_capacitor_swift_pm_releases_download_*
    xcodebuild \
      -project ios/App/App.xcodeproj \
      -scheme App \
      -destination generic/platform=iOS \
      -clonedSourcePackagesDirPath "$tmp_root/plugin-example-swiftpm" \
      -derivedDataPath "$tmp_root/plugin-example-derived-data" \
      CODE_SIGNING_ALLOWED=NO
    ;;
  web)
    ;;
esac
