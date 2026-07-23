package com.appblish.jgallery.core.index.internal

import com.appblish.jgallery.core.model.MediaId

/**
 * A cheap per-row fingerprint used to decide what changed between two index states. `date-modified`
 * + `size` is the standard MediaStore content-change heuristic; [displayName] additionally catches a
 * pure rename, which changes neither the modified time nor the size, so without it the incremental
 * sync would drop the rename and leave a stale name in the cache (APP-590). Pure Kotlin so the diff
 * is unit-testable without Android.
 */
internal data class IndexSignature(
    val id: MediaId,
    val dateModifiedMillis: Long,
    val sizeBytes: Long,
    val displayName: String,
)

/** The rows to re-read (new or modified) and the rows to drop (gone from the device) since last sync. */
internal data class IndexDelta(
    val changedIds: Set<MediaId>,
    val deletedIds: Set<MediaId>,
) {
    val isEmpty: Boolean get() = changedIds.isEmpty() && deletedIds.isEmpty()

    companion object {
        val EMPTY = IndexDelta(emptySet(), emptySet())
    }
}

/**
 * Diff the persisted index signatures against a fresh scan of the current library. This is what makes
 * the index *incremental*: unchanged rows appear in neither set, so they are never re-read from the
 * provider nor re-written to the cache — opening the app does not trigger a full re-index (spec §1
 * rule 4). Deletions are caught by "was persisted, no longer present", which a changed-since-token
 * query cannot see — hence the full (but minimal-projection) signature comparison.
 *
 * [changedIds] preserves the encounter order of [current] so downstream re-reads stay stable/testable.
 */
internal fun computeIndexDelta(
    persisted: List<IndexSignature>,
    current: List<IndexSignature>,
): IndexDelta {
    val persistedById = HashMap<MediaId, IndexSignature>(persisted.size)
    for (sig in persisted) persistedById[sig.id] = sig

    val currentIds = HashSet<MediaId>(current.size)
    val changed = LinkedHashSet<MediaId>()
    for (sig in current) {
        currentIds += sig.id
        val prev = persistedById[sig.id]
        val isNewOrModified = prev == null ||
            prev.dateModifiedMillis != sig.dateModifiedMillis ||
            prev.sizeBytes != sig.sizeBytes ||
            prev.displayName != sig.displayName // a pure rename bumps neither date nor size (APP-590)
        if (isNewOrModified) changed += sig.id
    }

    val deleted = LinkedHashSet<MediaId>()
    for (sig in persisted) {
        if (sig.id !in currentIds) deleted += sig.id
    }

    return IndexDelta(changedIds = changed, deletedIds = deleted)
}
