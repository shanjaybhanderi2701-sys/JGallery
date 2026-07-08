plugins {
    alias(libs.plugins.android.lint)
    alias(libs.plugins.kotlin.jvm)
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

// Pin the Kotlin jvmTarget to match compileJava (17) so the module builds identically regardless of
// the JDK running Gradle (JDK 17 on CI, but a newer JBR locally would otherwise default to its own
// version and trip Gradle's JVM-target consistency check).
kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    compileOnly(libs.lint.api)
    compileOnly(libs.lint.checks)

    testImplementation(libs.lint.api)
    testImplementation(libs.lint.tests)
    testImplementation(libs.junit)
}

// Register the IssueRegistry so `lintChecks(project(":lint:storage-boundary"))` picks it up.
tasks.jar {
    manifest {
        attributes(
            mapOf("Lint-Registry-v2" to "com.appblish.jgallery.lint.StorageBoundaryIssueRegistry"),
        )
    }
}
