# :macrobenchmark — 10k+ scroll frame-time gate (spec §11 DoD, APP-342)

The Wave-2 **headline gate**: measure frame timing while scrolling a **10,000+ item** Photos grid.
This is the harness the DoD's *"operator-assisted physical-device 10k+ item frame-time pass
(`androidx.benchmark.macro` FrameTimingMetric or equivalent)"* clause was always meant to run.

## What it measures

`PhotosScrollBenchmark.scrollPhotosGrid10k` launches **`PhotosBenchmarkActivity`** (a
`benchmark`-variant-only Activity in `:app`) which renders the **real, stateless `PhotosScreen`**
over a fixed **10,500-item** fixture — no Hilt, no onboarding gate, no MediaStore. It then flings the
`photos_grid` `LazyVerticalGrid` and records `FrameTimingMetric`
(`frameDurationCpuMs` **P50 / P90 / P99**).

Isolating the stateless screen + synthetic fixture is deliberate: the gate is about the scroll hot
path (timeline cells → grid tile reuse + layout), so this measures composition/layout/draw cost
rather than disk-IO or thumbnail-decode variance. It mirrors how `PhotosGridTest` already drives the
same overload.

## How the pieces fit

| Piece | Where | Why |
|-------|-------|-----|
| `benchmark` build type | `AndroidApplicationConventionPlugin` | release-like, **non-debuggable**, debug-signed — macrobenchmark rejects debuggable builds (JIT/debug hooks distort timing) |
| `<profileable shell="true"/>` | `app/src/benchmark/AndroidManifest.xml` | lets macrobenchmark profile the non-debuggable APK |
| `PhotosBenchmarkActivity` | `app/src/benchmark/…` | benchmark-variant-only 10.5k fixture surface; sets `testTagsAsResourceId` so UiAutomator finds `photos_grid` |
| `:macrobenchmark` | this module (`com.android.test`) | separate test APK; `targetProjectPath = ":app"`, `testBuildType = "benchmark"` |

## Run it

**Physical device (authoritative — DoD headline pass):**
```bash
./gradlew :macrobenchmark:connectedBenchmarkAndroidTest
```
Results: `macrobenchmark/build/outputs/connected_android_test_additional_output/**` (per-iteration
`*-benchmarkData.json`) and the connected-test HTML report.

**Emulator (CI fallback — directional regression gate):**
```bash
./gradlew :macrobenchmark:connectedBenchmarkAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.androidx.benchmark.suppressErrors=EMULATOR
```
`suppressErrors=EMULATOR` is required because macrobenchmark aborts on non-physical hardware; the DoD
explicitly permits the emulator fallback. **Emulator numbers are directional only** — treat them as a
regression signal, not the authoritative physical measurement.

## CI

The `macrobenchmark` lane in `.github/workflows/ci.yml` runs the emulator fallback (API 30, pixel_5)
and uploads the frame-timing JSON as the `macrobenchmark-results` artifact.

## Operator-assisted physical pass

Per the DoD: with **no physical device available to CI**, the physical-device 10k+ frame-time pass
remains **operator-assisted**. QA re-verifies this harness and flags the physical pass accordingly.
To record the physical run, an operator runs the physical command above on a real device and attaches
the `*-benchmarkData.json` P50/P90/P99 to the APP-342 / APP-313 thread.
