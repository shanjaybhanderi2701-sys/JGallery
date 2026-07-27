# JGallery — Production release recipe (APP-513)

One-command path to a **signed, Play-ready Android App Bundle (`.aab`)** for the 1.0 launch.
This document is the launch runbook; publishing itself is a **separate, gated** step (do NOT publish
from here).

## Version scheme (1.0)

Set in `build-logic/.../AndroidApplicationConventionPlugin.kt` `defaultConfig`:

| field         | value   | meaning                                                              |
|---------------|---------|---------------------------------------------------------------------|
| `versionName` | `1.0.0` | human-facing Play listing string (semver MAJOR.MINOR.PATCH).        |
| `versionCode` | `1`     | monotonic integer Play orders uploads by.                           |

**Rule:** bump `versionCode` (never reuse or decrease) on **every** upload to Play, independent of
`versionName`. Play rejects an upload whose `versionCode` is ≤ one already in that track.

## The upload key vs. Play App Signing

We use **Play App Signing** (recommended by Google):

- **Google holds the *app signing key*** — the key that actually signs APKs delivered to devices.
  We never see it; Google generates/manages it on enrollment.
- **We hold the *upload key*** — the key we sign the `.aab` with before uploading. Play verifies the
  upload key, strips our signature, and re-signs with the app signing key.
- If the upload key is ever lost/compromised, Google can **reset** it (a lost app signing key would
  be unrecoverable — that's the whole point of the split).

### Our upload key

- Type: PKCS12, RSA-2048, 10000-day validity, alias **`jgallery-upload`**.
- Stored **outside the repo tree** in the release engineer's per-agent `secure-release/`
  directory (mode `0700`), alongside `credentials.env` which holds the `JGALLERY_RELEASE_*`
  store/key passwords. Never committed; the exact path is registered in `credentials.env`, not in
  this repo.
- Fingerprints (record these; Play shows the upload cert on enrollment — they must match):
  - **SHA-1:**   `79:3B:41:4C:40:F4:5B:4E:4B:8A:0A:B5:51:E6:56:9C:FE:D2:74:A7`
  - **SHA-256:** `A4:AC:52:60:1F:6B:2B:FB:B6:44:B4:76:97:03:C9:D3:EF:B0:A3:4D:CC:EB:D3:F0:90:6F:AF:77:16:0F:78:F2`

Regenerate (only if starting fresh — Play upload key resets require a support flow):
```
keytool -genkeypair -v -keystore jgallery-upload.jks -storetype PKCS12 \
  -alias jgallery-upload -keyalg RSA -keysize 2048 -validity 10000 \
  -dname "CN=JGallery, OU=Mobile, O=Appblish, C=US"
```

## Signing config — no secrets in the repo

The `release` `signingConfig` (`AndroidApplicationConventionPlugin.kt`) resolves the key in
precedence order:

1. **Env vars (CI):** `RELEASE_STORE_FILE` (absolute or repo-relative path),
   `RELEASE_STORE_PASSWORD`, `RELEASE_KEY_ALIAS`, `RELEASE_KEY_PASSWORD`.
2. **`keystore.properties`** at repo root (**gitignored**): keys `storeFile` (absolute path to the
   off-tree upload key), `storePassword`, `keyAlias`, `keyPassword`.
3. **Fallback: committed `debug.keystore`** — AOSP debug convention (public, not a secret). Makes
   local/perf APKs installable; **never** a Play upload key.

`keystore.properties` and all `*.keystore`/`*.jks` (except `debug.keystore`) are gitignored — the
build produces a signed artifact without any secret entering version control.

## Build the signed AAB (one command)

```
# JDK 21 (Android Studio JBR) + Android SDK on PATH; keystore.properties present at repo root.
./gradlew :app:bundleRelease
# → app/build/outputs/bundle/release/app-release.aab
```

Verify the upload signature on the bundle:
```
jarsigner -verify -verbose -certs app/build/outputs/bundle/release/app-release.aab
```

## Bounded egress — Firebase Crashlytics only (APP-614)

The board chose **Option A** (APP-613): ship Firebase Crashlytics for 1.0. The §9.3 *zero-network*
guarantee is retired; the APP-289 egress guard is now a **bounded** guard. On the `release`
variant it permits **only** the Firebase surface — `INTERNET` + `ACCESS_NETWORK_STATE`, and
`com.google.firebase` / `com.google.android.gms` / `com.google.android.datatransport` deps — and
still fails the build on any **other** network permission or network/analytics dependency:
```
./gradlew :app:verifyNoEgressManifestRelease :app:verifyNoEgressDependenciesRelease
```
Confirm the bundle's egress surface is exactly the two approved permissions and nothing else:
```
# expect ONLY android.permission.INTERNET + android.permission.ACCESS_NETWORK_STATE
unzip -p app-release.aab base/manifest/AndroidManifest.xml | strings | grep -i permission
```

> **Play gate:** the §9.3 "never uploaded" store/trust/Data-safety copy must be pulled (APP-616)
> **before** the 1.0.0 Play upload (APP-517), so store claims stay truthful now that crash
> telemetry is uploaded.

## Release symbol upload — Crashlytics mapping (APP-614)

`bundleRelease` (and `assembleRelease`) auto-runs `uploadCrashlyticsMappingFile<Variant>` because
the `com.google.firebase.crashlytics` plugin is applied and `mappingFileUploadEnabled = true` on the
`release` buildType (`AndroidApplicationConventionPlugin` / `app/build.gradle.kts`). This uploads the
R8 mapping file to Firebase (project `jgallery-5b48b`) so obfuscated production stack traces
de-obfuscate in the Crashlytics console. Debug/benchmark variants have upload **disabled**.

- **Auth:** the mapping upload is keyed by the app's `mobilesdk_app_id` from
  `app/google-services.json` — no service account is required for a Kotlin/JVM app. For CI or
  authenticated/scripted upload, export the provisioned service account:
  `GOOGLE_APPLICATION_CREDENTIALS=~/.config/paperclip/secrets/jgallery-crashlytics-sa.json`.
- **NDK symbols:** **not applicable** — JGallery ships no native code (`nativeSymbolUploadEnabled =
  false`). Flip that flag (and add the NDK toolchain) only if a native dependency is ever added.
- Force a standalone upload if needed:
  ```
  ./gradlew :app:uploadCrashlyticsMappingFileRelease
  ```

## Enroll in Play App Signing (Play Console — gated, done at publish time)

1. Play Console → create the app → **Setup → App integrity → App signing**.
2. Choose **"Let Google manage and protect your app signing key"** (default for new apps).
3. Upload the `.aab` to an **internal testing** track first; Play generates the app signing key and
   registers our **upload cert** (verify the SHA-256 above matches what Play displays).
4. From then on: sign every `.aab` with the **upload** key and upload; Google re-signs for devices.

## Do NOT publish here

Producing and verifying the `.aab` is the scope of APP-513. Actual store submission/rollout is the
**gated publish child** — it requires the Play Console account, listing assets, and a launch
go-decision.
