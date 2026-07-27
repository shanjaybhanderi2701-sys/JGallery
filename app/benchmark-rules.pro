# Benchmark-variant R8 keep rules (APP-699).
#
# Applied ONLY when the benchmark variant is built minified (`-Pjgallery.bench.minify=true`), on top
# of the shipped `release` rules (proguard-android-optimize.txt + proguard-rules.pro). Purpose: keep
# the benchmark-only fixture surface that R8 would otherwise treat as dead code in a release-like
# shrink, so the "true minified, release-like" benchmark target (APP-699) still launches and drives
# the real pipeline. This file never affects the shipped debug/release APK — it is referenced only by
# the benchmark build type.

# The fixture Activity is reached by name from the macrobenchmark (`am start-activity -n …
# PhotosBenchmarkActivity`) and from the benchmark manifest's launcher intent-filter. The manifest
# entry already keeps the class, but keep its members too so the intent-extra handling and Compose
# entry points are not stripped/renamed.
-keep class com.appblish.jgallery.benchmark.PhotosBenchmarkActivity { *; }

# Corpus seeder + decode/cache-hit counters: invoked reflectively via the Hilt entry point and read
# from the Activity; keep them and the Hilt @EntryPoint interface intact.
-keep class com.appblish.jgallery.benchmark.BenchmarkCorpusSeeder { *; }
-keep class com.appblish.jgallery.benchmark.BenchDecodeCounters { *; }
-keep interface com.appblish.jgallery.benchmark.BenchImageLoaderEntryPoint { *; }

# The counting Coil EventListener is instantiated only inside BenchDecodeCounters; keep the whole
# benchmark package's types as a safety net so no fixture-only symbol is shrunk away under R8.
-keep class com.appblish.jgallery.benchmark.** { *; }
