#!/usr/bin/env bash
# APP-289 DoD proof: the egress guard goes RED on introduced egress and GREEN on a clean tree.
#
# Run from the repo root on a machine with a JDK + Android SDK (e.g. the APP-281 CI runner):
#   ./scripts/prove_egress_guard.sh
#
# Each red scenario is applied to the working tree, proven to FAIL `:app:verifyNoEgress`, then
# reverted (throwaway — nothing is committed). Exits 0 only if all reds fail and the final
# green pass succeeds.
set -u
cd "$(dirname "$0")/.."

GRADLE="./gradlew --console=plain -q"
APP_MANIFEST="app/src/main/AndroidManifest.xml"
APP_BUILD="app/build.gradle.kts"
SEARCH_SCREEN="feature/search/src/main/java/com/appblish/jgallery/feature/search/SearchScreen.kt"
FAILED=0

restore() { git checkout -- "$@" 2>/dev/null || git restore "$@"; }

expect_red() {
  local label="$1"
  if $GRADLE :app:verifyNoEgress >/tmp/egress_guard_out.txt 2>&1; then
    echo "✗ $label: guard stayed GREEN — DoD NOT met"; FAILED=1
  else
    echo "✓ $label: guard went RED as required"
    grep -m1 "VIOLATION" /tmp/egress_guard_out.txt || true
  fi
}

# NOTE (APP-614): INTERNET + ACCESS_NETWORK_STATE were deliberately removed from the manifest
# denylist so Firebase Crashlytics can upload crash reports (board Option A, APP-613). They now
# pass GREEN by design. The manifest check still reds on every OTHER network-capable permission,
# which is what RED 1 now proves.
echo "== RED 1: a still-forbidden network permission (CHANGE_WIFI_STATE) in the merged manifest =="
perl -0pi -e 's|(<manifest[^>]*>)|$1\n    <uses-permission android:name="android.permission.CHANGE_WIFI_STATE" />|' "$APP_MANIFEST"
expect_red "CHANGE_WIFI_STATE permission"
restore "$APP_MANIFEST"

echo "== RED 2: network dependency (okhttp) in the resolved runtime graph =="
perl -0pi -e 's|(dependencies \{)|$1\n    implementation("com.squareup.okhttp3:okhttp:4.12.0")|' "$APP_BUILD"
expect_red "okhttp dependency"
restore "$APP_BUILD"

echo "== RED 3: trust-claim copy outside the registered files =="
printf '\n// throwaway (prove_egress_guard.sh)\nprivate const val STRAY_CLAIM = "your photos are never uploaded"\n' >> app/src/main/java/com/appblish/jgallery/MainActivity.kt
expect_red "unregistered trust claim"
restore app/src/main/java/com/appblish/jgallery/MainActivity.kt

echo "== RED 4 (APP-619 Finding 1): a NEW Firebase artifact under the approved group =="
# firebase-messaging is a real egress/data surface under com.google.firebase (an APPROVED group)
# but is NOT in APPROVED_GOOGLE_ARTIFACTS. Pre-APP-619 the group prefix would have passed it
# GREEN; the closed-world artifact set must now turn the build RED. Version comes from the BoM.
perl -0pi -e 's|(dependencies \{)|$1\n    implementation("com.google.firebase:firebase-messaging")|' "$APP_BUILD"
expect_red "unapproved Firebase artifact (firebase-messaging)"
restore "$APP_BUILD"

echo "== GREEN: clean tree =="
if $GRADLE :app:verifyNoEgress; then
  echo "✓ green on clean tree"
else
  echo "✗ guard is RED on the clean tree — fix before merging"; FAILED=1
fi

exit $FAILED
