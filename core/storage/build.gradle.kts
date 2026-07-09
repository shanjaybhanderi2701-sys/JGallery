plugins {
    alias(libs.plugins.jgallery.android.storage) // PRIVILEGED: the one convention without the boundary lint
    alias(libs.plugins.jgallery.android.hilt)
}

android {
    namespace = "com.appblish.jgallery.core.storage"
}

dependencies {
    api(project(":core:model"))

    // Persistent Recycle-Bin retention metadata (spec §7.5) — the app-managed bin manifest.
    implementation(libs.androidx.datastore.preferences)
}
