package com.appblish.jgallery.feature.search

import com.appblish.jgallery.core.model.MediaFilter
import com.google.common.truth.Truth.assertThat
import org.junit.Test

/** JVM coverage for the recent-search (de)serialization — round-trip fidelity + corruption tolerance. */
class RecentSearchCodecTest {

    @Test
    fun `encode then decode round-trips text and both facets`() {
        val recents = listOf(
            RecentSearch("beach", MediaFilter.PHOTOS, dateFacet = "2024"),
            RecentSearch("clip", MediaFilter.VIDEOS, dateFacet = null),
            RecentSearch("misc", MediaFilter.ALL, dateFacet = "This week"),
        )
        val decoded = RecentSearchCodec.decode(RecentSearchCodec.encode(recents))
        assertThat(decoded).isEqualTo(recents)
    }

    @Test
    fun `free-text queries with tabs and newlines survive (base64-encoded, cannot break the grid)`() {
        val nasty = RecentSearch("we\tird\nquery (1)", MediaFilter.ALL, dateFacet = "od\td\nfacet")
        val decoded = RecentSearchCodec.decode(RecentSearchCodec.encode(listOf(nasty)))
        assertThat(decoded).containsExactly(nasty)
    }

    @Test
    fun `an empty history encodes and decodes to empty`() {
        assertThat(RecentSearchCodec.encode(emptyList())).isEmpty()
        assertThat(RecentSearchCodec.decode("")).isEmpty()
    }

    @Test
    fun `a corrupt or unknown-enum line is skipped, not fatal to the rest`() {
        val good = RecentSearchCodec.encode(
            listOf(RecentSearch("a", MediaFilter.ALL), RecentSearch("b", MediaFilter.VIDEOS)),
        )
        val corrupted = "garbage-no-tabs\n$good\nQVBQ\tNOT_A_FILTER\t"
        val decoded = RecentSearchCodec.decode(corrupted)
        assertThat(decoded.map { it.text }).containsExactly("a", "b")
    }
}
