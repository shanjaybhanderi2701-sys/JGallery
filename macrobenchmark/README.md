# :macrobenchmark — 10k+ scroll frame-time gate (spec §11 DoD, APP-342)

The Wave-2 **headline gate**: measure frame timing while scrolling a **10,000+ item** Photos grid.
This is the harness the DoD's *"operator-assisted physical-device 10k+ item frame-time pass
(`androidx.benchmark.macro` FrameTimingMetric or equivalent)"* clause was always meant to run.

## What it measures

`PhotosScrollBenchmark.scrollPhotosGrid10k` launches **`PhotosBenchmarkActivity`** (a
`benchmark`-variant-only Activity in `:app`) which **seeds ≥10,000 REAL image files into MediaStore**
(`BenchmarkCorpusSeeder`) and renders the **real, stateless `PhotosScreen`** over tiles carrying
**REAL MediaStore row-ids**. It then flings the `photos_grid` `LazyVerticalGrid` and records
`FrameTimingMetric` (`frameDurationCpuMs` **P50 / P90 / P99**) plus a per-iteration **jank%** parsed
from `dumpsys gfxinfo` (logged as `JGALLERY_BENCH_JANK …`).

### Why real files now (APP-390 / R0-1 — the measurement fix)

The original harness rendered synthetic `MediaItem`s whose ids resolved to nothing (`bench-$i`), so
**every grid tile missed the thumbnail fetcher and drew a placeholder** — zero decode, zero IO. That
measured pure list/layout machinery and produced a **false ~1.9% jank pass**. A real scroll on a
device with 10k photos decodes thumbnails on the fly
(`ContentResolver.loadThumbnail` → JPEG re-encode → Coil decode, see `MediaStoreStorageAccess`),
which is where the jank actually lives.

`BenchmarkCorpusSeeder` writes ≥10,000 real, decodable files (varied **JPEG / PNG / HEIC / WebP**
across resolutions up to full **camera-res 4032×3024**) into `Pictures/JGalleryBench`, then queries
them back so tiles carry the real numeric row-ids the app's fetcher resolves to
`content://media/external/…/<id>`. Seeding is **idempotent** and runs off the main thread in the
(unmeasured) setup phase, so only the first iteration pays for it. A launch-time self-check decodes
one seeded thumbnail and logs `JGALLERY_BENCH_DECODE decodeOk=true …` as grep-able proof that the
corpus exercises real decode (not the old id-miss).

**Corpus size** defaults to the DoD floor of **10,000** and is overridable with
`-Pandroid.testInstrumentationRunnerArguments.jgallery.bench.corpusSize=N` (the CI lane uses a
smaller, still at-scale corpus to fit the hosted emulator's bounded disk/time; the full 10,000 is for
the authoritative physical/FTL run, R0-2).

## How the pieces fit

| Piece | Where | Why |
|-------|-------|-----|
| `benchmark` build type | `AndroidApplicationConventionPlugin` | release-like, **non-debuggable**, debug-signed — macrobenchmark rejects debuggable builds (JIT/debug hooks distort timing) |
| `<profileable shell="true"/>` | `app/src/benchmark/AndroidManifest.xml` | lets macrobenchmark profile the non-debuggable APK |
| `PhotosBenchmarkActivity` | `app/src/benchmark/…` | benchmark-variant-only surface; seeds the real corpus then renders `PhotosScreen`; sets `testTagsAsResourceId` so UiAutomator finds `photos_grid` |
| `BenchmarkCorpusSeeder` | `app/src/benchmark/…` | seeds ≥10k real JPEG/PNG/HEIC/WebP files into MediaStore (idempotent) so a fling drives real decode/IO |
| `:macrobenchmark` | this module (`com.android.test`) | separate test APK; `targetProjectPath = ":app"`, `testBuildType = "benchmark"` |

## APP-699 — real-pipeline mode, scale, cold pass, peak memory, decode counts

APP-697 found the harness above measured a **hand-built timeline fed to the stateless
`PhotosScreen`**, so it *bypassed* the production data path (Room → `CachedMediaIndexRepository` →
`PhotosViewModel` → `buildPhotosTimeline`) that the report named the **#1 scroll-perf suspect**.
APP-699 adds the missing coverage — **all harness/instrumentation code, no production behaviour
change**:

| Gap | What was added |
|-----|----------------|
| **Real-pipeline mode** | `PhotosBenchmarkActivity` is now `@AndroidEntryPoint` and, under `--ez bench_real_pipeline true`, renders the **real Hilt `PhotosScreen()`** (default `hiltViewModel()`), so the fling drives the production Room→repo→VM→timeline path. |
| **Scale 5k / 15k / 50k** | `Scale` enum; each `@Test` seeds its own corpus. 5k/15k run locally now; **50k is infra-gated** (needs a large/repartitioned AVD or physical device, ~8–12 GB; the shared AVD has ~3.8 GB free). |
| **Cold pass** | `realPipeline_15k_cold` runs `StartupMode.COLD` (WARM caveat from APP-382 still applies on the emulator — treat COLD emulator numbers as directional). |
| **Peak memory** | Per-iteration `dumpsys meminfo` sampled across both scroll passes; logged `JGALLERY_BENCH_MEM peakPssKb=… javaHeapKb=… nativeHeapKb=… totalPssKb=…`. |
| **decode-count + cache-hit** | Real-pipeline mode wraps the **real Coil `ImageLoader`** (same caches) with a counting `EventListener` (harness-only, via `SingletonImageLoader.setSafe`). The test flings **down then back up**; the app logs `JGALLERY_BENCH_DECODE_COUNTS decodeCount=… cacheHit=… success=…` on a timer, so decodes plateau while cache hits climb on the return fling — the **zero-re-decode** proof. |
| **True minified R8 target** | `-Pjgallery.bench.minify=true` flips the `benchmark` build type to `isMinifyEnabled`/`isShrinkResources` = true with the shipped `release` R8 config + `app/benchmark-rules.pro`, so numbers reflect the shrunk code graph. Off by default. |
| **Self-cleaning (hardened)** | Teardown deletes every seeded MediaStore row **and** drops the rebuildable Room index cache the real-pipeline sync populated (`deleteDatabase("media-index.db")`), then verifies `remaining=0`. |

### Run the new lanes

```bash
# real-pipeline, local emulator, 5k then 15k (the "runs locally now" DoD gate)
./gradlew :macrobenchmark:connectedBenchmarkAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=com.appblish.jgallery.macrobenchmark.PhotosScrollBenchmark#realPipeline_5k_warm \
  -Pandroid.testInstrumentationRunnerArguments.androidx.benchmark.suppressErrors=EMULATOR

# true minified R8 release-like target
./gradlew :macrobenchmark:connectedBenchmarkAndroidTest -Pjgallery.bench.minify=true \
  -Pandroid.testInstrumentationRunnerArguments.androidx.benchmark.suppressErrors=EMULATOR

# 50k (authoritative) — only on a large-AVD / physical device (see infra note above)
./gradlew :macrobenchmark:connectedBenchmarkAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=com.appblish.jgallery.macrobenchmark.PhotosScrollBenchmark#realPipeline_50k_warm
```

Artifacts per run: FrameTimingMetric P50/P90/P99 in `*-benchmarkData.json`, plus grep-able logcat
lines `JGALLERY_BENCH_JANK` (jank%), `JGALLERY_BENCH_MEM` (peak memory), and
`JGALLERY_BENCH_DECODE_COUNTS` (decode/cache-hit).

## Self-cleaning fixture (APP-458)

The seeder writes thousands of real files into MediaStore, so the fixture is built to **leave the
device's photo library exactly as it found it** and to **never seed by accident**:

- **Opt-in gate.** `PhotosBenchmarkActivity` seeds **only** when launched with `--ez bench_opt_in
  true` (the macrobenchmark passes it). A plain tap-launch of the sideloaded benchmark APK shows a
  **guard screen** and seeds nothing — a stray tap can no longer pollute a real library (this was the
  APP-400 finding-6 leak: 1,316 synthetic files stranded in JD's library).
- **Automatic teardown.** `PhotosScrollBenchmark.tearDownCorpus()` (`@After`, so it runs on
  **success and on failure/abort**) drives the app into cleanup mode and deletes every seeded row.
- **One-shot purge** (recover assets already leaked onto a device):
  ```bash
  adb shell am start -n com.appblish.jgallery/.benchmark.PhotosBenchmarkActivity --ez bench_cleanup true
  ```
  or tap **"Remove any leaked benchmark assets from this device"** on the guard screen. Cleanup is
  idempotent and safe on a clean device.
- **Bounded deletion.** Every count/query/delete is scoped by a single selection —
  `RELATIVE_PATH LIKE 'Pictures/JGalleryBench/%'` — so cleanup can **never** touch real user media,
  and a defensive `check()` refuses to run if that sentinel namespace is ever weakened.
- **Proof of a clean library.** Cleanup logs `JGALLERY_BENCH_CLEANUP deleted=N remaining=M`; a clean
  run ends with `remaining=0`. The teardown logs `JGALLERY_BENCH_TEARDOWN cleanupConfirmed=true`.

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

The `macrobenchmark` lane in `.github/workflows/ci.yml` runs the emulator fallback (API 34, pixel_5,
`corpusSize=3000`) and uploads the frame-timing JSON as the `macrobenchmark-results` artifact. The
per-iteration jank% is in the run's logcat (`JGALLERY_BENCH_JANK …`).

### COLD launch display/focus race (APP-382)

On the CI emulator the COLD launch **intermittently** places `PhotosBenchmarkActivity` on a
**secondary display / unfocused window** while `UiAutomation` only queries **display 0** — so the
finder sees no `photos_grid` / scrollable node and the gate emits no numbers. It is a race, not a
hard failure: in a failing run iteration 0 rendered and scrolled fine (produced a trace) while
iteration 1 found nothing. Pinning the launch to display 0 alone did **not** fix it.

The failure is caused by the **COLD process-kill between iterations**, not by the first launch.
Evidence from the uploaded artifacts of two failed COLD runs: iteration 0 always rendered + scrolled
fine (produced a perfetto trace), but after the between-iteration process kill, iteration 1
relaunched onto a display/window `UiAutomation` (display 0) could not see — the dumped hierarchy
showed **only `com.android.systemui`**, no app window — and warm re-launches *within* that iteration
never recovered it. Pinning the launch to `--display 0` alone did **not** fix it.

The ticket's own diagnosis is decisive: a **WARM `am start` (process still alive) reliably exposes
the grid** on display 0. So the benchmark now runs with **`StartupMode.WARM`** instead of `COLD`:
the target process is kept warm across iterations, so the display-0 window association from the
first launch persists and every iteration stays queryable. The setup block issues `am start-activity
-W … --display 0` (benchmark 1.3.3 exposes no supported launch-display id through the `Intent` /
`startActivityAndWait` API) with a short retry/re-probe as belt-and-suspenders for the first launch.
The whole loop runs in `setupBlock` (unmeasured), so it never touches `FrameTimingMetric` — only the
fling loop is measured.

**Trade-off:** WARM measures a warm-process scroll rather than a cold first render — the right signal
for this *directional scroll-jank regression* lane (it isolates scroll cost from cold-start IO
noise). The authoritative COLD 10k+ frame-time headline is covered by the board-confirmed
operator-assisted physical pass (below), not by this emulator lane.

## Operator-assisted physical pass

Per the DoD: with **no physical device available to CI**, the physical-device 10k+ frame-time pass
remains **operator-assisted**. QA re-verifies this harness and flags the physical pass accordingly.
To record the physical run, an operator runs the physical command above on a real device and attaches
the `*-benchmarkData.json` P50/P90/P99 to the APP-342 / APP-313 thread.
