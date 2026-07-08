package com.appblish.jgallery.convention

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.dependencies

/**
 * Convention for every `:feature:*` module. Composes library + Compose + Hilt and wires the
 * common UI/nav dependencies plus the shared design system. The storage-boundary lint check is
 * inherited from [AndroidLibraryConventionPlugin]; feature modules do NOT depend on `:core:storage`
 * — they consume repositories/models from `:core:index` / `:core:thumbs` and never see platform
 * file APIs, structurally.
 */
class AndroidFeatureConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        pluginManager.apply("jgallery.android.library")
        pluginManager.apply("jgallery.android.compose")
        pluginManager.apply("jgallery.android.hilt")

        dependencies {
            add("implementation", project(":core:ui"))
            add("implementation", project(":core:model"))

            add("implementation", libs.library("androidx-core-ktx"))
            add("implementation", libs.library("androidx-lifecycle-runtime-ktx"))
            add("implementation", libs.library("androidx-lifecycle-viewmodel-compose"))
            add("implementation", libs.library("androidx-navigation-compose"))
            add("implementation", libs.library("hilt-navigation-compose"))
            add("implementation", libs.library("kotlinx-coroutines-android"))
        }
    }
}
