plugins {
    alias(libs.plugins.jgallery.android.feature)
}

android {
    namespace = "com.appblish.jgallery.feature.search"
}

dependencies {
    // Live search observes the cached index (spec §1 rule 4 / §4.1) and loads result tiles ONLY as
    // ThumbnailRequest models (spec §1 rule 2) — no :core:storage dependency, so the §1.6 boundary holds.
    implementation(project(":core:index"))
    implementation(project(":core:thumbs"))

    // AsyncImage for the shared result-grid tiles; the app-wide loader resolves ThumbnailRequest models.
    implementation(libs.coil.compose)

    // Recent-search history persistence (spec S4.4/S6) — most-recent-first, capped, process-durable.
    implementation(libs.androidx.datastore.preferences)
}
