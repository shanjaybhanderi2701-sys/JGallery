plugins {
    alias(libs.plugins.jgallery.android.application)
    alias(libs.plugins.jgallery.android.compose)
    alias(libs.plugins.jgallery.android.hilt)
    // CI egress guard (APP-289): keeps the §9.3 "never uploaded" claim true by construction —
    // fails the build on network permissions in the merged manifest, denylisted deps in the
    // resolved runtime classpath, or trust-claim copy outside the registered files.
    alias(libs.plugins.jgallery.egress.guard)
}

android {
    namespace = "com.appblish.jgallery"
}

dependencies {
    // Core — depended on directly so all Hilt DI modules are aggregated by the app.
    implementation(project(":core:model"))
    implementation(project(":core:storage"))
    implementation(project(":core:index"))
    implementation(project(":core:thumbs"))
    implementation(project(":core:playback"))
    implementation(project(":core:ui"))

    // Features.
    implementation(project(":feature:onboarding"))
    implementation(project(":feature:albums"))
    implementation(project(":feature:photos"))
    implementation(project(":feature:collections"))
    implementation(project(":feature:search"))
    implementation(project(":feature:viewer"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.hilt.navigation.compose)

    // Instrumented-test lane (Compose UI). BOM + ui-test-junit4 come from the compose convention.
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.rules)
}
