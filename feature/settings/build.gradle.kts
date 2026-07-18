plugins {
    alias(libs.plugins.jgallery.android.feature)
}

android {
    namespace = "com.appblish.jgallery.feature.settings"

    // The About screen surfaces the app version (design SET-04). Feature modules are libraries with no
    // versionName/versionCode of their own, so we thread the 1.0-launch values (mirrors the app
    // convention plugin, APP-513) in as BuildConfig fields. Keep in sync with
    // AndroidApplicationConventionPlugin if the launch version changes.
    buildFeatures {
        buildConfig = true
    }
    defaultConfig {
        buildConfigField("String", "VERSION_NAME", "\"1.0.0\"")
        buildConfigField("int", "VERSION_CODE", "1")
    }
}

dependencies {
    // Settings reuses the shared design system (SortBySheet, ColumnCountSheet, JGallerySheet, theme).
    // It owns no media state — its DataStore holds only theme + slideshow prefs — so it needs no :core:index.
    implementation(project(":core:viewdefaults")) // WRITES the app-wide default sort + grid density (APP-569).
    implementation(libs.androidx.activity.compose) // BackHandler + Custom Tab / ACTION_VIEW launch.
    implementation(libs.androidx.datastore.preferences)
}
