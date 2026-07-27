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

    // Adaptive foundation (APP-651): LocalWindowSizeClass exposes the material3 WindowSizeClass type
    // as part of this module's public API, so the artifact must be `api`. The compose BOM is declared
    // `implementation` by the compose convention plugin and does NOT constrain the `api` configuration,
    // so pull the BOM platform onto `api` here to keep the version BOM-managed (composeBom 2024.09.03
    // → material3-window-size-class 1.3.0) for consumers without their own BOM. No version pin.
    api(platform(libs.androidx.compose.bom))
    api(libs.androidx.compose.material3.window.sizeclass)

    // The shared decode/degrade hook (MediaDecodeBox, spec §8) wraps Coil's AsyncImage so image and
    // video tiles fall back to the D3 placeholder uniformly. The model type stays opaque here — this
    // module never learns about :core:thumbs request types, so the §1.6 boundary is unaffected.
    api(libs.coil.compose)
}
