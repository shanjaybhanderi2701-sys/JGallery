plugins {
    alias(libs.plugins.jgallery.android.feature)
}

android {
    namespace = "com.appblish.jgallery.feature.onboarding"
}

dependencies {
    // Onboarding is the ONE feature that drives the permission half of the §1.6 boundary. It depends
    // on the :core:storage INTERFACES only (StoragePermissionController / AccessRequest) — it never
    // names MediaStore / File / Environment / MANAGE_EXTERNAL_STORAGE / a Settings action, so the
    // RawStorageAccess lint (applied by the feature convention) still guards this module.
    implementation(project(":core:storage"))

    // Activity Result launcher (RuntimePermissions arm) + startActivity host for the SystemSettings arm.
    implementation(libs.androidx.activity.compose)

    // Persist selected language + "past language" flag (DataStore is already in the catalog).
    implementation(libs.androidx.datastore.preferences)
}
