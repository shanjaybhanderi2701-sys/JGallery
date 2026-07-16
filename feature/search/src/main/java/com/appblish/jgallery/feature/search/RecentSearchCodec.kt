package com.appblish.jgallery.feature.search

import com.appblish.jgallery.core.model.MediaFilter
import java.util.Base64

/**
 * (De)serialises the recent-search list to the single string DataStore persists (mirrors the
 * bin-manifest codec in `:core:storage`): one entry per line, tab-separated fields, with the
 * free-text [RecentSearch.text] / [RecentSearch.dateFacet] Base64-encoded so a query containing
 * tabs or newlines can never tear the record grid. A line that fails to parse (corruption, an enum
 * name removed in a future version) is skipped, never fatal to the rest of the history.
 *
 * Line format: `base64(text) \t mediaTypeName \t base64(dateFacet)` — the third field is empty when
 * there is no date facet.
 */
internal object RecentSearchCodec {

    fun encode(recents: List<RecentSearch>): String =
        recents.joinToString("\n") { r ->
            "${enc(r.text)}\t${r.mediaType.name}\t${r.dateFacet?.let(::enc).orEmpty()}"
        }

    fun decode(text: String): List<RecentSearch> =
        text.lineSequence()
            .filter { it.isNotBlank() }
            .mapNotNull(::parse)
            .toList()

    private fun parse(line: String): RecentSearch? {
        val fields = line.split("\t")
        if (fields.size != 3) return null
        return runCatching {
            RecentSearch(
                text = dec(fields[0]),
                mediaType = MediaFilter.valueOf(fields[1]),
                dateFacet = fields[2].takeIf { it.isNotEmpty() }?.let(::dec),
            )
        }.getOrNull()
    }

    private fun enc(value: String): String =
        Base64.getEncoder().encodeToString(value.toByteArray(Charsets.UTF_8))

    private fun dec(value: String): String =
        String(Base64.getDecoder().decode(value), Charsets.UTF_8)
}
