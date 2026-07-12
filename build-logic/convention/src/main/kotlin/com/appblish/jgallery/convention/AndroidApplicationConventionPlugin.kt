package com.appblish.jgallery.convention

import com.android.build.api.dsl.ApplicationExtension
import com.android.build.api.dsl.ApkSigningConfig
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure
import java.io.File
import java.util.Properties

/**
 * The `:app` module. Applies the storage-boundary lint check too — the app wires DI bindings for
 * the storage abstraction but must not perform raw file ops itself.
 */
class AndroidApplicationConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        pluginManager.apply("com.android.application")
        pluginManager.apply("org.jetbrains.kotlin.android")

        extensions.configure<ApplicationExtension> {
            configureAndroidCommon(this)
            defaultConfig {
                targetSdk = BuildConfig.TARGET_SDK
                versionCode = 1
                versionName = "0.1.0"
                testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
            }
            // Release signing (APP-455). Perf verification (JD device test, APP-400 finding 1)
            // requires an R8-optimized, non-debuggable, *installable* (= signed) APK. The signing
            // key itself does not affect R8/perf — it only has to make the APK installable — so
            // by default we sign with a committed debug-safe upload keystore (`debug.keystore`,
            // well-known password "android"; this is NOT a secret, it is the AOSP debug-key
            // convention and is the only *.keystore whitelisted by .gitignore). A real release/CI
            // keystore can be supplied WITHOUT committing secrets via `keystore.properties`
            // (gitignored) or the RELEASE_STORE_* environment variables; when present it wins.
            signingConfigs {
                create("release") {
                    configureReleaseSigning(target, this)
                }
            }
            buildTypes {
                getByName("release") {
                    isMinifyEnabled = true
                    isShrinkResources = true
                    signingConfig = signingConfigs.getByName("release")
                    proguardFiles(
                        getDefaultProguardFile("proguard-android-optimize.txt"),
                        "proguard-rules.pro",
                    )
                }
                // The macrobenchmark target (spec §11 10k-scroll frame-time gate, APP-342).
                // A release-like, NON-debuggable, profileable variant — macrobenchmark refuses
                // debuggable builds because JIT/debug hooks distort frame timing. Kept minify-off
                // so the measured code path matches the tiles/list users actually scroll (not a
                // shrunk graph) and so the benchmark-only PhotosBenchmarkActivity survives without
                // keep rules. Debug-signed so it installs on CI/dev without a release keystore.
                // Profileable is declared in app/src/benchmark/AndroidManifest.xml.
                create("benchmark") {
                    initWith(getByName("release"))
                    isMinifyEnabled = false
                    isShrinkResources = false
                    isDebuggable = false
                    signingConfig = signingConfigs.getByName("debug")
                    // Feature/core modules only publish debug+release; consume their release
                    // variant from this benchmark variant.
                    matchingFallbacks += "release"
                }
            }
        }

        applyStorageBoundaryLint()
    }
}

/**
 * Resolves the release [signingConfig] from, in order of precedence:
 *  1. `RELEASE_STORE_FILE` / `RELEASE_STORE_PASSWORD` / `RELEASE_KEY_ALIAS` / `RELEASE_KEY_PASSWORD`
 *     environment variables (CI-friendly; no secrets in the repo).
 *  2. A `keystore.properties` file at the repo root (gitignored) with the same keys.
 *  3. The committed debug-safe `debug.keystore` fallback (well-known creds), so a plain
 *     `./gradlew :app:assembleRelease` on any checkout yields an installable APK for perf testing.
 *
 * Any secret path (1 or 2) still produces an unsigned-in-repo build: nothing sensitive is
 * committed. The fallback is signed with the AOSP debug convention key, which is public.
 */
private fun configureReleaseSigning(project: Project, signing: ApkSigningConfig) {
    val rootDir = project.rootProject.projectDir

    fun envOrProp(env: String, prop: String, props: Properties): String? =
        System.getenv(env) ?: props.getProperty(prop)

    val props = Properties().apply {
        val f = File(rootDir, "keystore.properties")
        if (f.exists()) f.inputStream().use { load(it) }
    }

    val storePath = envOrProp("RELEASE_STORE_FILE", "storeFile", props)
    if (storePath != null && File(rootDir, storePath).exists()) {
        signing.storeFile = File(rootDir, storePath)
        signing.storePassword = envOrProp("RELEASE_STORE_PASSWORD", "storePassword", props)
        signing.keyAlias = envOrProp("RELEASE_KEY_ALIAS", "keyAlias", props)
        signing.keyPassword = envOrProp("RELEASE_KEY_PASSWORD", "keyPassword", props)
    } else {
        // Debug-safe fallback — committed keystore, public AOSP debug creds. Installable, not for
        // Play upload. Documented in app/README-release.md.
        signing.storeFile = File(rootDir, "debug.keystore")
        signing.storePassword = "android"
        signing.keyAlias = "androiddebugkey"
        signing.keyPassword = "android"
    }
}
