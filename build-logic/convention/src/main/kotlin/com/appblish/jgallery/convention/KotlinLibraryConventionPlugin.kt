package com.appblish.jgallery.convention

import org.gradle.api.JavaVersion
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.dependencies

/**
 * Pure-Kotlin/JVM module (no Android). Used by `:core:model` — domain types with zero platform
 * dependencies, so they cannot reach file APIs by construction.
 */
class KotlinLibraryConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        pluginManager.apply("org.jetbrains.kotlin.jvm")

        extensions.configure<JavaPluginExtension> {
            sourceCompatibility = BuildConfig.JAVA_VERSION
            targetCompatibility = BuildConfig.JAVA_VERSION
        }
        configureKotlinJvm()

        dependencies {
            add("testImplementation", libs.library("junit"))
            add("testImplementation", libs.library("truth"))
        }
    }
}
