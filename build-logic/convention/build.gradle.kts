plugins {
    `kotlin-dsl`
}

group = "com.appblish.jgallery.buildlogic"

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    compileOnly(libs.android.gradlePlugin)
    compileOnly(libs.kotlin.gradlePlugin)
    compileOnly(libs.ksp.gradlePlugin)
}

// Register each convention plugin by id so modules can apply them via the version catalog.
gradlePlugin {
    plugins {
        register("androidApplication") {
            id = "jgallery.android.application"
            implementationClass = "com.appblish.jgallery.convention.AndroidApplicationConventionPlugin"
        }
        register("androidLibrary") {
            id = "jgallery.android.library"
            implementationClass = "com.appblish.jgallery.convention.AndroidLibraryConventionPlugin"
        }
        register("androidStorage") {
            id = "jgallery.android.storage"
            implementationClass = "com.appblish.jgallery.convention.AndroidStorageConventionPlugin"
        }
        register("androidCompose") {
            id = "jgallery.android.compose"
            implementationClass = "com.appblish.jgallery.convention.AndroidComposeConventionPlugin"
        }
        register("androidFeature") {
            id = "jgallery.android.feature"
            implementationClass = "com.appblish.jgallery.convention.AndroidFeatureConventionPlugin"
        }
        register("androidHilt") {
            id = "jgallery.android.hilt"
            implementationClass = "com.appblish.jgallery.convention.AndroidHiltConventionPlugin"
        }
        register("egressGuard") {
            id = "jgallery.egress.guard"
            implementationClass = "com.appblish.jgallery.convention.EgressGuardConventionPlugin"
        }
        register("kotlinLibrary") {
            id = "jgallery.kotlin.library"
            implementationClass = "com.appblish.jgallery.convention.KotlinLibraryConventionPlugin"
        }
    }
}
