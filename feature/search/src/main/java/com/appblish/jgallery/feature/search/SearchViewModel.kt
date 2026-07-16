package com.appblish.jgallery.feature.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.appblish.jgallery.core.index.MediaIndexRepository
import com.appblish.jgallery.core.model.ColumnCount
import com.appblish.jgallery.core.model.MediaFilter
import com.appblish.jgallery.core.model.MediaItem
import com.appblish.jgallery.core.model.MediaQuery
import com.appblish.jgallery.core.model.SearchQuery
import com.appblish.jgallery.core.model.matching
import com.appblish.jgallery.feature.search.di.SearchDispatcher
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Search UI state (design 502-D §3, spec §4.5). Three states map 1:1 to the redline:
 * - [Empty]: nothing typed yet (blank text + All + no date) — shows recents (or the hero) and both
 *   live filter rows. Per §4.1/AC-4 the screen **stays here** on a blank query; it never dumps the
 *   whole library.
 * - [Results]: a non-empty query with ≥1 match — the shared media grid plus a count line.
 * - [NoResults]: a real, non-empty query that matched nothing — a friendly, editable dead-end-free
 *   state that names the query (AC-8).
 *
 * [Results]/[NoResults] carry the active facets so the screen can render the count line and keep the
 * chip rows in sync without re-reading the ViewModel's private state.
 */
sealed interface SearchUiState {
    data class Empty(val recents: List<RecentSearch>) : SearchUiState

    data class Results(
        val items: List<MediaItem>,
        val text: String,
        val mediaType: MediaFilter,
        val dateFacet: DateFacet?,
    ) : SearchUiState

    data class NoResults(
        val text: String,
        val mediaType: MediaFilter,
        val dateFacet: DateFacet?,
    ) : SearchUiState
}

/**
 * Drives live search (spec §4, design 502-D). Observes the whole cached in-memory index once
 * ([MediaIndexRepository.observeMedia] with an unrestricted [MediaQuery]) and re-derives the result
 * list whenever the debounced query or the index changes — no rescan, no storage access, no network.
 *
 * Matching runs off the main thread on the injected [SearchDispatcher] (Default in production); the
 * query is debounced ~275ms (§4.6) so a fast typist doesn't re-filter on every keystroke. Executed
 * non-empty queries are recorded to [RecentSearchStore] (spec §4.4) — on a result tap and on the IME
 * search action, never per keystroke, so recents don't fill with typed prefixes.
 */
@OptIn(FlowPreview::class)
@HiltViewModel
class SearchViewModel(
    repository: MediaIndexRepository,
    private val recentStore: RecentSearchStore,
    dispatcher: CoroutineDispatcher,
    private val clock: () -> Long,
) : ViewModel() {

    @Inject
    constructor(
        repository: MediaIndexRepository,
        recentStore: RecentSearchStore,
        @SearchDispatcher dispatcher: CoroutineDispatcher,
    ) : this(repository, recentStore, dispatcher, { System.currentTimeMillis() })

    private val textState = MutableStateFlow("")
    private val mediaTypeState = MutableStateFlow(MediaFilter.ALL)
    private val dateFacetState = MutableStateFlow<DateFacet?>(null)
    private val columnsState = MutableStateFlow(ColumnCount.DEFAULT)

    /** The typed query text (search bar). */
    val text: StateFlow<String> = textState.asStateFlow()

    /** The selected media-type facet (top chip row); [MediaFilter.ALL] by default. */
    val mediaType: StateFlow<MediaFilter> = mediaTypeState.asStateFlow()

    /** The selected date facet (second chip row); null (no range) by default. */
    val dateFacet: StateFlow<DateFacet?> = dateFacetState.asStateFlow()

    /** In-session grid density for the results grid (pinch-zoom); ephemeral, not persisted. */
    val columns: StateFlow<ColumnCount> = columnsState.asStateFlow()

    /** The debounced facet snapshot the results stream reacts to. */
    private val activeQuery =
        combine(textState, mediaTypeState, dateFacetState) { text, type, facet ->
            ActiveQuery(text, type, facet)
        }.debounce(DEBOUNCE_MS)

    val state: StateFlow<SearchUiState> =
        combine(
            repository.observeMedia(MediaQuery()),
            activeQuery,
            recentStore.recents,
        ) { items, active, recents ->
            val query = SearchQuery(
                text = active.text,
                mediaType = active.mediaType,
                dateRange = active.dateFacet?.range(clock()),
            )
            if (query.isEmpty) {
                SearchUiState.Empty(recents)
            } else {
                val matches = items.matching(query)
                if (matches.isEmpty()) {
                    SearchUiState.NoResults(active.text, active.mediaType, active.dateFacet)
                } else {
                    SearchUiState.Results(matches, active.text, active.mediaType, active.dateFacet)
                }
            }
        }
            .flowOn(dispatcher)
            .stateIn(
                viewModelScope,
                SharingStarted.WhileSubscribed(5_000),
                SearchUiState.Empty(emptyList()),
            )

    fun setText(value: String) {
        textState.value = value
    }

    /** Clear the query text (the ✕ in the search bar) — returns to the empty/recent state. */
    fun clearText() {
        textState.value = ""
    }

    fun setMediaType(filter: MediaFilter) {
        mediaTypeState.value = filter
    }

    /** Toggle a Date chip: selecting a new facet sets it, tapping the selected one clears it (§4). */
    fun toggleDate(facet: DateFacet) {
        dateFacetState.update { if (it == facet) null else facet }
    }

    fun setColumns(columns: ColumnCount) {
        columnsState.value = columns
    }

    /** Re-run a saved search: restore its text + both facets so the results stream re-derives (§4.4). */
    fun reRunRecent(recent: RecentSearch) {
        textState.value = recent.text
        mediaTypeState.value = recent.mediaType
        dateFacetState.value = DateFacet.fromToken(recent.dateFacet)
    }

    /** Remove a single recent (the per-chip ✕, design §5). */
    fun removeRecent(recent: RecentSearch) {
        viewModelScope.launch { recentStore.remove(recent) }
    }

    /** Wipe all recents (the header Clear-all, design §5 / AC-7). */
    fun clearRecents() {
        viewModelScope.launch { recentStore.clear() }
    }

    /**
     * Record the current query as executed (spec §4.4). Called when the user opens a result or fires
     * the IME search action — the moments a query is genuinely "run" — not on every keystroke. Blank
     * text is ignored by the store, so chip-only queries are never recorded.
     */
    fun recordCurrentQuery() {
        viewModelScope.launch {
            recentStore.record(
                RecentSearch(
                    text = textState.value,
                    mediaType = mediaTypeState.value,
                    dateFacet = dateFacetState.value?.token,
                ),
            )
        }
    }

    /** The debounced facet snapshot; [SearchQuery] is built from it against the current [clock]. */
    private data class ActiveQuery(
        val text: String,
        val mediaType: MediaFilter,
        val dateFacet: DateFacet?,
    )

    private companion object {
        /** Debounce window before matching re-runs (spec §4.6: ~250–300ms). */
        const val DEBOUNCE_MS = 275L
    }
}
