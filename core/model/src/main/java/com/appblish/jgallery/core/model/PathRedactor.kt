package com.appblish.jgallery.core.model

/**
 * Strips filesystem paths, document/content URIs, and bare file/folder names out of arbitrary
 * text so diagnostic strings never carry user content off the device
 * (APP-628 — child of the APP-619 Crashlytics egress review, finding 3).
 *
 * JGallery is a file/media manager: an exception message routinely embeds the exact path or name
 * the user was acting on (e.g. `EACCES … /storage/emulated/0/DCIM/holiday.jpg`, or a SAF
 * `content://…/tree/primary%3ADCIM%2Fsecret.jpg`). Firebase Crashlytics (APP-614) uploads exception
 * messages verbatim, so any such path/name would leave the device as diagnostics PII. The standing
 * rule (see `docs/security/crashlytics-data-handling.md`) is therefore: **keep the reason/errno,
 * drop the name.** Storage-layer code that constructs or rethrows an IO/SAF exception runs its
 * message through [redact] (for values that may contain a path/URI) or interpolates [redactName]
 * (for values known to be a bare name) first.
 *
 * This is the *call-site* guard (item 1 of the spec). The process-wide
 * [com.appblish.jgallery.crash.CrashlyticsMessageSanitizer] uncaught-exception handler (APP-626,
 * item 2) is the defence-in-depth net for OS/framework messages that never pass through our code;
 * on merge that handler should delegate here so there is a single redaction source of truth.
 *
 * Pure Kotlin — no Android dependency — so it lives in `:core:model` and is fully unit-testable on
 * the JVM.
 */
object PathRedactor {
    /** Placeholder substituted for an absolute path or a redacted name with no type hint. */
    const val REDACTED_PATH: String = "<redacted-path>"

    /** Placeholder substituted for a document/content/file URI. */
    const val REDACTED_URI: String = "<redacted-uri>"

    // A URI: `scheme://opaque` (content://, file://, http://…). The opaque part runs to the next
    // whitespace/quote/angle bracket so the whole thing — including any encoded path such as
    // `…/tree/primary%3ADCIM%2Fsecret` — is consumed in one go.
    private val URI = Regex("""\b[a-zA-Z][a-zA-Z0-9+.\-]*://[^\s"'<>]+""")

    // An absolute POSIX path with at least two separators, e.g. `/storage/emulated/0/x.jpg` or
    // `/a/b`. Requiring `(segment/)+ segment*` means a lone root (`/data`) and, more importantly,
    // prose or MIME types with a single slash (`application/octet-stream`, `and/or`, `TCP/IP`) are
    // left untouched — only real path-shaped tokens are redacted. NOTE: a *relative* path (no
    // leading `/`, e.g. a bucket-relative `DCIM/holiday.jpg`) is deliberately NOT matched here, so
    // never feed a value that may be a relative path into [redact] expecting it to be stripped —
    // use [redactName] for such values instead.
    private val ABSOLUTE_PATH = Regex("""/(?:[^\s/\\"'<>|:*?]+/)+[^\s/\\"'<>|:*?]*""")

    /**
     * Returns [message] with every embedded document/content URI and absolute path replaced by a
     * fixed placeholder. Reason words, errno codes (EACCES/ENOENT), and numbers survive. URIs are
     * stripped before paths so an encoded path inside a URI is not partially left behind.
     */
    fun redact(message: String): String =
        message
            .replace(URI, REDACTED_URI)
            .replace(ABSOLUTE_PATH, REDACTED_PATH)

    /** Null-tolerant variant for values such as [Throwable.message]. */
    fun redactOrNull(message: String?): String? = message?.let(::redact)

    /**
     * Redacts a value that is known to be a bare file/folder name (no reliable directory component),
     * preserving only a simple file extension as a non-identifying type hint:
     * `holiday.jpg` -> `<redacted-path>.jpg`, `Secret Folder` -> `<redacted-path>`,
     * `.hidden` (a dotfile, no real extension) -> `<redacted-path>`. Because it never trusts the
     * stem, this is also the safe choice for a possibly-relative path (e.g. a bucket id) that
     * [redact] would leave untouched.
     */
    fun redactName(name: String?): String {
        if (name.isNullOrBlank()) return REDACTED_PATH
        val dot = name.lastIndexOf('.')
        // A leading-dot dotfile or a trailing dot carries no usable extension.
        val hasExt = dot in 1 until name.length - 1
        val ext = if (hasExt) name.substring(dot + 1) else null
        return if (ext != null && ext.all(Char::isLetterOrDigit)) "$REDACTED_PATH.$ext" else REDACTED_PATH
    }
}
