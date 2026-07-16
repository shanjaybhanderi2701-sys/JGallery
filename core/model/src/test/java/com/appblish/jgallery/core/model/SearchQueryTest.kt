package com.appblish.jgallery.core.model

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * The APP-502 §4.1 search domain: filename text matching (case/diacritic-insensitive substring),
 * the media-type + date facets, AND semantics, and the empty-query-yields-nothing rule (spec §5
 * acceptance items 1, 2, 4).
 */
class SearchQueryTest {

    private fun item(
        id: String = "1",
        name: String = "photo.jpg",
        type: MediaType = MediaType.IMAGE,
        mime: String = "image/jpeg",
        dateTaken: Long = 0L,
    ) = MediaItem(
        id = MediaId(id),
        displayName = name,
        type = type,
        bucketId = "b",
        bucketName = "b",
        dateTakenMillis = dateTaken,
        dateModifiedMillis = 0,
        sizeBytes = 0,
        width = 100,
        height = 100,
        durationMillis = 0,
        mimeType = mime,
    )

    private val invoice = item(id = "i", name = "Invoice-March.pdf.jpg")
    private val cafe = item(id = "c", name = "Café-Latté.png", mime = "image/png")
    private val video = item(id = "v", name = "invoice-clip.mp4", type = MediaType.VIDEO, mime = "video/mp4")
    private val gif = item(id = "g", name = "invoice.gif", mime = "image/gif")

    // --- AC1: filename substring, case- and diacritic-insensitive ---

    @Test
    fun `text is a case-insensitive substring of displayName`() {
        assertThat(SearchQuery(text = "invoice").matches(invoice)).isTrue()
        assertThat(SearchQuery(text = "INVOICE").matches(invoice)).isTrue()
        assertThat(SearchQuery(text = "march").matches(invoice)).isTrue()
        assertThat(SearchQuery(text = "receipt").matches(invoice)).isFalse()
    }

    @Test
    fun `diacritics are stripped on both sides`() {
        assertThat(SearchQuery(text = "cafe").matches(cafe)).isTrue()
        assertThat(SearchQuery(text = "latte").matches(cafe)).isTrue()
        assertThat(SearchQuery(text = "café").matches(cafe)).isTrue()
        assertThat(SearchQuery(text = "  LATTÉ ").matches(cafe)).isTrue() // trimmed + folded
    }

    @Test
    fun `blank text passes every item`() {
        listOf(invoice, cafe, video, gif).forEach {
            assertThat(SearchQuery(text = "   ").matches(it)).isTrue()
        }
    }

    // --- AC2: media-type facet + AND with text ---

    @Test
    fun `mediaType facet reuses MediaFilter`() {
        val q = SearchQuery(mediaType = MediaFilter.VIDEOS)
        assertThat(q.matches(video)).isTrue()
        assertThat(q.matches(invoice)).isFalse()
        assertThat(SearchQuery(mediaType = MediaFilter.GIFS).matches(gif)).isTrue()
    }

    @Test
    fun `text AND mediaType both must match`() {
        val photosNamedInvoice = SearchQuery(text = "invoice", mediaType = MediaFilter.PHOTOS)
        assertThat(photosNamedInvoice.matches(invoice)).isTrue() // photo + name match
        assertThat(photosNamedInvoice.matches(video)).isFalse()  // name match but wrong type
        assertThat(photosNamedInvoice.matches(gif)).isFalse()    // gif is not a photo
    }

    // --- date facet + AND ---

    @Test
    fun `dateRange facet gates on dateTakenMillis half-open`() {
        val a = item(id = "a", name = "a.jpg", dateTaken = 1_000L)
        val b = item(id = "b", name = "b.jpg", dateTaken = 2_000L)
        val q = SearchQuery(dateRange = DateRange(1_000L, 2_000L))
        assertThat(q.matches(a)).isTrue()  // start inclusive
        assertThat(q.matches(b)).isFalse() // end exclusive
    }

    @Test
    fun `all three facets AND together`() {
        val hit = item(id = "h", name = "invoice.jpg", dateTaken = 1_500L)
        val q = SearchQuery(text = "invoice", mediaType = MediaFilter.PHOTOS, dateRange = DateRange(1_000L, 2_000L))
        assertThat(q.matches(hit)).isTrue()
        assertThat(q.matches(item(id = "x", name = "invoice.jpg", dateTaken = 5_000L))).isFalse() // out of range
    }

    // --- AC4: empty query yields nothing via matching() ---

    @Test
    fun `isEmpty only when no facet constrains`() {
        assertThat(SearchQuery().isEmpty).isTrue()
        assertThat(SearchQuery(text = " ").isEmpty).isTrue()
        assertThat(SearchQuery(text = "x").isEmpty).isFalse()
        assertThat(SearchQuery(mediaType = MediaFilter.VIDEOS).isEmpty).isFalse()
        assertThat(SearchQuery(dateRange = DateRange(0, 1)).isEmpty).isFalse()
    }

    @Test
    fun `matching on an empty query returns no results not the whole library`() {
        val library = listOf(invoice, cafe, video, gif)
        assertThat(library.matching(SearchQuery())).isEmpty()
        assertThat(library.matching(SearchQuery(text = "  "))).isEmpty()
    }

    @Test
    fun `matching filters and preserves original order`() {
        val library = listOf(invoice, video, gif) // all contain "invoice"
        val result = library.matching(SearchQuery(text = "invoice", mediaType = MediaFilter.PHOTOS))
        assertThat(result).containsExactly(invoice) // only the photo
        val allInvoice = library.matching(SearchQuery(text = "invoice"))
        assertThat(allInvoice).containsExactly(invoice, video, gif).inOrder()
    }

    @Test
    fun `normalizeForSearch folds case trim and diacritics`() {
        assertThat("  Ólá Múndo ".normalizeForSearch()).isEqualTo("ola mundo")
        assertThat("PLAIN".normalizeForSearch()).isEqualTo("plain")
    }
}
