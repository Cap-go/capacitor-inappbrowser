#!/usr/bin/env bash
set -euo pipefail

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
    ./gradlew build test
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
