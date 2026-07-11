plugins {
    // Android library so the pluggable seam can name Media3 types. The storage-boundary lint APPLIES
    // (this module holds NO file/media APIs — bytes arrive via a caller-supplied DataSource.Factory).
    alias(libs.plugins.jgallery.android.library)
}

android {
    // Neutral namespace — this module is the shared player kit consumed by BOTH JGallery
    // (com.appblish.jgallery) and CalcVault (com.appblish.calculatorvault), so it carries neither
    // app's package. See core/playerkit/README (APP-402) for the two-repo home decision.
    namespace = "com.appblish.playerkit"
}

dependencies {
    // The pluggable playback seam is Media3's own DataSource.Factory; ProgressiveMediaSource is the
    // stable engine the surface hands to ExoPlayer. This is the ONLY dependency — no app model, no
    // crypto, no storage boundary — so an encrypted (CalcVault) or plain-file (JGallery) source
    // plugs in identically.
    api(libs.media3.exoplayer)
}
