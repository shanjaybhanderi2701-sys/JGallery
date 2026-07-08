// Root build script. Plugins are declared `apply false` so module scripts / convention plugins
// opt in. The convention plugins in build-logic are the primary configuration surface — module
// build files stay thin and consistent.
//
// Static analysis is Android Lint, wired into every module by the convention plugins with
// warningsAsErrors + the custom `RawStorageAccess` boundary check (spec §1.6). Run tree-wide with
// `./gradlew lint`.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.android.lint) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.hilt) apply false
}
