plugins {
    alias(libs.plugins.jgallery.android.library)
    alias(libs.plugins.jgallery.android.compose)
}

android {
    namespace = "com.appblish.jgallery.core.ui"
}

dependencies {
    api(project(":core:model"))
    implementation(libs.androidx.core.ktx)
}
