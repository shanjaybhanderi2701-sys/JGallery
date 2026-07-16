package com.appblish.jgallery.feature.search

import com.appblish.jgallery.core.model.MediaFilter
import kotlinx.coroutines.flow.Flow

/**
 * One executed search the user can re-run with a tap (parent APP-502 spec S4.4/S6). A recent is the
 * *whole* query — the typed [text] plus the facets it was run under — so re-running "cats" filtered
 * to [MediaFilter.VIDEOS] is a different recent from "cats" across everything.
 *
 * @property text the non-blank query text (trimmed).
 * @property mediaType the media-type facet the search ran under ([MediaFilter.ALL] = unfiltered).
 * @property dateFacet the date facet token the search ran under (e.g. `"This week"`, `"2024"`), or
 *   null when the search was not time-scoped. Kept as an opaque token so it stays compatible with
 *   however G3 finalises the date model.
 */
data class RecentSearch(
    val text: String,
    val mediaType: MediaFilter = MediaFilter.ALL,
    val dateFacet: String? = null,
) {
    /**
     * A canonical form suitable for persisting and de-duplicating: [text] trimmed, and a blank
     * [dateFacet] collapsed to null. Returns null when [text] is blank — a query with no text is not
     * a real search and must not be recorded (spec: "persist executed **non-empty** queries").
     */
    internal fun normalized(): RecentSearch? {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return null
        return copy(text = trimmed, dateFacet = dateFacet?.trim()?.takeIf { it.isNotEmpty() })
    }
}

/**
 * Recent-search history for the Search surface (spec S4.4/S6). Most-recent-first, de-duplicated
 * (re-running a query moves it to the top), capped at [MAX_RECENTS], and durable across process
 * death. On-device only — nothing here touches the network.
 *
 * Interface so the ViewModel unit-tests against an in-memory fake; the DataStore binding is
 * [DataStoreRecentSearchStore], provided from `di.SearchModule`.
 */
interface RecentSearchStore {

    /** The history, most-recent-first, already de-duplicated and capped at [MAX_RECENTS]. */
    val recents: Flow<List<RecentSearch>>

    /**
     * Record [query] as the most recent search. Blank-text queries are ignored. Any existing entry
     * equal to the normalized [query] is removed first, so a re-run moves to the top instead of
     * duplicating, and the list is trimmed to [MAX_RECENTS] newest entries.
     */
    suspend fun record(query: RecentSearch)

    /**
     * Remove a single entry (the per-chip ✕, design 502-D §5). Matches on the normalized form of
     * [query] so removing the chip the UI shows always hits the stored entry; a no-match is a no-op.
     */
    suspend fun remove(query: RecentSearch)

    /** Wipe the entire history. */
    suspend fun clear()

    companion object {
        /** Design cap — at most this many recents are ever kept or surfaced (spec S4.4). */
        const val MAX_RECENTS: Int = 8
    }
}
