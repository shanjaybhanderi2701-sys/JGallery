package com.appblish.jgallery.core.ui.format

/**
 * The single typed decode result that drives a grid tile, the viewer and Info alike — the design
 * rule engineering must honour is *"one typed decode result drives tile, viewer and Info; they never
 * disagree"* (W3 design spec §8). Wave 3 defines four visually-distinct "can't fully show this"
 * states; this type models them so E13 (images) and E14 (video) degrade through one shared path
 * instead of each inventing its own placeholder (APP-364).
 */
sealed interface MediaDecodeState {

    /** The extension, lower-cased and dot-free (e.g. `"psd"`), or empty when the name has none. */
    val extension: String

    /**
     * The decoder produced — or is expected to produce — a real preview. The happy path; renders the
     * actual image/poster with no placeholder chrome.
     */
    data object Rendered : MediaDecodeState {
        override val extension: String get() = ""
    }

    /**
     * A healthy file in a format we deliberately don't render (DOC/PSD/EPS/…). Neutral grey tile +
     * a "Can't preview this file" viewer card — the file is fine, we just can't show it (W3-01).
     */
    data class Unsupported(override val extension: String) : MediaDecodeState

    /**
     * The file's bytes are unreadable — truncated, zero-byte, or the decoder threw. Warm-red tile +
     * a "This file appears to be damaged" viewer card, visually distinct from [Unsupported] (W3-06).
     */
    data class Corrupt(override val extension: String) : MediaDecodeState

    /**
     * We render *something* but not the full fidelity (RAW embedded JPEG, SVG). Populated by E13; the
     * tile shows a real preview and the viewer an amber "best-effort" banner — graceful degradation,
     * not an error (W3-04). Modelled here so it flows through the same shared hook.
     */
    data class BestEffort(override val extension: String, val reason: String) : MediaDecodeState

    /** True when this state must be shown as a placeholder rather than a live decode. */
    val isPlaceholder: Boolean get() = this is Unsupported || this is Corrupt
}

/**
 * Decode-free classification from index metadata alone. Spec §8 is explicit that we do **not**
 * probe-decode files while indexing (that would break the §1 scroll-perf story), so this decides
 * only what can be decided cheaply up front — a zero-byte file can never decode, and a document
 * container is never an image/video — and otherwise defers to the actual decode, falling back to
 * [MediaDecodeState.Corrupt] if the decoder errors at render time.
 */
object MediaFormatSupport {

    /**
     * Container/document formats a gallery cannot render as media. Deliberately conservative: only
     * extensions we are certain are non-renderable so we never wrongly hide a decodable image behind
     * a placeholder. Unknown extensions are *not* here — they get an honest decode attempt.
     */
    private val UNSUPPORTED_EXTENSIONS: Set<String> = setOf(
        "doc", "docx", "pdf", "eps", "psd", "ai", "sketch",
        "txt", "rtf", "md", "csv", "json", "xml", "html", "htm",
        "zip", "rar", "7z", "tar", "gz",
        "xls", "xlsx", "ppt", "pptx", "key", "numbers", "pages",
        "mp3", "wav", "flac", "aac", "ogg", "m4a",
    )

    /** Lower-cased, dot-free extension of [displayName], or empty when it has none. */
    fun extensionOf(displayName: String): String =
        displayName.substringAfterLast('.', "").lowercase().trim()

    /**
     * Best up-front guess without touching the file's bytes. Returns a terminal placeholder state
     * when we can decide from metadata, or `null` meaning *"attempt the decode; if it errors, treat
     * it as [MediaDecodeState.Corrupt]."*
     *
     * @param mimeType the index's stored mime; empty/unknown is tolerated (we lean on the extension).
     * @param sizeBytes the index's stored size; `0` is treated as unreadable (a real photo is never 0 B).
     */
    fun preClassify(displayName: String, mimeType: String, sizeBytes: Long): MediaDecodeState? {
        val ext = extensionOf(displayName)
        // A zero-byte file has nothing to decode — damaged/empty, regardless of extension.
        if (sizeBytes <= 0L) return MediaDecodeState.Corrupt(ext)
        // Known document/container/audio formats: healthy, but not renderable media.
        if (ext in UNSUPPORTED_EXTENSIONS) return MediaDecodeState.Unsupported(ext)
        // An explicit non-image/non-video mime with a real subtype is likewise unrenderable.
        val mime = mimeType.lowercase().trim()
        if (mime.isNotEmpty() && mime != "application/octet-stream" &&
            !mime.startsWith("image/") && !mime.startsWith("video/")
        ) {
            return MediaDecodeState.Unsupported(ext)
        }
        // Renderable, or unknown-but-plausible: let the decoder try.
        return null
    }
}
