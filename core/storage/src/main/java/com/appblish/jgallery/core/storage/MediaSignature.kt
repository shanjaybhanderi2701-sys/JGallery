package com.appblish.jgallery.core.storage

import com.appblish.jgallery.core.model.MediaId

/**
 * A cheap "has this item changed?" fingerprint for a single media row, read with a minimal
 * projection so the index can diff the whole library without paying for a full-column enumeration
 * (spec §1 rule 4 — enumerate once, update incrementally).
 *
 * [dateModifiedMillis] + [sizeBytes] detect content changes. Deliberately backend-agnostic: any
 * future changed-since fast path enters the boundary as an opaque sync token minted by the backend,
 * not as a backend-specific column surfaced here.
 */
data class MediaSignature(
    val id: MediaId,
    val dateModifiedMillis: Long,
    val sizeBytes: Long,
)
