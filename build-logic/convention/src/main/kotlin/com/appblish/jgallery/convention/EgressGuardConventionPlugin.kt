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
 * CI egress guard (`jgallery.egress.guard`, APP-289) — makes the §9.3 trust claim
 * ("works fully on your device … never uploaded or shared") durable BY CONSTRUCTION.
 *
 * **APP-637 — VARIANT-AWARE (release stays zero-egress).** Firebase Crashlytics is now a
 * DEBUG-ONLY testing aid ([APP-636] decision), feeding the [APP-615] MCP crash-reading loop; the
 * shipped RELEASE/benchmark artifact returns to the ORIGINAL [APP-289] hard zero-egress guarantee.
 * The guard branches per variant (see [strictZeroEgressFor]):
 *
 *  - **release / benchmark** — strict zero-egress: INTERNET + ACCESS_NETWORK_STATE (and every other
 *    network permission) are forbidden in the merged manifest, and ANY Firebase/GMS/datatransport
 *    artifact on the runtime classpath fails the build. The §9.3 "never uploaded" claim is TRUE for
 *    the shipped build again, so the trust copy STAYS (APP-616 is superseded/no-longer-required).
 *  - **debug** — the bounded [APP-614]/[APP-619] surface: INTERNET + ACCESS_NETWORK_STATE are
 *    permitted, and EXACTLY the reviewed Crashlytics artifact set in [EgressDependencyPolicy] is
 *    exempt; any other network permission, denylisted dependency, or artifact drifting in under an
 *    approved Google group still turns the debug build red.
 *
 * Three checks, aggregated under `:app:verifyNoEgress` (also wired into `check`):
 *
 *  1. [VerifyNoEgressManifestTask] — per variant, fails if a network-capable permission appears in
 *     the MERGED manifest (strict variants also forbid INTERNET + ACCESS_NETWORK_STATE), so a
 *     permission smuggled in by a library manifest is caught, not just ones declared in our sources.
 *  2. [VerifyNoEgressDependenciesTask] — per variant, fails if any coordinate in the RESOLVED
 *     runtime classpath (transitives included) is denylisted / a shipped-variant egress artifact.
 *  3. [VerifyTrustClaimSingleSourceTask] — fails if trust-claim wording appears in production
 *     source outside the registered claim files. Keeps the claim auditable in one place
 *     (TrustCopy) so it can be pulled in one edit if egress ever lands on release (B1 on APP-285).
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
            // APP-637: only the `debug` variant carries the bounded Crashlytics allowlist; release +
            // benchmark (the shipped/perf artifacts) enforce the original APP-289 zero-egress policy.
            val strict = strictZeroEgressFor(variant.buildType)

            val manifestCheck = tasks.register<VerifyNoEgressManifestTask>("verifyNoEgressManifest$variantName") {
                group = VERIFICATION_GROUP
                description = "Fails if a network-capable permission is in the $variantName merged manifest."
                mergedManifest.set(variant.artifacts.get(SingleArtifact.MERGED_MANIFEST))
                strictZeroEgress.set(strict)
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
                    strictZeroEgress.set(strict)
                }

            // Per-variant umbrella (APP-637), e.g. `:app:verifyNoEgressRelease` (strict zero-egress)
            // / `:app:verifyNoEgressDebug` (bounded Crashlytics allowlist) — the DoD entry points.
            val variantUmbrella = tasks.register("verifyNoEgress$variantName") {
                group = VERIFICATION_GROUP
                description =
                    "Runs the $variantName egress checks (${if (strict) "strict zero-egress" else "bounded Crashlytics allowlist"})."
                dependsOn(manifestCheck, dependencyCheck, claimScan)
            }

            umbrella.configure { dependsOn(variantUmbrella) }
        }

        // A plain `./gradlew check` (and anything that lifecycles through it) runs the guard too.
        tasks.named("check") { dependsOn(umbrella) }
    }

    private companion object {
        const val VERIFICATION_GROUP = "verification"

        /**
         * APP-637 variant policy: every build type EXCEPT `debug` is a shipped/perf artifact that
         * must enforce strict zero-egress. `debug` (the only build type carrying the Crashlytics
         * `debugImplementation` deps + `src/debug` manifest) gets the bounded APP-614/APP-619
         * allowlist. A null build type (defensive) is treated as strict — fail closed.
         */
        fun strictZeroEgressFor(buildType: String?): Boolean = buildType != "debug"

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

/** Check 1: no network-capable permission may survive manifest merging (variant-aware, APP-637). */
abstract class VerifyNoEgressManifestTask : DefaultTask() {

    @get:InputFile
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val mergedManifest: RegularFileProperty

    /**
     * `true` for the release/benchmark zero-egress branch: INTERNET + ACCESS_NETWORK_STATE are also
     * forbidden. `false` for the debug branch: those two are permitted (Crashlytics). APP-637.
     */
    @get:Input
    abstract val strictZeroEgress: Property<Boolean>

    @TaskAction
    fun verify() {
        val strict = strictZeroEgress.get()
        val manifest = mergedManifest.get().asFile
        val text = manifest.readText()
        val forbidden = if (strict) STRICT_FORBIDDEN_PERMISSIONS else BOUNDED_FORBIDDEN_PERMISSIONS
        val hits = forbidden.filter { text.contains(it) }
        if (hits.isNotEmpty()) {
            throw GradleException(
                if (strict) {
                    """
                    |EGRESS GUARD VIOLATION (§9.3 zero-egress — release/benchmark, APP-289/APP-637):
                    |Network-capable permission(s) found in a SHIPPED variant's merged manifest:
                    |${hits.joinToString("\n") { "  - $it" }}
                    |Merged manifest: $manifest
                    |
                    |The release/benchmark build must be zero-egress — NO network permission at all.
                    |Firebase Crashlytics is debug-only (APP-637); its INTERNET + firebase_* meta-data
                    |belong in app/src/debug/AndroidManifest.xml, never in src/main. Remove the
                    |permission (check library manifests for the merge source). If a shipped egress
                    |surface is genuinely required, that reverses the APP-636 decision — raise it as a
                    |product/privacy decision (like APP-613) before widening this guard.
                    """.trimMargin()
                } else {
                    """
                    |EGRESS GUARD VIOLATION (§9.3 trust-claim integrity — debug, APP-285/APP-289):
                    |Network-capable permission(s) found in the debug merged manifest:
                    |${hits.joinToString("\n") { "  - $it" }}
                    |Merged manifest: $manifest
                    |
                    |On debug, only INTERNET + ACCESS_NETWORK_STATE are permitted (APP-614: Firebase
                    |Crashlytics). Any OTHER network permission is still forbidden. Either remove it
                    |(check library manifests for the merge source), or — if a new egress surface is
                    |genuinely required — raise it as a product/privacy decision before widening this.
                    """.trimMargin()
                },
            )
        }
        logger.lifecycle(
            if (strict) {
                "Egress guard: shipped-variant merged manifest is zero-egress (no network permission, APP-637)."
            } else {
                "Egress guard: debug merged manifest within the approved egress surface " +
                    "(INTERNET + ACCESS_NETWORK_STATE only, APP-614)."
            },
        )
    }

    private companion object {
        /**
         * Network permissions that ALWAYS fail the build, on every variant. An unrelated egress
         * surface (wifi control, nearby devices, etc.) turns any build red.
         */
        val BOUNDED_FORBIDDEN_PERMISSIONS = listOf(
            "android.permission.CHANGE_NETWORK_STATE",
            "android.permission.ACCESS_WIFI_STATE",
            "android.permission.CHANGE_WIFI_STATE",
            "android.permission.NEARBY_WIFI_DEVICES",
        )

        /**
         * The strict (release/benchmark) set: the always-forbidden permissions PLUS INTERNET and
         * ACCESS_NETWORK_STATE, which are permitted on debug for Crashlytics but forbidden on any
         * shipped artifact so the §9.3 zero-egress guarantee holds by construction (APP-637).
         */
        val STRICT_FORBIDDEN_PERMISSIONS = listOf(
            "android.permission.INTERNET",
            "android.permission.ACCESS_NETWORK_STATE",
        ) + BOUNDED_FORBIDDEN_PERMISSIONS
    }
}

/** Check 2: no network/analytics library may enter the resolved runtime graph (transitives included). */
abstract class VerifyNoEgressDependenciesTask : DefaultTask() {

    /** Root of the resolved runtime-classpath graph (configuration-cache-safe dependency input). */
    @get:Input
    abstract val rootComponent: Property<ResolvedComponentResult>

    /**
     * `true` for the release/benchmark zero-egress branch (no Firebase exemption — ANY egress-group
     * artifact is a violation). `false` for the debug bounded-allowlist branch. APP-637.
     */
    @get:Input
    abstract val strictZeroEgress: Property<Boolean>

    @TaskAction
    fun verify() {
        val strict = strictZeroEgress.get()
        val seen = mutableSetOf<String>()
        val queue = ArrayDeque(listOf(rootComponent.get()))
        val denylisted = sortedSetOf<String>()
        val drift = sortedSetOf<String>()

        while (queue.isNotEmpty()) {
            val component = queue.removeFirst()
            if (!seen.add(component.id.displayName)) continue

            val moduleVersion = component.moduleVersion
            val group = moduleVersion?.group.orEmpty()
            val coordinate = moduleVersion?.let { "${it.group}:${it.name}" }.orEmpty()
            when (val verdict = EgressDependencyPolicy.classify(group, coordinate, strict)) {
                is EgressDependencyPolicy.Verdict.Denylisted ->
                    denylisted.add("${component.id.displayName}  (matched denylist token: '${verdict.token}')")
                EgressDependencyPolicy.Verdict.Drift ->
                    drift.add(component.id.displayName)
                EgressDependencyPolicy.Verdict.Approved,
                EgressDependencyPolicy.Verdict.Clean -> Unit
            }

            component.dependencies
                .filterIsInstance<ResolvedDependencyResult>()
                .forEach { queue.add(it.selected) }
        }

        if (denylisted.isNotEmpty() || drift.isNotEmpty()) {
            throw GradleException(
                buildString {
                    appendLine("EGRESS GUARD VIOLATION (§9.3 trust-claim integrity — see APP-285/APP-289/APP-637):")
                    if (strict) {
                        appendLine(
                            "This is a SHIPPED variant (release/benchmark) — it must be ZERO-egress (APP-637). " +
                                "Firebase Crashlytics is debug-only; any Firebase/GMS/network artifact here means " +
                                "an egress dependency leaked onto a shipped classpath. It belongs on " +
                                "`debugImplementation`, not `implementation`.",
                        )
                        appendLine()
                    }
                    if (denylisted.isNotEmpty()) {
                        appendLine("Denylisted network/analytics dependencies in the resolved runtime classpath:")
                        denylisted.forEach { appendLine("  - $it") }
                        appendLine()
                    }
                    if (drift.isNotEmpty()) {
                        appendLine(
                            "Unapproved artifact(s) under an approved egress group (APP-619 Finding 1 — the " +
                                "board approved Crashlytics ONLY, not the whole Firebase/GMS surface):",
                        )
                        drift.forEach { appendLine("  - $it") }
                        appendLine()
                    }
                    appendLine(
                        "Transitives: run `./gradlew :app:dependencies --configuration " +
                            "${if (strict) "releaseRuntimeClasspath" else "debugRuntimeClasspath"}` to find what pulls them in.",
                    )
                    appendLine(
                        "Fix a DENYLIST hit: remove/exclude the dependency (on a shipped variant, move it to " +
                            "`debugImplementation`), or — per the standing rule in the APP-285 security-signoff " +
                            "doc — pull the trust-claim copy in the SAME change and obtain a new Security sign-off.",
                    )
                    append(
                        "Fix a DRIFT hit (debug only): a new Firebase/GMS surface entered the graph. Do NOT just " +
                            "add it — get an egress + Play Data-safety review, then add the exact coordinate to " +
                            "EgressDependencyPolicy.APPROVED_EGRESS_ARTIFACTS with Security sign-off.",
                    )
                },
            )
        }
        logger.lifecycle(
            "Egress guard: resolved runtime classpath clean (${seen.size} components checked, " +
                "${if (strict) "strict zero-egress" else "debug bounded allowlist"}).",
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
