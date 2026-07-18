package com.appblish.jgallery.core.ui.share

import android.content.ClipData
import android.content.Intent
import android.net.Uri

/**
 * A resolved "Share selection" request (G2 · APP-541), emitted by a ViewModel and consumed by its
 * screen to fire the system share sheet. Shared by the Photos tab and album detail so both surfaces
 * share one shape and one launch path.
 */
sealed interface MediaShareRequest {
    /** Ready to share [uris] tagged with the narrowed [mimeType] (see [ShareIntents.commonMimeType]). */
    data class Ready(val uris: List<Uri>, val mimeType: String) : MediaShareRequest

    /** Nothing shareable remained (every selected item was deleted underneath us). */
    data object Empty : MediaShareRequest
}

/**
 * Builds the system share-sheet intent for a multi-select "Share" (G2 · APP-541). The media uris are
 * the §1.6-sanctioned MediaStore `content://` uris (the same deliberate cross-boundary exposure the
 * viewer's "Set as" / "Open with" already use — APP-297); JGallery declares **no** FileProvider, so
 * nothing app-private is ever exposed. Every uri is handed out read-only and temporarily via
 * [Intent.FLAG_GRANT_READ_URI_PERMISSION] plus a [ClipData] carrying all uris, so the grant reliably
 * propagates to whichever app the user picks in the chooser (the flag alone only covers the primary
 * data uri; [ClipData] covers the whole `EXTRA_STREAM` list on every API level).
 *
 * The pure [commonMimeType] narrowing is the only real logic here, so it is unit-tested on the JVM;
 * [buildSendIntent] is a thin, device-verified assembly around the platform [Intent].
 */
object ShareIntents {

    /**
     * The single MIME type to tag the share intent with, narrowed from the per-item [perItemMime]
     * (nullable — an unresolved type). Exact match wins ("image/jpeg" for a homogeneous batch); a
     * mixed batch that still shares a top-level type collapses to "image/&#42;" / "video/&#42;"; anything
     * else — an empty list, any unknown type, or a genuine image+video mix — falls back to the
     * universal "&#42;/&#42;" so no capable target is filtered out of the chooser.
     */
    fun commonMimeType(perItemMime: List<String?>): String {
        if (perItemMime.isEmpty()) return ANY
        // A single unresolved type means we can't safely narrow — offer everything.
        val types = perItemMime.map { it?.takeIf { t -> t.isNotBlank() } ?: return ANY }
        val distinct = types.distinct()
        if (distinct.size == 1) return distinct.first()
        val topLevels = distinct.map { it.substringBefore('/') }.distinct()
        return if (topLevels.size == 1) "${topLevels.first()}/*" else ANY
    }

    /**
     * Assemble the share intent. One selected item uses [Intent.ACTION_SEND] with a single
     * `EXTRA_STREAM`; two or more use [Intent.ACTION_SEND_MULTIPLE] with the parcelable list. The
     * caller wraps the result in [Intent.createChooser] and launches it (guarded by `runCatching`, as
     * the viewer's intents are, so a device with no share target degrades gracefully).
     */
    fun buildSendIntent(uris: List<Uri>, mimeType: String): Intent {
        require(uris.isNotEmpty()) { "Cannot build a share intent with no uris" }
        val intent = if (uris.size == 1) {
            Intent(Intent.ACTION_SEND).apply {
                putExtra(Intent.EXTRA_STREAM, uris.first())
            }
        } else {
            Intent(Intent.ACTION_SEND_MULTIPLE).apply {
                putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList(uris))
            }
        }
        return intent.apply {
            type = mimeType
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            // Attach every uri as ClipData so the temporary read grant reaches the chosen app for the
            // whole EXTRA_STREAM list, not just the primary data uri.
            clipData = ClipData.newUri(null, "shared media", uris.first()).also { clip ->
                uris.drop(1).forEach { clip.addItem(ClipData.Item(it)) }
            }
        }
    }

    private const val ANY = "*/*"
}
