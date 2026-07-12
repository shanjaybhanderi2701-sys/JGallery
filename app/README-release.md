# JGallery release build (perf-testing build config) — APP-455

All performance verification (grid scroll, frame-time) **must** use a **release** build, not a
debug build. Debug builds run without R8, are `debuggable`, and carry JIT/debug overhead that
distorts frame timing — the exact reason JD's original scroll test on a debug APK (APP-400 finding
1) was invalid.

## What the `release` buildType does
Configured in `build-logic/.../AndroidApplicationConventionPlugin.kt`:
- `isMinifyEnabled = true` — R8 code shrink + optimize.
- `isShrinkResources = true` — resource shrink.
- `proguardFiles(proguard-android-optimize.txt, app/proguard-rules.pro)` — R8 keep rules.
- `signingConfig = signingConfigs.getByName("release")` — the APK is **signed**, so it installs.

R8 keep rules live in `app/proguard-rules.pro`. They rely on library-supplied consumer rules
(Hilt, Compose, Media3, Coil, DataStore) and add only JGallery's own reflection surface
(persisted `MediaType` enum names) plus narrow `-dontwarn` safety nets. Do **not** add broad keeps —
over-keeping defeats the shrink and invalidates the perf build.

## §9.3 integrity under R8
The APP-289 egress guard runs per-variant, including `release`:
```
./gradlew :app:verifyNoEgressManifestRelease   # no INTERNET / network perm in merged manifest
./gradlew :app:verifyNoEgressDependenciesRelease
```
Green release egress guard == the "works fully on device, never uploaded" claim still holds for the
shipped APK. Verified on this config: the release merged manifest declares **zero** network
permissions.

## Building the APK
```
./gradlew :app:assembleRelease
# → app/build/outputs/apk/release/app-release.apk
```

## Signing — no secrets in the repo
The release signing config resolves the key in this precedence:
1. **Environment variables** (CI): `RELEASE_STORE_FILE` (path relative to repo root),
   `RELEASE_STORE_PASSWORD`, `RELEASE_KEY_ALIAS`, `RELEASE_KEY_PASSWORD`.
2. **`keystore.properties`** at the repo root (git-ignored): keys `storeFile`, `storePassword`,
   `keyAlias`, `keyPassword`.
3. **Fallback: committed `debug.keystore`** — the AOSP debug-key convention (store/key password
   `android`, alias `androiddebugkey`). This is **public, not a secret**; it is the one `*.keystore`
   whitelisted in `.gitignore`. It makes the APK installable for perf testing on any checkout, but
   is not a Play-upload key.

To sign with a real upload key without committing secrets, create `keystore.properties` (ignored) or
set the `RELEASE_*` env vars in CI. The key only affects installability, never R8/perf.

## Re-delivery to JD
This build config is the mechanism for the final re-delivery to JD. Route the actual APK delivery
through the QA release path (per APP-407 / the GitHub-Release delivery pattern), not a raw local
file. Device install + run smoke is a board/QA hands-on step (standing device-verify rule).
