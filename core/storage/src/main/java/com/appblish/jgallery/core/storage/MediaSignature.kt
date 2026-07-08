package com.appblish.jgallery.core.storage

import com.appblish.jgallery.core.model.MediaId

/**
 * A cheap "has this item changed?" fingerprint for a single media row, read with a minimal
 * projection so the index can diff the whole library without paying for a full-column enumeration
 * (spec §1 rule 4 — enumerate once, update incrementally).
 *
 * [dateModifiedMillis] + [sizeBytes] detect content changes; [generation] carries MediaStore's
 * monotonic `GENERATION_MODIFIED` on API 30+ (0 below that), enabling a future changed-since-token
 * fast path via [MediaQuery.changedSinceGeneration] without re-reading unchanged rows.
 */
data class MediaSignature(
    val id: MediaId,
    val dateModifiedMillis: Long,
    val sizeBytes: Long,
    val generation: Long,
)
