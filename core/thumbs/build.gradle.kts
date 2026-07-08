plugins {
    alias(libs.plugins.jgallery.android.library) // boundary lint APPLIES — decode sources come from :core:storage
    alias(libs.plugins.jgallery.android.hilt)
}

android {
    namespace = "com.appblish.jgallery.core.thumbs"
}

dependencies {
    api(project(":core:model"))
    implementation(project(":core:storage"))
    api(libs.coil.compose)
    implementation(libs.coil.video)
}
