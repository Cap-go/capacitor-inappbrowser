#!/usr/bin/env sh

set -eu

package_id="${1:-app.capgo.inappbrowser}"
activity_name="${2:-app.capgo.inappbrowser/.MainActivity}"

adb wait-for-device >/dev/null
adb shell pm clear "$package_id" >/dev/null
adb shell am start -W -n "$activity_name" --ez capgo_maestro_autorun_proxy_regression true >/dev/null
