plugins {
    alias(libs.plugins.jgallery.android.feature)
}

android {
    namespace = "com.appblish.jgallery.feature.search"
}

dependencies {
    // Recent-search history persistence (spec S4.4/S6) — most-recent-first, capped, process-durable.
    implementation(libs.androidx.datastore.preferences)
}
