// JGallery — standalone Android smart gallery (Phase G1).
// Fully separate from CalcVault. Module graph below encodes the §1.6 storage boundary:
// ONLY :core:storage may touch platform file/media APIs; everything else consumes its
// abstraction. That boundary is enforced structurally by the storage-boundary lint check,
// wired through the feature/library convention plugins (see build-logic).

pluginManagement {
    includeBuild("build-logic")
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "JGallery"

enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")

// --- App ---
include(":app")

// --- Core layers ---
include(":core:model")   // pure-Kotlin domain types (no Android)
include(":core:storage") // §1.6 storage-access abstraction — the ONLY file/media boundary
include(":core:index")   // MediaStore-backed cached, incremental index
include(":core:thumbs")  // cached thumbnail pipeline (in-memory LRU + on-disk)
include(":core:ui")      // theme scaffolding + shared Compose components

// --- Features ---
include(":feature:onboarding") // language → primer → All Files Access flow (drives the §1.6 permission boundary)
include(":feature:albums")
include(":feature:photos")
include(":feature:collections") // placeholder this phase (content = G4)
include(":feature:search")      // placeholder this phase (content = G3)
include(":feature:viewer")

// --- Static-analysis / enforcement ---
include(":lint:storage-boundary") // custom lint rule that enforces the §1.6 boundary
