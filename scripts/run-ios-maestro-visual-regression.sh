#!/usr/bin/env sh

set -eu
exec sh "$(CDPATH= cd -- "$(dirname "$0")" && pwd)/run-maestro-visual-regression.sh" ios assert