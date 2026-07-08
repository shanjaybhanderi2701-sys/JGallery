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
 * ("works fully on your device … never uploaded or shared") durable BY CONSTRUCTION, not by
 * convention. The Security sign-off on the claim (APP-285 `security-signoff` doc) is valid only
 * while this guard is green; if egress capability is ever introduced, the build MUST go red until
 * the claim copy is pulled (standing rule in the sign-off doc).
 *
 * Three checks, aggregated under `:app:verifyNoEgress` (also wired into `check`):
 *
 *  1. [VerifyNoEgressManifestTask] — per variant, fails if any network-capable permission
 *     (INTERNET, network/wifi state) appears in the MERGED manifest, so a permission smuggled in
 *     by a library manifest is caught, not just ones declared in our sources.
 *  2. [VerifyNoEgressDependenciesTask] — per variant, fails if any coordinate in the RESOLVED
 *     runtime classpath (transitives included) matches the network/analytics denylist.
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
                |The "never uploaded" trust claim is true only while egress is impossible at the OS
                |level. Either remove the permission (check library manifests for the merge source),
                |or — per the standing rule in the APP-285 security-signoff doc — pull the trust
                |claim copy (TrustCopy + registered claim files) in the SAME change and obtain a new
                |Security sign-off.
                """.trimMargin(),
            )
        }
        logger.lifecycle("Egress guard: merged manifest clean (no network-capable permissions).")
    }

    private companion object {
        val FORBIDDEN_PERMISSIONS = listOf(
            "android.permission.INTERNET",
            "android.permission.ACCESS_NETWORK_STATE",
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

            val coordinate = component.moduleVersion?.let { "${it.group}:${it.name}" }.orEmpty().lowercase()
            DENYLIST.firstOrNull { coordinate.contains(it) }?.let {
                violations.add("${component.id.displayName}  (matched denylist token: '$it')")
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
