#!/usr/bin/env bash
# APP-637 unit checks for the VARIANT-AWARE egress dependency policy.
#
# build-logic can't carry a Gradle `src/test` source set without breaking the configuration cache,
# so we compile the REAL EgressDependencyPolicy together with EgressPolicyChecks.kt in a single
# kotlinc module (so the checks can see the `internal` object) and run its `main`. Both the release
# (strict zero-egress) and debug (bounded Crashlytics allowlist) branches are asserted.
#
# Usage: ./scripts/verify_egress_policy.sh
# kotlinc is resolved from PATH, then from the Android Studio bundle. Exits non-zero on any failure.
set -euo pipefail
cd "$(dirname "$0")/.."

POLICY="build-logic/convention/src/main/kotlin/com/appblish/jgallery/convention/EgressDependencyPolicy.kt"
CHECKS="scripts/EgressPolicyChecks.kt"

# Prefer the Android Studio JBR (no `java` on PATH in this environment); kotlinc needs JAVA_HOME.
AS_JBR="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
[ -x "$AS_JBR/bin/java" ] && export JAVA_HOME="$AS_JBR"

# run_kotlinc <args...> — dispatch to PATH kotlinc, else the (non-exec) Android Studio bundle via bash.
AS_KOTLINC="/Applications/Android Studio.app/Contents/plugins/Kotlin/kotlinc/bin/kotlinc"
run_kotlinc() {
  if command -v kotlinc >/dev/null 2>&1; then
    kotlinc "$@"
  elif [ -f "$AS_KOTLINC" ]; then
    bash "$AS_KOTLINC" "$@"
  else
    echo "kotlinc not found (PATH or Android Studio bundle). Cannot run egress policy checks." >&2
    exit 2
  fi
}

OUT="$(mktemp -d)"
trap 'rm -rf "$OUT"' EXIT

echo "Compiling $POLICY + $CHECKS ..."
run_kotlinc "$POLICY" "$CHECKS" -include-runtime -d "$OUT/checks.jar" >/dev/null

echo "Running egress policy checks ..."
"$JAVA_HOME/bin/java" -jar "$OUT/checks.jar"
