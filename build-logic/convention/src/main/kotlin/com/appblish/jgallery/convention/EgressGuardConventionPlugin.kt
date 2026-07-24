package com.appblish.jgallery.convention

import com.android.build.api.artifact.SingleArtifact
import com.android.build.api.variant.ApplicationAndroidComponentsExtension
import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.result.ResolvedComponentResult
import org.gradle.api.artifacts.result.ResolvedDependencyResult
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.gradle.kotlin.dsl.register

/**
 * CI egress guard (`jgallery.egress.guard`, APP-289) — originally made the §9.3 trust claim
 * ("works fully on your device … never uploaded or shared") durable BY CONSTRUCTION.
 *
 * **APP-614 RELAXATION (board Option A, APP-613).** The board chose to ship Firebase Crashlytics
 * for the 1.0 launch, deliberately retiring the §9.3 *zero-network* guarantee. This guard is now a
 * *bounded* egress guard, not a zero-egress guard: it allows EXACTLY the Firebase Crashlytics
 * surface (INTERNET + ACCESS_NETWORK_STATE; `com.google.firebase` / `com.google.android.gms` /
 * `com.google.android.datatransport` deps) and still fails the build on ANY other network
 * permission or network/analytics dependency. The §9.3 "never uploaded" claim copy is being pulled
 * separately under APP-616, which gates the Play upload (APP-517); Check 3 below keeps that copy
 * single-sourced so it can be removed as one mechanical set. The prior APP-285 Security sign-off is
 * superseded by the APP-613 decision and requires re-issue against the new (bounded) egress surface.
 *
 * Three checks, aggregated under `:app:verifyNoEgress` (also wired into `check`):
 *
 *  1. [VerifyNoEgressManifestTask] — per variant, fails if any network-capable permission
 *     (INTERNET, network/wifi state) appears in the MERGED manifest, so a permission smuggled in
 *     by a library manifest is caught, not just ones declared in our sources.
 *  2. [VerifyNoEgressDependenciesTask] — per variant, fails if any coordinate in the RESOLVED
 *     runtime classpath (transitives included) matches the network/analytics denylist, OR (APP-619
 *     Finding 1) is an artifact under an approved Google egress group that is NOT in the pinned,
 *     closed-world Crashlytics/Analytics artifact set — so a new Firebase/GMS surface can't ride in
 *     under the group prefix.
 *  3. [VerifyTrustClaimSingleSourceTask] — fails if trust-claim wording appears in production
 *     source outside the registered claim files. Keeps the claim auditable in one place
 *     (TrustCopy) so it can be pulled in one edit if egress ever lands (B1 on APP-285).
 *
 * Complements — does not replace — the `RawStorageAccess` boundary lint, which covers
 * file/MediaStore/Environment but NOT network.
 */
class EgressGuardConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        // The guard is app-module-only: the merged manifest and full runtime graph exist there.
        pluginManager.withPlugin("com.android.application") { configureGuard(target) }
    }

    private fun configureGuard(target: Project): Unit = with(target) {
        val umbrella = tasks.register("verifyNoEgress") {
            group = VERIFICATION_GROUP
            description =
                "Fails the build if network egress capability (merged-manifest permission or " +
                    "resolved network/analytics dependency) or an unregistered trust claim is introduced."
        }

        val claimScan = tasks.register<VerifyTrustClaimSingleSourceTask>("verifyTrustClaimSingleSource") {
            group = VERIFICATION_GROUP
            description = "Fails if trust-claim copy exists in src/main outside the registered claim files."
            // Production sources only: user-facing claims live in src/main; tests assert against
            // TrustCopy constants and fail on their own if the copy is pulled.
            sources.from(
                rootProject.layout.projectDirectory.asFileTree.matching {
                    include("app/src/main/**/*.kt")
                    include("core/*/src/main/**/*.kt")
                    include("feature/*/src/main/**/*.kt")
                },
            )
            rootDir.set(rootProject.layout.projectDirectory.asFile.absolutePath)
            allowedFiles.set(REGISTERED_CLAIM_FILES)
        }

        val androidComponents = extensions.getByType(ApplicationAndroidComponentsExtension::class.java)
        androidComponents.onVariants { variant ->
            val variantName = variant.name.replaceFirstChar(Char::uppercase)

            val manifestCheck = tasks.register<VerifyNoEgressManifestTask>("verifyNoEgressManifest$variantName") {
                group = VERIFICATION_GROUP
                description = "Fails if a network-capable permission is in the $variantName merged manifest."
                mergedManifest.set(variant.artifacts.get(SingleArtifact.MERGED_MANIFEST))
            }

            val dependencyCheck =
                tasks.register<VerifyNoEgressDependenciesTask>("verifyNoEgressDependencies$variantName") {
                    group = VERIFICATION_GROUP
                    description = "Fails if a denylisted network/analytics dependency is in the $variantName runtime classpath."
                    rootComponent.set(
                        configurations
                            .named("${variant.name}RuntimeClasspath")
                            .flatMap { it.incoming.resolutionResult.rootComponent },
                    )
                }

            umbrella.configure { dependsOn(manifestCheck, dependencyCheck, claimScan) }
        }

        // A plain `./gradlew check` (and anything that lifecycles through it) runs the guard too.
        tasks.named("check") { dependsOn(umbrella) }
    }

    private companion object {
        const val VERIFICATION_GROUP = "verification"

        /**
         * The ONLY production files allowed to carry trust-claim wording (paths relative to the
         * repo root). SearchScreen/CollectionsScreen are the registered B1 residuals from the
         * APP-285 sign-off: true today on the same structural basis, pinned here so the standing
         * rule ("pull every claim if egress lands") has a complete, mechanical list.
         */
        val REGISTERED_CLAIM_FILES = listOf(
            "feature/onboarding/src/main/java/com/appblish/jgallery/feature/onboarding/TrustCopy.kt",
            "feature/search/src/main/java/com/appblish/jgallery/feature/search/SearchScreen.kt",
            "feature/collections/src/main/java/com/appblish/jgallery/feature/collections/CollectionsScreen.kt",
        )
    }
}

/** Check 1: no network-capable permission may survive manifest merging. */
abstract class VerifyNoEgressManifestTask : DefaultTask() {

    @get:InputFile
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val mergedManifest: RegularFileProperty

    @TaskAction
    fun verify() {
        val manifest = mergedManifest.get().asFile
        val text = manifest.readText()
        val hits = FORBIDDEN_PERMISSIONS.filter { text.contains(it) }
        if (hits.isNotEmpty()) {
            throw GradleException(
                """
                |EGRESS GUARD VIOLATION (§9.3 trust-claim integrity — see APP-285/APP-289):
                |Network-capable permission(s) found in the merged manifest:
                |${hits.joinToString("\n") { "  - $it" }}
                |Merged manifest: $manifest
                |
                |Only INTERNET + ACCESS_NETWORK_STATE are permitted (APP-614: Firebase Crashlytics).
                |Any OTHER network permission is still forbidden. Either remove it (check library
                |manifests for the merge source), or — if a new egress surface is genuinely required
                |— raise it as a product/privacy decision (like APP-613) before widening this guard.
                """.trimMargin(),
            )
        }
        logger.lifecycle(
            "Egress guard: merged manifest within the approved egress surface " +
                "(INTERNET + ACCESS_NETWORK_STATE only, APP-614).",
        )
    }

    private companion object {
        /**
         * Network permissions that STILL fail the build. INTERNET and ACCESS_NETWORK_STATE were
         * removed from this list under APP-614 (board Option A, APP-613) so Firebase Crashlytics
         * can upload crash reports; every other network-capable permission stays forbidden, so an
         * unrelated egress surface (wifi control, nearby devices, etc.) still turns the build red.
         */
        val FORBIDDEN_PERMISSIONS = listOf(
            "android.permission.CHANGE_NETWORK_STATE",
            "android.permission.ACCESS_WIFI_STATE",
            "android.permission.CHANGE_WIFI_STATE",
            "android.permission.NEARBY_WIFI_DEVICES",
        )
    }
}

/** Check 2: no network/analytics library may enter the resolved runtime graph (transitives included). */
abstract class VerifyNoEgressDependenciesTask : DefaultTask() {

    /** Root of the resolved runtime-classpath graph (configuration-cache-safe dependency input). */
    @get:Input
    abstract val rootComponent: Property<ResolvedComponentResult>

    @TaskAction
    fun verify() {
        val seen = mutableSetOf<String>()
        val queue = ArrayDeque(listOf(rootComponent.get()))
        val violations = sortedSetOf<String>()

        while (queue.isNotEmpty()) {
            val component = queue.removeFirst()
            if (!seen.add(component.id.displayName)) continue

            val group = component.moduleVersion?.group.orEmpty().lowercase()
            val coordinate = component.moduleVersion?.let { "${it.group}:${it.name}" }.orEmpty().lowercase()
            if (group in APPROVED_GOOGLE_GROUPS) {
                // APP-614: Google Firebase/GMS is the ONE approved egress dependency stack (board
                // Option A). APP-619 Finding 1: membership in an approved GROUP is not enough —
                // these groups are a CLOSED WORLD. Only the exact Crashlytics+Analytics artifact
                // set resolved at APP-614 sign-off is exempt; ANY other artifact under these
                // groups (firebase-messaging, firebase-storage user-file UPLOAD, firestore,
                // play-services-ads, …) is a NEW egress/data surface and fails the guard until it
                // is reviewed and added to APPROVED_GOOGLE_ARTIFACTS.
                if (coordinate !in APPROVED_GOOGLE_ARTIFACTS) {
                    violations.add(
                        "${component.id.displayName}  (unapproved artifact under approved Google " +
                            "egress group '$group' — a new Firebase/GMS surface needs Security " +
                            "review before it can egress; see APP-619)",
                    )
                }
            } else {
                // Everything else — including any non-Google analytics/network lib — is blocked
                // if it matches the denylist.
                DENYLIST.firstOrNull { coordinate.contains(it) }?.let {
                    violations.add("${component.id.displayName}  (matched denylist token: '$it')")
                }
            }

            component.dependencies
                .filterIsInstance<ResolvedDependencyResult>()
                .forEach { queue.add(it.selected) }
        }

        if (violations.isNotEmpty()) {
            throw GradleException(
                """
                |EGRESS GUARD VIOLATION (§9.3 trust-claim integrity — see APP-285/APP-289):
                |Denylisted network/analytics dependencies in the resolved runtime classpath:
                |${violations.joinToString("\n") { "  - $it" }}
                |
                |Transitives count: run `./gradlew :app:dependencies --configuration debugRuntimeClasspath`
                |to find what pulls them in. Either remove/exclude the dependency, or — per the
                |standing rule in the APP-285 security-signoff doc — pull the trust claim copy in the
                |SAME change and obtain a new Security sign-off.
                """.trimMargin(),
            )
        }
        logger.lifecycle("Egress guard: resolved runtime classpath clean (${seen.size} components checked).")
    }

    private companion object {
        /** APP-289 denylist. Matched as substrings of the lowercased `group:name` coordinate. */
        val DENYLIST = listOf(
            "retrofit",
            "okhttp",
            "ktor",
            "volley",
            "firebase",
            "analytics",
            "crashlytics",
            "sentry",
            "amplitude",
            "mixpanel",
            "segment",
            "apollo",
            "grpc",
        )

        /**
         * APP-614 approved egress GROUPS (board Option A, APP-613): the three Google namespaces
         * the Crashlytics/Analytics stack resolves into. Membership here does NOT by itself
         * approve a coordinate — it only selects the closed-world artifact check
         * ([APPROVED_GOOGLE_ARTIFACTS]) instead of the substring denylist. A group prefix alone
         * (the pre-APP-619 behaviour) would have green-lit the entire Firebase/GMS product
         * surface.
         */
        val APPROVED_GOOGLE_GROUPS = setOf(
            "com.google.firebase",
            "com.google.android.gms",
            "com.google.android.datatransport",
        )

        /**
         * APP-619 Finding 1 (Medium) — artifact-level allowlist / drift guard.
         *
         * The EXACT `group:name` set that `firebase-crashlytics` + `firebase-analytics` (Firebase
         * BoM 33.5.1) resolve to on BOTH the debug and release runtime classpaths, captured
         * 2026-07-24 (debug == release, 30 coordinates). This is a CLOSED WORLD: any coordinate
         * under an [APPROVED_GOOGLE_GROUPS] namespace that is NOT listed here fails the guard, so a
         * future `firebase-messaging` / `firebase-storage` (user-file UPLOAD) / `firestore` /
         * `play-services-ads*` addition can no longer pass silently under a group prefix — it
         * turns the build red and forces a Security review + a deliberate edit here (the same
         * product/privacy gate as APP-613).
         *
         * Version is intentionally excluded from the key, so a BoM version bump that keeps the same
         * artifact set stays green; only a NEW product surface trips the guard. Regenerate after an
         * approved BoM/dependency change with:
         *   ./gradlew :app:dependencies --configuration releaseRuntimeClasspath \
         *     | grep -oE 'com\.google\.(firebase|android\.gms|android\.datatransport):[a-z0-9-]+' \
         *     | sort -u
         * and diff against this set before updating (any additions are new egress surface).
         */
        val APPROVED_GOOGLE_ARTIFACTS = setOf(
            "com.google.android.datatransport:transport-api",
            "com.google.android.datatransport:transport-backend-cct",
            "com.google.android.datatransport:transport-runtime",
            "com.google.android.gms:play-services-ads-identifier",
            "com.google.android.gms:play-services-base",
            "com.google.android.gms:play-services-basement",
            "com.google.android.gms:play-services-measurement",
            "com.google.android.gms:play-services-measurement-api",
            "com.google.android.gms:play-services-measurement-base",
            "com.google.android.gms:play-services-measurement-impl",
            "com.google.android.gms:play-services-measurement-sdk",
            "com.google.android.gms:play-services-measurement-sdk-api",
            "com.google.android.gms:play-services-stats",
            "com.google.android.gms:play-services-tasks",
            "com.google.firebase:firebase-analytics",
            "com.google.firebase:firebase-annotations",
            "com.google.firebase:firebase-bom",
            "com.google.firebase:firebase-common",
            "com.google.firebase:firebase-common-ktx",
            "com.google.firebase:firebase-components",
            "com.google.firebase:firebase-config-interop",
            "com.google.firebase:firebase-crashlytics",
            "com.google.firebase:firebase-datatransport",
            "com.google.firebase:firebase-encoders",
            "com.google.firebase:firebase-encoders-json",
            "com.google.firebase:firebase-encoders-proto",
            "com.google.firebase:firebase-installations",
            "com.google.firebase:firebase-installations-interop",
            "com.google.firebase:firebase-measurement-connector",
            "com.google.firebase:firebase-sessions",
        )
    }
}

/**
 * Check 3 (B1 fold): trust-claim wording may exist in production source ONLY in the registered
 * claim files, so every user-facing safety claim stays on the sign-off gate and can be pulled as
 * one mechanical set. Comments are stripped before matching — only claim text that can reach the
 * user (string literals) is policed; KDoc that *talks about* the rule is fine.
 */
abstract class VerifyTrustClaimSingleSourceTask : DefaultTask() {

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val sources: ConfigurableFileCollection

    @get:Input
    abstract val rootDir: Property<String>

    @get:Input
    abstract val allowedFiles: ListProperty<String>

    @TaskAction
    fun verify() {
        val root = java.io.File(rootDir.get())
        val allowed = allowedFiles.get().toSet()
        val violations = mutableListOf<String>()

        sources.files.sortedBy { it.path }.forEach { file ->
            val relative = file.relativeTo(root).invariantSeparatorsPath
            if (relative in allowed) return@forEach

            val withoutComments = file.readText()
                .replace(BLOCK_COMMENT, "")
                .replace(LINE_COMMENT, "")
                .lowercase()
            CLAIM_PHRASES.filter { withoutComments.contains(it) }.forEach { phrase ->
                violations.add("$relative  (claim phrase: \"$phrase\")")
            }
        }

        if (violations.isNotEmpty()) {
            throw GradleException(
                """
                |TRUST-CLAIM SINGLE-SOURCE VIOLATION (§9.3 / APP-285 B1):
                |Trust-claim wording found outside the registered claim files:
                |${violations.joinToString("\n") { "  - $it" }}
                |
                |All user-facing safety claims must live in TrustCopy (or a file registered in
                |EgressGuardConventionPlugin.REGISTERED_CLAIM_FILES with Security approval) so they
                |sit on the sign-off gate and can be pulled together if egress is ever introduced.
                """.trimMargin(),
            )
        }
        logger.lifecycle("Egress guard: no unregistered trust-claim copy in production sources.")
    }

    private companion object {
        val BLOCK_COMMENT = Regex("""/\*[\s\S]*?\*/""")
        val LINE_COMMENT = Regex("""//.*""")

        /** Lowercase phrases that constitute a §9.3 safety claim. */
        val CLAIM_PHRASES = listOf(
            "never uploaded",
            "never shared",
            "never leaves your device",
            "never leave your device",
            "safe & secure",
            "safe and secure",
            "not uploaded",
            "no upload",
        )
    }
}
