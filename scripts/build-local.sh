#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
# Configure JAVA_HOME and ANDROID_HOME (or local.properties) for your machine.
# CLOCK_GRADLE may point to a preinstalled Gradle executable.
if [[ -n "${CLOCK_GRADLE:-}" ]]; then
    exec "$CLOCK_GRADLE" --no-daemon "$@"
fi
exec sh ./gradlew --no-daemon "$@"
