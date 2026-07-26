package com.appblish.jgallery.core.ui.nav

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.appblish.jgallery.core.ui.theme.JGalleryColors
import com.appblish.jgallery.core.ui.theme.JGalleryDimens

/**
 * Leading **navigation rail** for Medium & Expanded windows (adaptive, APP-652 / spec §4.1). On
 * tablets and unfolded foldables the shell (`JGalleryApp`) swaps the bottom [GalleryTabBar] for this
 * rail — "the single most important 'not a stretched phone' signal." It is the *same* destinations
 * (Photos · Albums) and the *same* [GalleryTabBarItem] data as the bottom bar; only the container
 * orientation changes (spec §8: reuse, don't fork).
 *
 * Visual language is kept identical to the OnePlus-modeled bottom bar rather than adopting Material's
 * pill-indicator `NavigationRail`, so the app reads as one system across form factors: full-bleed
 * [JGalleryColors.Background] surface, no elevation, 25dp glyph, 13sp/600 label, active tab = accent
 * icon **and** accent label **and** a 4dp accent dot under the label (reserved space when inactive so
 * labels don't jump). No ripple/pill highlight.
 *
 * **Insets (coordinate with APP-643 UI-chrome audit):** the app is edge-to-edge, so the rail owns the
 * **start + vertical** safe insets (system bars ∪ display cutout) itself — the `Surface` stays
 * full-bleed to the physical leading edge (its background paints under the cutout strip) and only the
 * item [Column] is lifted clear via `windowInsetsPadding`. The shell in turn consumes the start inset
 * for the content pane so it is never applied twice.
 *
 * Stateless and route-keyed (mirrors [GalleryTabBar]): the caller owns [selectedRoute] and reacts to
 * [onSelect]. The container is tagged `gallery_nav_rail`, each item `tab_<route>` (and marked
 * `selected` in semantics) so shell/UI tests can assert rail routing and active state without DI.
 */
@Composable
fun GalleryNavRail(
    items: List<GalleryTabBarItem>,
    selectedRoute: String?,
    onSelect: (GalleryTabBarItem) -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier
            .fillMaxHeight()
            .testTag("gallery_nav_rail"),
        color = JGalleryColors.Background,
        tonalElevation = 0.dp,
    ) {
        Column(
            modifier = Modifier
                .fillMaxHeight()
                // Full-bleed surface, inset content: lift the items clear of the status bar, the
                // system nav bar, and the start display cutout (leaves the background bleeding to the
                // physical edge). Start + Vertical only — the content pane owns the end inset.
                .windowInsetsPadding(
                    WindowInsets.systemBars.union(WindowInsets.displayCutout)
                        .only(WindowInsetsSides.Start + WindowInsetsSides.Vertical),
                )
                .width(JGalleryDimens.NavRailWidth)
                .padding(vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            items.forEach { item ->
                GalleryNavRailItem(
                    item = item,
                    selected = item.route == selectedRoute,
                    onClick = { onSelect(item) },
                )
            }
        }
    }
}

@Composable
private fun GalleryNavRailItem(
    item: GalleryTabBarItem,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val contentColor = if (selected) JGalleryColors.Accent else JGalleryColors.TextSecondary
    // No ripple/pill highlight — the active tab is marked by the accent icon+label + dot alone.
    val interactionSource = remember { MutableInteractionSource() }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(CircleShape)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick,
            )
            .semantics { this.selected = selected }
            .testTag("tab_${item.route}")
            .padding(vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            imageVector = if (selected) item.selectedIcon else item.unselectedIcon,
            contentDescription = item.label,
            tint = contentColor,
            modifier = Modifier.size(JGalleryDimens.TabBarIconSize),
        )
        Spacer(modifier = Modifier.height(3.dp))
        Text(
            text = item.label,
            color = contentColor,
            fontSize = 13.sp,
            fontWeight = FontWeight.W600,
        )
        Spacer(modifier = Modifier.height(3.dp))
        // 4dp accent dot: present only for the active tab (reserved space otherwise, so labels don't
        // jump when selection moves) — identical to the bottom bar's active marker.
        Box(
            modifier = Modifier
                .size(JGalleryDimens.TabBarActiveDotSize)
                .clip(CircleShape)
                .background(if (selected) JGalleryColors.Accent else Color.Transparent)
                .testTag(if (selected) "tab_active_dot" else "tab_inactive_dot"),
        )
    }
}
