# App R8/ProGuard keep rules (APP-455).
#
# Philosophy: rely on library-supplied *consumer* rules first. Hilt/Dagger, Jetpack Compose,
# AndroidX Media3, Coil 3, kotlinx-coroutines and DataStore each ship their own keep rules inside
# their AARs, which R8 applies automatically — DO NOT re-declare them here (over-keeping defeats the
# shrink that makes this a valid perf build). Only rules for JGallery's OWN reflection surfaces, plus
# narrowly-scoped safety nets for known R8-full-mode gotchas, belong below.

# --- JGallery reflection surface: persisted enum names -------------------------------------------
# TrashMetadataStore round-trips MediaType via Enum.valueOf() on strings read back from the trash
# metadata file (core/storage TrashMetadataStore#deserialize). proguard-android-optimize.txt already
# keeps values()/valueOf() for all enums, but we pin it explicitly because the input is *persisted*
# data: an over-aggressive future rule that renamed these constants would silently corrupt the bin.
-keepclassmembers enum com.appblish.jgallery.** {
    public static **[] values();
    public static ** valueOf(java.lang.String);
    <fields>;
}

# --- Kotlin metadata safety net ------------------------------------------------------------------
# Silence intrinsics warnings if a transitive strips them; does not weaken any keep.
-dontwarn kotlin.**
-dontwarn org.jetbrains.annotations.**

# --- AndroidX Media3 (ExoPlayer) -----------------------------------------------------------------
# media3-exoplayer ships consumer rules covering its reflective extractor/renderer loading. We only
# add -dontwarn for its optional-codec classes so a stripped optional dependency can't fail the build
# (JGallery bundles exoplayer + ui only; extension modules are absent by design).
-dontwarn androidx.media3.**

# --- Coil 3 --------------------------------------------------------------------------------------
# coil-svg/coil-gif pull optional decoders; Coil ships its own keeps. Silence optionals only.
-dontwarn io.coil.kt.coil3.**
-dontwarn okio.**
