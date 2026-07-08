package com.appblish.jgallery.core.storage

import com.appblish.jgallery.core.model.MediaId
import com.appblish.jgallery.core.model.MediaType
import com.appblish.jgallery.core.model.SortSpec

/** Declarative query the boundary translates into a MediaStore selection (spec §1 rule 4). */
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
