plugins {
    alias(libs.plugins.jgallery.android.library) // boundary lint APPLIES — bytes come from :core:storage
    alias(libs.plugins.jgallery.android.hilt)
}

android {
    namespace = "com.appblish.jgallery.core.playback"
}

dependencies {
    api(project(":core:model"))
    // implementation (not api): features depend on :core:playback but cannot see :core:storage through it.
    implementation(project(":core:storage"))

    // Media3 types appear in the public API (MediaSource) the same way Coil does in :core:thumbs —
    // the player engine is the sanctioned consumer-facing surface; the byte source stays boundary-routed.
    api(libs.media3.exoplayer)
}
