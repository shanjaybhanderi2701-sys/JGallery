package com.appblish.jgallery.convention

import com.android.build.api.dsl.LibraryExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.dependencies

/**
 * PRIVILEGED module convention — used ONLY by `:core:storage`, the single §1.6 boundary.
 *
 * This is the one convention that deliberately does NOT apply the storage-boundary lint check,
 * so `:core:storage` is the only place `java.io.File`, `MediaStore`, `ContentResolver` file IO,
 * `Environment`, and `MANAGE_EXTERNAL_STORAGE` may be referenced. Which convention a module
 * applies is the structural switch that grants/denies platform file access — a feature can only
 * gain raw access by editing build-logic, which is reviewable and obvious.
 */
class AndroidStorageConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        pluginManager.apply("com.android.library")
        pluginManager.apply("org.jetbrains.kotlin.android")

        extensions.configure<LibraryExtension> {
            configureAndroidCommon(this)
            defaultConfig {
                testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
            }
            testOptions.targetSdk = BuildConfig.TARGET_SDK
        }
        // NOTE: applyStorageBoundaryLint() intentionally omitted — this is the boundary owner.

        dependencies {
            add("implementation", libs.library("kotlinx-coroutines-core"))
            add("implementation", libs.library("kotlinx-coroutines-android"))
            add("testImplementation", libs.library("junit"))
            add("testImplementation", libs.library("truth"))
            add("testImplementation", libs.library("kotlinx-coroutines-test"))
            add("androidTestImplementation", libs.library("androidx-test-ext-junit"))
            add("androidTestImplementation", libs.library("androidx-test-runner"))
        }
    }
}
