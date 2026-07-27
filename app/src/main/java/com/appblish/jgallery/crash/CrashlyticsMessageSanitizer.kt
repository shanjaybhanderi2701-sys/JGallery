package com.appblish.jgallery.crash

import java.lang.reflect.Field
import java.util.Collections
import java.util.IdentityHashMap

/**
 * Uncaught-exception handler that strips user file paths / names out of exception **messages**
 * before Crashlytics uploads them off-device (APP-626, closing APP-619 Finding 3 from the APP-618
 * security review).
 *
 * Filora is a file manager, so the platform/framework exceptions the OS builds routinely embed real
 * user paths and filenames, e.g.
 * `java.io.IOException: open failed: EACCES (Permission denied): /storage/emulated/0/DCIM/x.jpg`
 * (cf. APP-574 `.vaultkey`). Crashlytics captures those verbatim on any uncaught crash — that is
 * user content leaving the device. The standing rule in
 * `docs/security/crashlytics-data-handling.md` keeps the strings **we** author clean, but it cannot
 * govern OS-generated messages; this handler closes that residual gap.
 *
 * Design (see the doc, §"Residual risk the rule cannot cover — the sanitizer"):
 *  - Installed from [JGalleryApplication.onCreate] **after** `super.onCreate()`, so Crashlytics'
 *    auto-init handler is already the current default and is captured here as the [delegate]. We
 *    forward a sanitized throwable to it — we do **not** replace the upload path.
 *  - Only the **message** of the throwable and its `cause` / `suppressed` chain is scrubbed.
 *    Exception **type** and **stack frames** are preserved untouched: they are needed for triage
 *    and do not normally carry user content.
 *  - Scrubbing is done in place (reflection on `Throwable.detailMessage`) so object identity, type,
 *    stack frames and cause structure survive exactly as Crashlytics expects. If that reflection is
 *    unavailable the handler degrades to a no-op scrub rather than dropping the crash — reporting
 *    reliability is never sacrificed.
 */
object CrashlyticsMessageSanitizer {

    /**
     * Absolute Android storage paths, consumed up to the next whitespace / quote / closing bracket
     * / comma. Covers shared storage (`/storage`, `/sdcard`, `/mnt`) and app-private (`/data/...`)
     * roots, both of which can embed a user filename in an OS `EACCES`/`ENOENT` message.
     */
    private val ABS_PATH = Regex("""/(?:storage|sdcard|mnt|data)/[^\s"'`)\]},]+""")

    /**
     * A bare filename (not already inside an absolute path) carrying a media / document / sentinel
     * extension. Anchored so it does not start mid-word or immediately after a path separator.
     */
    private val FILENAME = Regex(
        """(?<![\w/.])[\w%+\-.]*[\w%+\-]\.(?:jpg|jpeg|png|gif|webp|heic|heif|bmp|dng|raw|""" +
            """mp4|mkv|mov|webm|3gp|avi|m4v|mp3|wav|flac|ogg|opus|pdf|txt|zip|vaultkey)\b""",
        RegexOption.IGNORE_CASE,
    )

    private const val REDACTED_PATH = "/<redacted-path>"
    private const val REDACTED_FILE = "<redacted-file>"

    /**
     * Scrub absolute storage paths and bare media/document filenames out of a single message
     * string. Returns the input unchanged when there is nothing to redact (including null/empty),
     * so callers can cheaply detect a no-op via referential/`==` equality.
     */
    fun scrub(message: String?): String? {
        if (message.isNullOrEmpty()) return message
        return message
            .replace(ABS_PATH, REDACTED_PATH)
            .replace(FILENAME, REDACTED_FILE)
    }

    /**
     * Chain this sanitizer in FRONT of the current default handler (Crashlytics' auto-init handler).
     * Idempotent: a second call is a no-op, so it is safe to invoke unconditionally from
     * [JGalleryApplication.onCreate].
     */
    fun install() {
        val current = Thread.getDefaultUncaughtExceptionHandler()
        if (current is SanitizingHandler) return // already installed
        Thread.setDefaultUncaughtExceptionHandler(SanitizingHandler(current))
    }

    private class SanitizingHandler(
        private val delegate: Thread.UncaughtExceptionHandler?,
    ) : Thread.UncaughtExceptionHandler {
        override fun uncaughtException(thread: Thread, throwable: Throwable) {
            val safe = try {
                sanitizeChain(throwable)
            } catch (_: Throwable) {
                throwable // never swallow a crash because sanitizing failed
            }
            delegate?.uncaughtException(thread, safe)
        }
    }

    /**
     * Scrub the message of [root] and every throwable reachable via `cause` / `suppressed`, in
     * place, and return [root]. In-place mutation preserves object identity, exception type and
     * stack frames exactly. Identity tracking guards against cause cycles.
     */
    fun sanitizeChain(root: Throwable): Throwable {
        val seen = Collections.newSetFromMap(IdentityHashMap<Throwable, Boolean>())
        val pending = ArrayDeque<Throwable>()
        pending.addLast(root)
        while (pending.isNotEmpty()) {
            val t = pending.removeLast()
            if (!seen.add(t)) continue
            scrubInPlace(t)
            t.cause?.let(pending::addLast)
            for (suppressed in t.suppressed) pending.addLast(suppressed)
        }
        return root
    }

    private fun scrubInPlace(t: Throwable) {
        val original = t.message ?: return
        val cleaned = scrub(original) ?: return
        if (cleaned == original) return
        val field = detailMessageField ?: return
        try {
            field.set(t, cleaned)
        } catch (_: Throwable) {
            // Reflection blocked at runtime: leave the message as-is rather than crash the handler.
        }
    }

    /** `java.lang.Throwable.detailMessage`, resolved once; null if reflection is unavailable. */
    private val detailMessageField: Field? by lazy {
        try {
            Throwable::class.java.getDeclaredField("detailMessage").apply { isAccessible = true }
        } catch (_: Throwable) {
            null
        }
    }
}
