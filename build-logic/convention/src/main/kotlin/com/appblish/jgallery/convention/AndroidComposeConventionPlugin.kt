package com.appblish.jgallery.convention

import com.android.build.api.dsl.CommonExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.dependencies

/**
 * Adds Jetpack Compose to an already-configured Android (library or application) module and
 * wires the Compose UI-test dependencies used by the instrumented-test lane.
 */
class AndroidComposeConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        pluginManager.apply("org.jetbrains.kotlin.plugin.compose")

        val commonExtension = extensions.getByName("android") as CommonExtension<*, *, *, *, *, *>
        commonExtension.buildFeatures.compose = true

        dependencies {
            // `platform(...)` is a member of DependencyHandler, resolved via the dependencies-block receiver.
            add("implementation", platform(libs.library("androidx-compose-bom")))
            add("androidTestImplementation", platform(libs.library("androidx-compose-bom")))

            add("implementation", libs.library("androidx-compose-ui"))
            add("implementation", libs.library("androidx-compose-ui-graphics"))
            add("implementation", libs.library("androidx-compose-ui-tooling-preview"))
            add("implementation", libs.library("androidx-compose-foundation"))
            add("implementation", libs.library("androidx-compose-material3"))
            add("implementation", libs.library("androidx-compose-material-icons-extended"))

            add("debugImplementation", libs.library("androidx-compose-ui-tooling"))
            add("debugImplementation", libs.library("androidx-compose-ui-test-manifest"))
            add("androidTestImplementation", libs.library("androidx-compose-ui-test-junit4"))
        }
    }
}
