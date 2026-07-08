package com.appblish.jgallery.convention

import com.android.build.api.dsl.CommonExtension
import org.gradle.api.JavaVersion
import org.gradle.api.Project
import org.gradle.api.artifacts.VersionCatalog
import org.gradle.api.artifacts.VersionCatalogsExtension
import org.gradle.api.provider.Provider
import org.gradle.api.artifacts.MinimalExternalModuleDependency
import org.gradle.kotlin.dsl.dependencies
import org.gradle.kotlin.dsl.getByType
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.dsl.KotlinAndroidProjectExtension
import org.jetbrains.kotlin.gradle.dsl.KotlinJvmProjectExtension

/** JGallery-wide compile targets — keep every module identical. */
object BuildConfig {
    const val COMPILE_SDK = 35 // Compose 1.8 (via Coil3 3.2) + AGP 8.7 AAR metadata require API 35 to compile against
    const val TARGET_SDK = 34
    const val MIN_SDK = 29 // modern MediaStore columns (BUCKET_*, DURATION); All-Files R-era model, guarded at runtime
    val JAVA_VERSION = JavaVersion.VERSION_17
    val JVM_TARGET = JvmTarget.JVM_17
}

internal val Project.libs: VersionCatalog
    get() = extensions.getByType<VersionCatalogsExtension>().named("libs")

internal fun VersionCatalog.library(alias: String): Provider<MinimalExternalModuleDependency> =
    findLibrary(alias).orElseThrow { IllegalStateException("Missing library alias '$alias' in version catalog") }

/** Shared Android options for both application and library modules. */
internal fun Project.configureAndroidCommon(extension: CommonExtension<*, *, *, *, *, *>) {
    extension.apply {
        compileSdk = BuildConfig.COMPILE_SDK

        defaultConfig {
            minSdk = BuildConfig.MIN_SDK
        }

        compileOptions {
            sourceCompatibility = BuildConfig.JAVA_VERSION
            targetCompatibility = BuildConfig.JAVA_VERSION
        }

        lint {
            warningsAsErrors = true
            abortOnError = true
            checkDependencies = true
            sarifReport = true
            // Navigation 2.8.x lint jars are compiled against an older lint API — suppress the
            // ObsoleteLintCustomCheck warning they emit so CI stays clean without a baseline file.
            disable += "ObsoleteLintCustomCheck"
            // This gallery app legitimately uses MANAGE_EXTERNAL_STORAGE for All Files Access
            // (spec §1.6, §9.3). The ScopedStorage lint check warns about Play policy; the policy
            // allows gallery/file-manager apps with a justified use case — suppress the check here
            // and note it in the release checklist instead.
            disable += "ScopedStorage"
            // "Newer version available" nags fail the build whenever an upstream release is
            // published — a time-dependent, non-deterministic signal that has no place gating CI.
            // Dependency bumps are a deliberate, reviewed action, not a lint error.
            disable += "GradleDependency"
            disable += "AndroidGradlePluginVersion"
            // The launcher adaptive-icon must live in mipmap-anydpi-v26 (aapt2 rejects <adaptive-icon>
            // in an unqualified folder), which lint flags as an "obsolete" v26 qualifier at minSdk 29.
            // That advice is inapplicable to adaptive icons; no real SDK_INT guard trips this check.
            disable += "ObsoleteSdkInt"
            // No baseline: with the above nags disabled the scaffold ships with zero violations, and
            // the RawStorageAccess boundary check must fail the build the moment one is introduced.
        }
    }

    // Kotlin jvmTarget for android modules.
    extensions.findByType(KotlinAndroidProjectExtension::class.java)?.apply {
        compilerOptions {
            jvmTarget.set(BuildConfig.JVM_TARGET)
            freeCompilerArgs.add("-Xjvm-default=all")
        }
    }
}

/** Applies the storage-boundary lint check so raw file/media access fails the build. */
internal fun Project.applyStorageBoundaryLint() {
    dependencies {
        add("lintChecks", project(":lint:storage-boundary"))
    }
}

internal fun Project.configureKotlinJvm() {
    extensions.getByType<KotlinJvmProjectExtension>().apply {
        compilerOptions {
            jvmTarget.set(BuildConfig.JVM_TARGET)
        }
    }
}
