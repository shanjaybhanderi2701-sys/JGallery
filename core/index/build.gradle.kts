plugins {
    alias(libs.plugins.jgallery.android.library) // boundary lint APPLIES — index goes through :core:storage
    alias(libs.plugins.jgallery.android.hilt)
}

android {
    namespace = "com.appblish.jgallery.core.index"
}

// Room schema export location (top-level KSP DSL) — keeps migrations reviewable in code review.
ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

dependencies {
    api(project(":core:model"))
    // implementation (not api): features depend on :core:index but cannot see :core:storage through it.
    implementation(project(":core:storage"))
    implementation(libs.androidx.collection)

    // Persisted favorite-media flag (G2 · APP-543) — a flat Set<MediaId>, same DataStore idiom as the
    // Albums pin flag. Lives here so Photos / Viewer / Albums share one source of truth.
    implementation(libs.androidx.datastore.preferences)

    // Persistent cached index (spec §1 rule 4). Room = queryable, Flow-native, survives process death
    // so opening the app reads the cache instead of re-scanning MediaStore.
    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)

    // DAO round-trip is covered by the instrumented lane (in-memory Room needs an Android runtime);
    // the delta + sync logic is pure-JVM unit-tested.
    androidTestImplementation(libs.room.ktx)
}
