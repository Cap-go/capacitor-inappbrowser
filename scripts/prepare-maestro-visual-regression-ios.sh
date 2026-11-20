#!/usr/bin/env sh

set -eu
exec sh "$(CDPATH= cd -- "$(dirname "$0")" && pwd)/prepare-maestro-visual-regression.sh" ios