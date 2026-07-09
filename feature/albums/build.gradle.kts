plugins {
    alias(libs.plugins.jgallery.android.feature)
}

android {
    namespace = "com.appblish.jgallery.feature.albums"
}

dependencies {
    // The tab observes the cached index (spec §1 rule 4); covers load ONLY as ThumbnailRequest
    // models (spec §1 rule 2) — no :core:storage dependency, so the §1.6 boundary holds structurally.
    implementation(project(":core:index"))
    implementation(project(":core:thumbs"))

    // AsyncImage; the app-wide loader (from :core:thumbs) resolves ThumbnailRequest models.
    implementation(libs.coil.compose)

    // Per-tab grid density persistence (design §3).
    implementation(libs.androidx.datastore.preferences)
}
