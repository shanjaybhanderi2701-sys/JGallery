package com.appblish.jgallery.core.ui.grid

import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import coil3.SingletonImageLoader
import coil3.compose.LocalPlatformContext
import coil3.request.Disposable
import coil3.request.ImageRequest
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

/**
 * The ONE shared windowed-grid thumbnail prefetch (APP-722 P2). Extracted from the Photos timeline's
 * cold-cache prefetch (APP-456 / APP-701 / APP-709) so **every** media grid — Photos, Albums,
 * inside-album, Search, Trash — gets the identical "phenomenal scroll + thumbnail loading", not just
 * Photos. This is the P2 anti-fork rule: the other grids call THIS, they do not copy the old
 * unbounded concept.
 *
 * It can never regress the APP-712 P0 write-back ceiling: this composable only *enqueues* Coil
 * requests, and every request resolves through the single shared `ImageLoader` → the one gated
 * `ThumbnailFetcher` → the bounded `WriteBackGate(cap=8)`. There is no write-back path here to fork,
 * so that cap stays the single write-back admission point for all grids no matter how far any of them
 * flings.
 *
 * Two phases driven off [gridState]:
 * - **During a scroll** — a bounded, direction-aware, nearest-first lookahead ([PrefetchPlanner.ahead])
 *   in the scroll direction. It stays modest ([PREFETCH_AHEAD_MAX]) so tiles actually entering view
 *   keep winning decode slots, and the prior batch is disposed on every new window, so decodes for
 *   tiles flung past are cancelled. Above a fast-fling velocity ([FlingDecodeGate]) the lookahead is
 *   suspended and its in-flight batch cancelled outright, so a hard fling never floods the small,
 *   no-priority decode pool with tiles that fly past before they finish (APP-701).
 * - **Once the scroll settles** — a wider symmetric warm ([PrefetchPlanner.idleWarm]) in both
 *   directions, plus a coarse low-res ring ([PrefetchPlanner.coarseRing], APP-709) beyond it when
 *   [coarseEdgePx] > 0. Aggressive is safe when idle because no visible tile competes for slots; both
 *   warms are disposed the instant scrolling resumes, so they never steal a decode from the next
 *   fling. This is also the fling-settle warm-restart (APP-709): it fires the instant
 *   `isScrollInProgress` goes false, so tiles refill immediately rather than a batch late.
 *
 * Renders nothing — enqueued requests only populate Coil's caches; they need no draw target. The disk
 * cache persists across launches (Coil `DiskCache`, 256 MB LRU), so this cold pass is paid once per
 * library, not once per launch.
 *
 * @param gridState the grid's [LazyGridState]; the prefetch reads its visible window and scroll state.
 * @param itemCount the live grid-adapter cell count (headers included is fine — [modelAt] returns
 *   null for a non-tile index, which is skipped). Read live via [rememberUpdatedState] so a growing
 *   index (paged/windowed load) widens the window without restarting the effect.
 * @param modelAt resolves a grid-adapter index to the Coil model for its tile (a `ThumbnailRequest`),
 *   or null when the index is a header / not resolvable yet (e.g. a windowed tile whose page is not
 *   loaded). Kept opaque (`Any?`) so this module never depends on `:core:thumbs` (§1.6). Read live so
 *   a fresh backing list (a new window, a filter change) is always seen.
 * @param coarseEdgePx the cheap low-res edge for the APP-709 coarse warm ring, or `0` to disable the
 *   coarse ring (grids that do not seed a progressive placeholder, e.g. album covers). The crisp warm
 *   always runs; only the outer coarse ring is gated on this. Callers pass
 *   `ThumbnailSizes.coarsePreviewEdgePx` from `:core:thumbs`.
 */
@Composable
fun GridThumbnailPrefetch(
    gridState: LazyGridState,
    itemCount: Int,
    modelAt: (index: Int) -> Any?,
    coarseEdgePx: Int = 0,
) {
    val platformContext = LocalPlatformContext.current
    val imageLoader = remember(platformContext) { SingletonImageLoader.get(platformContext) }
    val currentItemCount by rememberUpdatedState(itemCount)
    val currentModelAt by rememberUpdatedState(modelAt)
    val currentCoarseEdgePx by rememberUpdatedState(coarseEdgePx)

    LaunchedEffect(gridState, imageLoader) {
        fun enqueueTiles(indices: List<Int>, edgePx: Int): List<Disposable> {
            val resolve = currentModelAt
            return indices.mapNotNull { index ->
                val model = resolve(index) ?: return@mapNotNull null
                val request = ImageRequest.Builder(platformContext)
                    .data(model)
                    .size(edgePx, edgePx)
                    .build()
                imageLoader.enqueue(request)
            }
        }

        coroutineScope {
            launch {
                var lastFirstVisible = gridState.firstVisibleItemIndex
                var lastTimeNanos = System.nanoTime()
                var velocity = 0f
                var prefetching = true
                var inFlight: List<Disposable> = emptyList()
                snapshotFlow {
                    val visible = gridState.layoutInfo.visibleItemsInfo
                    PrefetchWindow(
                        firstVisible = visible.firstOrNull()?.index ?: 0,
                        lastVisible = visible.lastOrNull()?.index ?: -1,
                        visibleCount = visible.size,
                        tilePx = visible.maxOfOrNull { minOf(it.size.width, it.size.height) } ?: 0,
                    )
                }
                    .distinctUntilChanged()
                    .collect { window ->
                        if (window.lastVisible < 0 || window.tilePx <= 0) return@collect
                        val now = System.nanoTime()
                        velocity = FlingDecodeGate.smoothVelocity(
                            prevVelocity = velocity,
                            indexDelta = window.firstVisible - lastFirstVisible,
                            elapsedNanos = now - lastTimeNanos,
                        )
                        lastTimeNanos = now
                        val goingDown = window.firstVisible >= lastFirstVisible
                        lastFirstVisible = window.firstVisible

                        // Cancel the prior batch; those tiles are now on-screen or flung past. Done
                        // unconditionally so a fast fling also cancels any lookahead dispatched just
                        // before it crossed the threshold (APP-701).
                        inFlight.forEach { it.dispose() }
                        inFlight = emptyList()

                        // Fast fling — suspend the lookahead entirely so the bounded, no-priority
                        // decode pool stays reserved for the tiles actually landing; the idle warm
                        // refills the caches the instant the fling settles (APP-701, finding #5).
                        prefetching = FlingDecodeGate.shouldPrefetchAhead(velocity, prefetching)
                        if (!prefetching) return@collect

                        // ~1.5 viewports ahead, bounded — aggressive enough to stay ahead of a steady
                        // scroll without saturating the decode pool the visible tiles need.
                        val aheadCount = (window.visibleCount * 3 / 2)
                            .coerceIn(1, PREFETCH_AHEAD_MAX)
                        val indices = PrefetchPlanner.ahead(
                            firstVisible = window.firstVisible,
                            lastVisible = window.lastVisible,
                            goingDown = goingDown,
                            aheadCount = aheadCount,
                            itemCount = currentItemCount,
                        )
                        inFlight = enqueueTiles(indices, window.tilePx)
                    }
            }

            launch {
                var warming: List<Disposable> = emptyList()
                snapshotFlow { gridState.isScrollInProgress }
                    .distinctUntilChanged()
                    .collect { scrolling ->
                        warming.forEach { it.dispose() }
                        warming = emptyList()
                        if (scrolling) return@collect
                        val visible = gridState.layoutInfo.visibleItemsInfo
                        val first = visible.firstOrNull()?.index ?: return@collect
                        val last = visible.lastOrNull()?.index ?: return@collect
                        val tilePx = visible.maxOfOrNull { minOf(it.size.width, it.size.height) } ?: 0
                        if (tilePx <= 0) return@collect
                        val itemCount = currentItemCount
                        // Crisp warm — the near viewport, at the real display size (unchanged).
                        val radius = (visible.size * 2).coerceIn(1, IDLE_WARM_MAX)
                        val crisp = PrefetchPlanner.idleWarm(
                            firstVisible = first,
                            lastVisible = last,
                            radius = radius,
                            itemCount = itemCount,
                        )
                        warming = enqueueTiles(crisp, tilePx)
                        // Coarse warm (APP-709) — a wider band BEYOND the crisp ring at the cheap
                        // low-res edge, so a subsequent fling settling into it paints an instant
                        // low-res tile and upgrades to crisp on show. Only when the caller opts in with
                        // a coarse edge (grids that seed a progressive placeholder). Cheap decodes,
                        // disjoint from the crisp band, and — like the crisp warm — disposed the instant
                        // scrolling resumes, so neither steals a decode slot from the next fling.
                        val coarseEdge = currentCoarseEdgePx
                        if (coarseEdge > 0) {
                            val outerRadius = (radius + visible.size * COARSE_RING_VIEWPORTS)
                                .coerceAtMost(COARSE_RING_MAX)
                            val coarse = PrefetchPlanner.coarseRing(
                                firstVisible = first,
                                lastVisible = last,
                                innerRadius = radius,
                                outerRadius = outerRadius,
                                itemCount = itemCount,
                            )
                            warming = warming + enqueueTiles(coarse, coarseEdge)
                        }
                    }
            }
        }
    }
}

private data class PrefetchWindow(
    val firstVisible: Int,
    val lastVisible: Int,
    val visibleCount: Int,
    val tilePx: Int,
)

private const val PREFETCH_AHEAD_MAX = 60
private const val IDLE_WARM_MAX = 80

// APP-709 coarse low-res warm ring, measured in *viewports* of extra reach past the crisp warm ring
// and hard-capped so the cheap-but-not-free coarse decodes stay bounded on a huge library.
private const val COARSE_RING_VIEWPORTS = 3
private const val COARSE_RING_MAX = 240
