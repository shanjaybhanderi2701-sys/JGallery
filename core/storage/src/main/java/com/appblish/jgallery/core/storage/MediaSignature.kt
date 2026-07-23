package com.appblish.jgallery.core.storage

import com.appblish.jgallery.core.model.MediaId

/**
 * A cheap "has this item changed?" fingerprint for a single media row, read with a minimal
 * projection so the index can diff the whole library without paying for a full-column enumeration
 * (spec §1 rule 4 — enumerate once, update incrementally).
 *
 * [dateModifiedMillis] + [sizeBytes] detect content changes; [displayName] detects a pure file rename,
 * which a MediaStore `DISPLAY_NAME` write performs *without* bumping `DATE_MODIFIED` or `SIZE` (APP-590);
 * [bucketId] detects a **folder/album rename**, which relocates a row into a differently-named parent —
 * changing its bucket identity and display name but, again, neither the modified time nor the size, so
 * without it the incremental sync silently drops the album rename and the cache keeps the stale album
 * name (APP-609). Deliberately backend-agnostic: any future changed-since fast path enters the boundary
 * as an opaque sync token minted by the backend, not as a backend-specific column here.
 */
data class MediaSignature(
    val id: MediaId,
    val dateModifiedMillis: Long,
    val sizeBytes: Long,
    val displayName: String,
    val bucketId: String,
)
