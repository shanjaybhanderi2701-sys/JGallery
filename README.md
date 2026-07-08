# JGallery

A standalone, high-performance Android smart gallery (Jetpack Compose + Kotlin). **Separate from
CalcVault** — its own repo/workspace. This is the Wave-1 scaffold for [Phase G1](#).

> Performance is the whole point: Samsung-Gallery-grade scrolling, cached thumbnails, no stutter at
> 10,000+ items. See the Phase-G1 spec (issue APP-267) for the full requirements.

## Module architecture

```
:app                     4-tab shell (Albums | Photos | Collections | Search), nav, DI wiring
:core:model              pure-Kotlin domain types (no Android — cannot touch file APIs)
:core:storage            §1.6 storage-access abstraction — the ONLY file/media boundary
:core:index              cached, incremental MediaStore-backed index (consumes :core:storage)
:core:thumbs             cached thumbnail pipeline: in-memory LRU + on-disk (consumes :core:storage)
:core:ui                 theme scaffolding (signed-off W1 tokens) + shared Compose components
:feature:albums          Albums tab            :feature:photos    Photos tab
:feature:collections     placeholder (G4)      :feature:search    placeholder (G3)
:feature:viewer          full-screen viewer
:lint:storage-boundary   custom lint rule enforcing the §1.6 boundary (build-failing)
build-logic              convention plugins — the single place module config lives
```

## The §1.6 boundary is structural, not conventional

Spec hard-rule 6: **all** file/media access goes through one abstraction so the permission model
(All Files Access today) can be swapped for media permissions / SAF without rewriting features. We
enforce this three ways:

1. **Dependency direction** — only `:core:storage` depends on platform file/media APIs. Features
   depend on `:core:index` / `:core:thumbs` (which expose models/repositories), never on
   `:core:storage`'s implementation.
2. **A custom lint check** (`RawStorageAccess`, `:lint:storage-boundary`) fails the build if any
   module references `java.io.File`, `MediaStore`, `ContentResolver` file IO, `Environment`, or
   `MANAGE_EXTERNAL_STORAGE`.
3. **Convention-plugin wiring** — that lint check is applied to every module *except* `:core:storage`
   (which uses the privileged `jgallery.android.storage` convention). A module can only gain raw file
   access by editing `build-logic`, which is obvious in review.

## Build & verify

```bash
./scripts/bootstrap.sh          # one-time: generate the Gradle wrapper jar (needs local Gradle 8.10+)
./gradlew assembleDebug         # compile everything
./gradlew lint                  # static analysis incl. the storage-boundary check
./gradlew testDebugUnitTest     # JVM unit tests
./gradlew :lint:storage-boundary:test          # prove the boundary rule fires
./gradlew connectedDebugAndroidTest            # instrumented tests (needs a device/emulator)
```

CI (`.github/workflows/ci.yml`) runs all of the above; the Wave-1 DoD is verified by the
**instrumented-test lane** on an API-30 emulator, not by manual screenshots.

## Tech

Kotlin 2.0 · AGP 8.7 · Compose (BOM 2024.09) · Hilt · Coil (thumbnails) · Media3 (video) ·
version catalog + convention plugins · minSdk 29 / target 34 · **All Files Access** permission model.
