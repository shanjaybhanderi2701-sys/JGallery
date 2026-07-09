package com.appblish.jgallery.core.model

/**
 * One entry in the app-managed Recycle Bin (spec §7.5). A media item that has been moved to Trash and
 * is held, restorable, for [TrashPolicy.RETENTION_DAYS] before it is purged for good.
 *
 * This carries the **retention metadata** the design mandates: [trashedAtMillis] (so days-left is
 * computed from the app's own 30-day policy, not a provider quirk) and the *original* location
 * ([originalBucketId] / [originalBucketName] / [originalRelativePath]) so a restore knows exactly
 * where the item came from — even if the storage backend later migrates off MediaStore's trash flag.
 *
 * It also snapshots enough of the media row ([type], [mimeType], dimensions, [durationMillis]) to
 * render the Trash grid tile without re-querying a row that is, by definition, hidden from the index.
 */
data class TrashEntry(
    val id: MediaId,
    val displayName: String,
    val type: MediaType,
    val mimeType: String,
    /** Bucket id the item lived in before it was trashed — the restore destination. */
    val originalBucketId: String,
    /** Human-readable album/folder name of the original location (for the UI, not addressing). */
    val originalBucketName: String,
    /** `RELATIVE_PATH` the item is restored into; the folder is recreated if it no longer exists. */
    val originalRelativePath: String,
    /** When the item entered the bin (epoch-millis) — the anchor for the 30-day retention window. */
    val trashedAtMillis: Long,
    val sizeBytes: Long,
    val width: Int,
    val height: Int,
    val durationMillis: Long, // 0 for images
)

/**
 * The single source of truth for the Recycle Bin's retention policy (spec §7.5, design W2-09). Pure
 * arithmetic so both the storage engine (purge) and the UI (days-left badge) agree on the numbers and
 * both are trivially unit-testable off-device.
 */
object TrashPolicy {

    /** Items are auto-purged this many days after they are trashed (design: "deleted after 30 days"). */
    const val RETENTION_DAYS = 30

    /** At or below this many days left, the tile's badge turns amber to warn before auto-deletion. */
    const val WARN_THRESHOLD_DAYS = 5

    private const val DAY_MILLIS = 24L * 60L * 60L * 1000L

    /** Total retention window in millis. */
    const val RETENTION_MILLIS = RETENTION_DAYS * DAY_MILLIS

    /** The instant an item trashed at [trashedAtMillis] becomes eligible for permanent purge. */
    fun expiresAtMillis(trashedAtMillis: Long): Long = trashedAtMillis + RETENTION_MILLIS

    /**
     * Whole days remaining before auto-purge, floored and never negative. Floored (not ceiled) so a
     * freshly-trashed item reads "29d left" — the honest count of *full* days it is guaranteed to
     * survive — matching the W2-09 design.
     */
    fun daysLeft(trashedAtMillis: Long, nowMillis: Long): Int {
        val remaining = expiresAtMillis(trashedAtMillis) - nowMillis
        return if (remaining <= 0L) 0 else (remaining / DAY_MILLIS).toInt()
    }

    /** True once the retention window has elapsed and the item should be permanently removed. */
    fun isExpired(trashedAtMillis: Long, nowMillis: Long): Boolean =
        nowMillis >= expiresAtMillis(trashedAtMillis)

    /** True while the item is still held but within the amber warning band (≤ [WARN_THRESHOLD_DAYS]). */
    fun isExpiringSoon(trashedAtMillis: Long, nowMillis: Long): Boolean =
        !isExpired(trashedAtMillis, nowMillis) &&
            daysLeft(trashedAtMillis, nowMillis) <= WARN_THRESHOLD_DAYS
}
