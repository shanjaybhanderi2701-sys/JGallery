package com.appblish.jgallery.feature.search

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.SearchOff
import androidx.compose.material.icons.outlined.TravelExplore
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.appblish.jgallery.core.model.ColumnCount
import com.appblish.jgallery.core.model.MediaFilter
import com.appblish.jgallery.core.model.MediaItem
import com.appblish.jgallery.core.model.MediaType
import com.appblish.jgallery.core.model.formatBadge
import com.appblish.jgallery.core.model.isPanorama
import com.appblish.jgallery.core.thumbs.thumbnailRequest
import com.appblish.jgallery.core.ui.component.FormatBadgeChip
import com.appblish.jgallery.core.ui.component.FormatFilterChips
import com.appblish.jgallery.core.ui.component.PreviewChip
import com.appblish.jgallery.core.ui.component.SoonBadge
import com.appblish.jgallery.core.ui.component.VideoOverlay
import com.appblish.jgallery.core.ui.format.MediaDecodeBox
import com.appblish.jgallery.core.ui.format.MediaDecodeTilePlaceholder
import com.appblish.jgallery.core.ui.grid.GridFastScroller
import com.appblish.jgallery.core.ui.grid.ScrollToTopFab
import com.appblish.jgallery.core.ui.grid.gridPinchColumns
import com.appblish.jgallery.core.ui.theme.JGalleryColors
import com.appblish.jgallery.core.ui.theme.JGalleryDimens

/**
 * Live Search (spec §4, design 502-D). Replaces the inert G3 "Soon" preview with a working search:
 * a focused search bar, two live filter rows (media type + date), and results in the shared media
 * grid. Everything runs on-device over the cached in-memory index — no rescan, no network (spec §8).
 *
 * States (§4.5) come straight from [SearchViewModel]: [SearchUiState.Empty] (recents / hero),
 * [SearchUiState.Results] (grid + count line), [SearchUiState.NoResults] (friendly, editable, never a
 * dead end). Tapping a result records the query to recents and opens the existing viewer via
 * [onMediaClick]; [onBack] returns to the originating surface.
 */
@Composable
fun SearchScreen(
    onBack: () -> Unit,
    onMediaClick: (MediaItem) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SearchViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val text by viewModel.text.collectAsStateWithLifecycle()
    val mediaType by viewModel.mediaType.collectAsStateWithLifecycle()
    val dateFacet by viewModel.dateFacet.collectAsStateWithLifecycle()
    val columns by viewModel.columns.collectAsStateWithLifecycle()

    SearchScreen(
        state = state,
        text = text,
        mediaType = mediaType,
        dateFacet = dateFacet,
        columns = columns,
        onBack = onBack,
        onTextChange = viewModel::setText,
        onClearText = viewModel::clearText,
        onMediaTypeChange = viewModel::setMediaType,
        onDateToggle = viewModel::toggleDate,
        onColumnsChange = viewModel::setColumns,
        onCommitQuery = viewModel::recordCurrentQuery,
        onMediaClick = onMediaClick,
        onReRunRecent = viewModel::reRunRecent,
        onRemoveRecent = viewModel::removeRecent,
        onClearRecents = viewModel::clearRecents,
        modifier = modifier,
    )
}

/** Stateless body — instrumented/preview tests drive this without Hilt. */
@Composable
fun SearchScreen(
    state: SearchUiState,
    text: String,
    mediaType: MediaFilter,
    dateFacet: DateFacet?,
    columns: ColumnCount,
    onBack: () -> Unit,
    onTextChange: (String) -> Unit,
    onClearText: () -> Unit,
    onMediaTypeChange: (MediaFilter) -> Unit,
    onDateToggle: (DateFacet) -> Unit,
    onColumnsChange: (ColumnCount) -> Unit,
    onCommitQuery: () -> Unit,
    onMediaClick: (MediaItem) -> Unit,
    onReRunRecent: (RecentSearch) -> Unit,
    onRemoveRecent: (RecentSearch) -> Unit,
    onClearRecents: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxSize().testTag("search_screen")) {
        SearchBar(
            text = text,
            onTextChange = onTextChange,
            onBack = onBack,
            onClear = onClearText,
            onSubmit = onCommitQuery,
        )

        // Two live filter rows (§4.3) — always visible so both facets are discoverable, and pinned
        // above the results so toggling re-runs in place. Reuse the exact core:ui chip geometry.
        SectionLabel("Media type")
        FormatFilterChips(selected = mediaType, onSelect = onMediaTypeChange)
        SectionLabel("Date")
        DateFilterChips(selected = dateFacet, onSelect = onDateToggle)

        Box(Modifier.fillMaxSize()) {
            when (state) {
                is SearchUiState.Empty -> EmptyContent(
                    recents = state.recents,
                    onReRunRecent = onReRunRecent,
                    onRemoveRecent = onRemoveRecent,
                    onClearRecents = onClearRecents,
                )

                is SearchUiState.NoResults -> NoResultsContent(text = state.text)

                is SearchUiState.Results -> ResultsContent(
                    results = state.items,
                    mediaType = state.mediaType,
                    dateFacet = state.dateFacet,
                    columns = columns,
                    onColumnsChange = onColumnsChange,
                    onMediaClick = { item ->
                        // Opening a result is the "executed" signal (spec §4.4): record, then view.
                        onCommitQuery()
                        onMediaClick(item)
                    },
                )
            }
        }
    }
}

/**
 * Focused-on-open search bar (design §2): leading back arrow, live text, trailing ✕ that appears only
 * when text is present, an Accent 2dp border while focused, and an IME **Search** action that records
 * the query. A [BasicTextField] keeps full control of the styling and cursor colour.
 */
@Composable
private fun SearchBar(
    text: String,
    onTextChange: (String) -> Unit,
    onBack: () -> Unit,
    onClear: () -> Unit,
    onSubmit: () -> Unit,
) {
    val focusRequester = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current
    var focused by remember { mutableStateOf(false) }

    // Auto-focus + raise the keyboard the moment Search opens (design §3.1).
    LaunchedEffect(Unit) { focusRequester.requestFocus() }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp)
            .height(52.dp)
            .clip(RoundedCornerShape(26.dp))
            .background(JGalleryColors.Surface)
            .border(
                width = if (focused) 2.dp else 0.dp,
                color = if (focused) JGalleryColors.Accent else Color.Transparent,
                shape = RoundedCornerShape(26.dp),
            )
            .padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onBack, modifier = Modifier.testTag("search_back")) {
            Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back", tint = JGalleryColors.Text)
        }
        BasicTextField(
            value = text,
            onValueChange = onTextChange,
            modifier = Modifier
                .weight(1f)
                .focusRequester(focusRequester)
                .onFocusChanged { focused = it.isFocused }
                .testTag("search_input"),
            singleLine = true,
            textStyle = TextStyle(color = JGalleryColors.Text, fontSize = 16.sp),
            cursorBrush = SolidColor(JGalleryColors.Accent),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = {
                onSubmit()
                focusManager.clearFocus()
            }),
            decorationBox = { inner ->
                Box(contentAlignment = Alignment.CenterStart) {
                    if (text.isEmpty()) {
                        Text(
                            text = "Search photos & videos",
                            color = JGalleryColors.TextSecondary,
                            fontSize = 16.sp,
                        )
                    }
                    inner()
                }
            },
        )
        if (text.isNotEmpty()) {
            IconButton(onClick = onClear, modifier = Modifier.testTag("search_clear")) {
                Icon(Icons.Outlined.Close, contentDescription = "Clear search", tint = JGalleryColors.Text)
            }
        }
    }
}

/** A small uppercase section label (design §2): 13sp/700, TextSecondary. */
@Composable
private fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text.uppercase(),
        color = JGalleryColors.TextSecondary,
        fontSize = 13.sp,
        fontWeight = FontWeight.Bold,
        modifier = modifier.padding(start = 20.dp, top = 14.dp, bottom = 4.dp),
    )
}

/**
 * The Date filter row (design §4): [DateFacet] chips rendered with the exact [FormatFilterChips]
 * geometry (38dp / 20dp radius, Accent selected, grey rest) — no new chip primitive, single-select,
 * all label-only. Tapping the selected chip clears it (handled by the ViewModel).
 */
@Composable
private fun DateFilterChips(
    selected: DateFacet?,
    onSelect: (DateFacet) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .testTag("date_filter_chips"),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        DateFacet.ORDER.forEach { facet ->
            val isSelected = facet == selected
            Row(
                modifier = Modifier
                    .height(38.dp)
                    .defaultMinSize(minWidth = 48.dp)
                    .background(
                        if (isSelected) JGalleryColors.Accent else JGalleryColors.Surface,
                        RoundedCornerShape(20.dp),
                    )
                    .clickable { onSelect(facet) }
                    .padding(horizontal = 14.dp)
                    .semantics { this.selected = isSelected }
                    .testTag("date_chip_${facet.name}"),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = facet.token,
                    color = if (isSelected) JGalleryColors.OnAccent else JGalleryColors.Text,
                    fontSize = 14.sp,
                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium,
                    maxLines = 1,
                )
            }
        }
    }
}

/**
 * Empty / initial state (design §3.1): recents (if any) with a Clear-all header, else the on-device
 * discovery hero. The deferred "Places & text-in-photo" strip anchors the bottom so the G3 promise
 * stays visible (design §6). Scrolls so a long recents list and the strip never clip.
 */
@Composable
private fun EmptyContent(
    recents: List<RecentSearch>,
    onReRunRecent: (RecentSearch) -> Unit,
    onRemoveRecent: (RecentSearch) -> Unit,
    onClearRecents: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(bottom = 24.dp),
    ) {
        if (recents.isNotEmpty()) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(start = 20.dp, end = 12.dp, top = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "RECENT SEARCHES",
                    color = JGalleryColors.TextSecondary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = "Clear all",
                    color = JGalleryColors.Accent,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable(onClick = onClearRecents)
                        .padding(horizontal = 8.dp, vertical = 6.dp)
                        .testTag("recents_clear_all"),
                )
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 8.dp)
                    .testTag("recent_searches"),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                recents.forEach { recent ->
                    RecentSearchChip(
                        recent = recent,
                        onReRun = { onReRunRecent(recent) },
                        onRemove = { onRemoveRecent(recent) },
                    )
                }
            }
        } else {
            DiscoveryHero(
                icon = Icons.Outlined.TravelExplore,
                headline = "Search your library",
                body = "Find a photo or video by name, then narrow by type or date — all on your " +
                    "device, never uploaded.",
            )
        }

        DeferredStrip()
    }
}

/**
 * A recent-search action pill (design §5): 38dp Surface pill with a leading clock glyph (recency, not
 * time-of-day), the query text, and a trailing ✕ in a grey disc. It is an **action pill, not a
 * toggle** — never Accent-filled. Two distinct tap targets: the body re-runs the whole saved query;
 * the ✕ (its own labelled control) removes just this entry.
 */
@Composable
private fun RecentSearchChip(
    recent: RecentSearch,
    onReRun: () -> Unit,
    onRemove: () -> Unit,
) {
    Row(
        modifier = Modifier
            .height(38.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(JGalleryColors.Surface)
            .testTag("recent_chip_${recent.text}"),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            modifier = Modifier
                .clickable(onClick = onReRun)
                .padding(start = 12.dp, end = 6.dp)
                .height(38.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Icon(
                imageVector = Icons.Outlined.History,
                contentDescription = null,
                tint = JGalleryColors.TextSecondary,
                modifier = Modifier.size(16.dp),
            )
            Text(
                text = recent.text,
                color = JGalleryColors.Text,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
            )
        }
        Box(
            modifier = Modifier
                .padding(end = 8.dp)
                .size(22.dp)
                .clip(CircleShape)
                .background(JGalleryColors.SheetHandle)
                .clickable(onClick = onRemove)
                .semantics { contentDescription = "Remove ${recent.text}" }
                .testTag("recent_remove_${recent.text}"),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Outlined.Close,
                contentDescription = null,
                tint = JGalleryColors.TextSecondary,
                modifier = Modifier.size(14.dp),
            )
        }
    }
}

/**
 * No-results state (design §3.3): names the query verbatim under the discovery-family glyph and keeps
 * the query + chips editable above it. Never a dead end (AC-8) — copy nudges a typo/facet fix.
 */
@Composable
private fun NoResultsContent(text: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxSize().padding(32.dp).testTag("search_no_results"),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            imageVector = Icons.Outlined.SearchOff,
            contentDescription = null,
            tint = JGalleryColors.TextSecondary,
            modifier = Modifier.size(56.dp),
        )
        Text(
            text = "No matches for “$text”",
            style = MaterialTheme.typography.headlineSmall,
            color = JGalleryColors.Text,
            modifier = Modifier.padding(top = 16.dp),
        )
        Text(
            text = "Check the spelling, or loosen the type/date filters above.",
            style = MaterialTheme.typography.bodyLarge,
            color = JGalleryColors.TextSecondary,
            modifier = Modifier.padding(top = 8.dp),
        )
    }
}

/** The on-device discovery hero for the no-recents empty state (design §3.1). No "Soon" framing. */
@Composable
private fun DiscoveryHero(
    icon: ImageVector,
    headline: String,
    body: String,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth().padding(horizontal = 32.dp, vertical = 40.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = JGalleryColors.Accent,
            modifier = Modifier.size(48.dp),
        )
        Text(
            text = headline,
            style = MaterialTheme.typography.headlineSmall,
            color = JGalleryColors.Text,
            modifier = Modifier.padding(top = 16.dp),
        )
        Text(
            text = body,
            style = MaterialTheme.typography.bodyMedium,
            color = JGalleryColors.TextSecondary,
            modifier = Modifier.padding(top = 8.dp),
        )
    }
}

/**
 * The deferred "Places & text-in-photo" strip (design §6): the G3 promises stay visible as one
 * clearly-deferred `SOON · G3` block — desaturated, non-tappable chips. Defer, never delete.
 */
@Composable
private fun DeferredStrip(modifier: Modifier = Modifier) {
    Column(modifier = modifier.fillMaxWidth().padding(top = 8.dp)) {
        Row(
            modifier = Modifier.padding(start = 20.dp, top = 14.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = "Places & text-in-photo",
                color = JGalleryColors.TextSecondary,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
            )
            SoonBadge(text = "Soon · G3")
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            listOf("Nearby", "Cities", "On a map", "Receipts", "Text in photos").forEach {
                PreviewChip(it)
            }
        }
    }
}

/**
 * Typing / results (design §3.2): an optional count line that surfaces the active facets, then the
 * shared media grid. The grid reuses the same tile + fast-scroll + pinch primitives as Photos/Albums
 * (no forked grid, §7); it is flat (search results have no date sections).
 */
@Composable
private fun ResultsContent(
    results: List<MediaItem>,
    mediaType: MediaFilter,
    dateFacet: DateFacet?,
    columns: ColumnCount,
    onColumnsChange: (ColumnCount) -> Unit,
    onMediaClick: (MediaItem) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxSize()) {
        Text(
            text = countLine(results.size, mediaType, dateFacet),
            color = JGalleryColors.TextSecondary,
            fontSize = 13.5.sp,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 6.dp)
                .testTag("search_result_count"),
        )
        SearchResultsGrid(
            results = results,
            columns = columns,
            onColumnsChange = onColumnsChange,
            onMediaClick = onMediaClick,
        )
    }
}

/** "3 results · Videos · This month" — count plus any active facets, for legible narrowing (§3.2). */
private fun countLine(count: Int, mediaType: MediaFilter, dateFacet: DateFacet?): String {
    val parts = buildList {
        add(if (count == 1) "1 result" else "$count results")
        if (mediaType != MediaFilter.ALL) {
            add(mediaType.name.lowercase().replaceFirstChar { it.uppercase() })
        }
        if (dateFacet != null) add(dateFacet.token)
    }
    return parts.joinToString(" · ")
}

@Composable
private fun SearchResultsGrid(
    results: List<MediaItem>,
    columns: ColumnCount,
    onColumnsChange: (ColumnCount) -> Unit,
    onMediaClick: (MediaItem) -> Unit,
) {
    val gridState = rememberLazyGridState()
    val tileShape = JGalleryDimens.tileRadius(columns)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .gridPinchColumns(currentColumns = { columns }, onColumnsChange = onColumnsChange),
    ) {
        LazyVerticalGrid(
            columns = GridCells.Fixed(columns.value),
            state = gridState,
            horizontalArrangement = Arrangement.spacedBy(JGalleryDimens.PhotosGutter),
            verticalArrangement = Arrangement.spacedBy(JGalleryDimens.PhotosGutter),
            modifier = Modifier.fillMaxSize().testTag("search_results_grid"),
        ) {
            items(
                count = results.size,
                key = { index -> results[index].id.value },
                contentType = { "media_tile" },
            ) { index ->
                val item = results[index]
                SearchTile(
                    item = item,
                    shape = tileShape,
                    columns = columns.value,
                    onClick = { onMediaClick(item) },
                )
            }
        }

        GridFastScroller(gridState = gridState, itemCount = results.size)
        ScrollToTopFab(gridState = gridState)
    }
}

/**
 * One square result tile — mirrors the Photos tile (thumbnail-only model, pano letterboxing, video
 * overlay + format badge) minus the selection chrome. Search has no multi-select this phase (§4.5).
 */
@Composable
private fun SearchTile(
    item: MediaItem,
    shape: RoundedCornerShape,
    columns: Int,
    onClick: () -> Unit,
) {
    val isPano = item.isPanorama
    val badge = item.formatBadge
    Box(
        modifier = Modifier
            .aspectRatio(1f)
            .clip(shape)
            .background(if (isPano) Color.Black else JGalleryColors.TilePlaceholder)
            .clickable(onClick = onClick)
            .semantics { contentDescription = item.displayName },
    ) {
        MediaDecodeBox(
            model = item.thumbnailRequest(),
            displayName = item.displayName,
            mimeType = item.mimeType,
            sizeBytes = item.sizeBytes,
            contentDescription = null,
            contentScale = if (isPano) ContentScale.FillWidth else ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
            crossfade = false,
            loadingColor = if (isPano) Color.Black else JGalleryColors.TilePlaceholder,
            placeholder = { MediaDecodeTilePlaceholder(it) },
        )
        if (badge != null) {
            FormatBadgeChip(
                badge = badge,
                modifier = Modifier.align(Alignment.TopStart).padding(6.dp),
            )
        }
        if (item.type == MediaType.VIDEO) {
            VideoOverlay(durationMillis = item.durationMillis, columns = columns)
        }
    }
}
