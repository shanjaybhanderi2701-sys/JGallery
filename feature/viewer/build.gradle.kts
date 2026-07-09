plugins {
    alias(libs.plugins.jgallery.android.feature)
}

android {
    namespace = "com.appblish.jgallery.feature.viewer"
}

dependencies {
    // The viewer consumes the cached index (pager order), the E4 image pipeline (thumbnail
    // placeholder + boundary-routed full decode) and boundary-routed Media3 sources — never
    // :core:storage directly (§1.6).
    implementation(project(":core:index"))
    implementation(project(":core:thumbs"))
    implementation(project(":core:playback"))
}
