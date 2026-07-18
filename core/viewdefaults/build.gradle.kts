plugins {
    alias(libs.plugins.jgallery.android.library)
    alias(libs.plugins.jgallery.android.hilt)
}

android {
    namespace = "com.appblish.jgallery.core.viewdefaults"
}

// The app-wide view-defaults seam (APP-569): the single source of truth for the "Default sort" and
// "Grid density" prefs. Settings WRITES here; Photos / Albums READ it to seed a tab that has no
// per-tab override yet. Both sides depend on this core module and never on each other — the seam that
// keeps the module graph a DAG (Architect ruling, APP-567). Holds no media state, so it needs no
// :core:index and never touches the §1.6 storage boundary.
dependencies {
    api(project(":core:model")) // SortSpec / ColumnCount are the currency of the seam.
    implementation(libs.androidx.datastore.preferences)
}
