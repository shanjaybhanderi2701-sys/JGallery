package com.appblish.jgallery.core.model

/**
 * Declarative media query (spec §1 rule 4). Lives in `:core:model` — not `:core:storage` — because
 * it is platform-free and features must be able to construct one for
 * `MediaIndexRepository.observeMedia` without seeing the storage module; the §1.6 boundary
 * translates it into a MediaStore selection privately.
 */
data class MediaQuery(
    val bucketId: String? = null, // null = all buckets (Photos tab); set = one album
    val types: Set<MediaType> = setOf(MediaType.IMAGE, MediaType.VIDEO),
    val sort: SortSpec = SortSpec(),
    val limit: Int? = null,
    val offset: Int = 0,
    /**
     * Restrict to these specific ids. The index uses this to re-read only the rows a delta scan
     * flagged as new/changed, instead of re-enumerating the whole library (spec §1 rule 4). `null`
     * = no id restriction; an empty set matches nothing.
     */
    val ids: Set<MediaId>? = null,
)
