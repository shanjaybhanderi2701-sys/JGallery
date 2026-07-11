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
include(":core:playback") // Media3 playback sources routed through the §1.6 boundary (viewer video)
include(":core:playerkit") // APP-402: app-agnostic video-player kit (pluggable DataSource seam + gesture/scale/zoom core) shared with CalcVault
include(":core:ui")      // theme scaffolding + shared Compose components

// --- Features ---
include(":feature:onboarding") // language → primer → All Files Access flow (drives the §1.6 permission boundary)
include(":feature:albums")
include(":feature:photos")
include(":feature:collections") // placeholder this phase (content = G4)
include(":feature:search")      // placeholder this phase (content = G3)
include(":feature:viewer")
include(":feature:trash")       // Recycle Bin / Trash (W2-E9, spec §7.5)

// --- Static-analysis / enforcement ---
include(":lint:storage-boundary") // custom lint rule that enforces the §1.6 boundary

// --- Performance gate ---
include(":macrobenchmark") // 10k+ item scroll frame-time harness (spec §11 DoD, APP-342)
