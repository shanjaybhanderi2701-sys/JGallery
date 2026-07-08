#!/usr/bin/env bash
# Materialize the Gradle wrapper jar on a fresh clone (the scaffold ships scripts + properties but
# not the binary jar). Requires a local Gradle >= 8.10 on PATH. Run once, then commit the jar.
set -euo pipefail

GRADLE_VERSION="8.10.2"

if [ -f gradle/wrapper/gradle-wrapper.jar ]; then
  echo "gradle-wrapper.jar already present — nothing to do."
  exit 0
fi

if ! command -v gradle >/dev/null 2>&1; then
  echo "ERROR: 'gradle' not found. Install Gradle ${GRADLE_VERSION} (e.g. 'brew install gradle' or sdkman)." >&2
  exit 1
fi

echo "Generating Gradle wrapper ${GRADLE_VERSION}..."
gradle wrapper --gradle-version "${GRADLE_VERSION}" --distribution-type bin
echo "Done. Commit gradle/wrapper/gradle-wrapper.jar."
