package com.appblish.jgallery.core.storage

import com.appblish.jgallery.core.model.MediaId

/**
 * A cheap "has this item changed?" fingerprint for a single media row, read with a minimal
 * projection so the index can diff the whole library without paying for a full-column enumeration
 * (spec §1 rule 4 — enumerate once, update incrementally).
 *
 * [dateModifiedMillis] + [sizeBytes] detect content changes; [displayName] detects a pure rename,
 * which a MediaStore `DISPLAY_NAME` write performs *without* bumping `DATE_MODIFIED` or `SIZE` — so
 * without this column an incremental sync would silently drop the rename and the cache would keep the
 * stale name (APP-590). Deliberately backend-agnostic: any future changed-since fast path enters the
 * boundary as an opaque sync token minted by the backend, not as a backend-specific column here.
 */
data class MediaSignature(
    val id: MediaId,
    val dateModifiedMillis: Long,
    val sizeBytes: Long,
    val displayName: String,
)
