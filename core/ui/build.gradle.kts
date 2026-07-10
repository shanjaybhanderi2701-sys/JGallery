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

    // The shared decode/degrade hook (MediaDecodeBox, spec §8) wraps Coil's AsyncImage so image and
    // video tiles fall back to the D3 placeholder uniformly. The model type stays opaque here — this
    // module never learns about :core:thumbs request types, so the §1.6 boundary is unaffected.
    api(libs.coil.compose)
}
