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
- Stored **outside the repo tree** at
  `…/companies/<companyId>/secrets/jgallery-upload.jks` (never committed).
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

## Zero-network guarantee (§9.3) holds in the bundle

The APP-289 egress guard runs on the `release` variant; the merged manifest declares **zero**
network permissions (no `INTERNET`, no `ACCESS_NETWORK_STATE`):
```
./gradlew :app:verifyNoEgressManifestRelease :app:verifyNoEgressDependenciesRelease
```
Confirm directly from the built AAB's base manifest:
```
# uses-permission list — expect NO android.permission.INTERNET
unzip -p app-release.aab base/manifest/AndroidManifest.xml | strings | grep -i permission
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
