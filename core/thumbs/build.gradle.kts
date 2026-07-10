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
    // Format breadth (W3-E13 §8): animated GIF/WEBP/HEIF, best-effort SVG, RAW embedded-JPEG.
    // NB: none of these add a network engine — the §9.3 egress guard stays intact.
    implementation(libs.coil.gif)
    implementation(libs.coil.svg)
    implementation(libs.androidx.exifinterface)

    // Format-matrix instrumented coverage (W3-E13 §8): decode succeeds or falls through gracefully.
    androidTestImplementation(libs.truth)
    androidTestImplementation(libs.kotlinx.coroutines.test)
}
