package com.appblish.jgallery.core.ui.nav

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.background
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.appblish.jgallery.core.ui.theme.JGalleryColors
import com.appblish.jgallery.core.ui.theme.JGalleryDimens

/**
 * One entry in the [GalleryTabBar]: a route id, a label, and the filled/outlined glyph pair. Kept
 * data-only and route-keyed so the shell (`JGalleryApp`) can supply the reduced Photos · Collections
 * set once the 2-tab nav refactor (C6 item 10) lands, without this widget depending on any enum.
 */
data class GalleryTabBarItem(
    val route: String,
    val label: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
)

/**
 * OnePlus-modeled bottom tab bar (design C1-01, item 10). Collapses the old 4-item Material
 * `NavigationBar` into a flat 2-tab bar:
 *  - 78dp tall, [JGalleryColors.Background] container, no elevation.
 *  - 25dp glyph, 13sp/600 label.
 *  - active tab = accent icon **and** accent label **and** a 4dp accent dot under the label
 *    (this dot replaces the old filled accent pill/indicator).
 *  - no center FAB, no badges.
 *
 * Stateless and route-keyed: the caller owns [selectedRoute] and reacts to [onSelect]. Each tab is
 * tagged `tab_<route>` (and marked `selected` in semantics) so shell/UI tests can assert routing and
 * active state without DI.
 */
@Composable
fun GalleryTabBar(
    items: List<GalleryTabBarItem>,
    selectedRoute: String?,
    onSelect: (GalleryTabBarItem) -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.testTag("gallery_tab_bar"),
        color = JGalleryColors.Background,
        tonalElevation = 0.dp,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(JGalleryDimens.TabBarHeight),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            items.forEach { item ->
                GalleryTabBarButton(
                    item = item,
                    selected = item.route == selectedRoute,
                    onClick = { onSelect(item) },
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun GalleryTabBarButton(
    item: GalleryTabBarItem,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val contentColor = if (selected) JGalleryColors.Accent else JGalleryColors.TextSecondary
    // No ripple/pill highlight — OnePlus model marks the active tab with the accent dot alone.
    val interactionSource = remember { MutableInteractionSource() }
    Column(
        modifier = modifier
            .clip(CircleShape)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick,
            )
            .semantics { this.selected = selected }
            .testTag("tab_${item.route}")
            .padding(vertical = 6.dp),
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
        // jump when selection moves).
        Box(
            modifier = Modifier
                .size(JGalleryDimens.TabBarActiveDotSize)
                .clip(CircleShape)
                .background(if (selected) JGalleryColors.Accent else Color.Transparent)
                .testTag(if (selected) "tab_active_dot" else "tab_inactive_dot"),
        )
    }
}
