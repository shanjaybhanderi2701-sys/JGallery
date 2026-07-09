plugins {
    alias(libs.plugins.jgallery.android.feature)
}

android {
    namespace = "com.appblish.jgallery.feature.trash"
}

dependencies {
    // The Recycle Bin reads + mutates trashed media through :core:index's TrashRepository — the same
    // feature-facing seam Photos/Albums use for reads and file-ops. `:core:storage` stays off this
    // module's classpath (it is `implementation` inside :core:index), so the §1.6 boundary holds
    // structurally and the RawStorageAccess lint has nothing to catch.
    implementation(project(":core:index"))

    // Trashed-item tiles load as ThumbnailRequest models through the E4 cached pipeline (spec §1 rule 2).
    implementation(project(":core:thumbs"))

    // AsyncImage; the app-wide loader (from :core:thumbs) resolves ThumbnailRequest models.
    implementation(libs.coil.compose)

    // BackHandler (collapse selection on system back).
    implementation(libs.androidx.activity.compose)
}
