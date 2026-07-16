package com.appblish.jgallery.core.ui.component

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.appblish.jgallery.core.ui.theme.JGalleryColors

/**
 * One canonical gallery title bar (design G1-D7 §1): a title on the left, then **Search + 3-dot
 * overflow** and nothing else. Both the Photos and Albums tabs host this so the top bar reads
 * identically across them (JD round-3 flagged the Photos tab still carrying loose grid/group icons
 * and no overflow). The overflow content is supplied by the caller as a list of [GalleryMenuItem]s
 * so each tab keeps its own action set while sharing the shell + styled menu surface.
 */
@Composable
fun GalleryTopBar(
    title: String,
    onSearch: () -> Unit,
    overflowItems: List<GalleryMenuItem>,
    modifier: Modifier = Modifier,
    searchTestTag: String = "search_action",
    overflowTestTag: String = "overflow_action",
) {
    GalleryTabHeader(title = title, modifier = modifier) {
        IconButton(onClick = onSearch, modifier = Modifier.testTag(searchTestTag)) {
            Icon(
                imageVector = Icons.Outlined.Search,
                contentDescription = "Search",
                tint = JGalleryColors.Text,
            )
        }
        GalleryOverflowMenu(items = overflowItems, testTag = overflowTestTag)
    }
}

/**
 * One entry in a [GalleryOverflowMenu]. [dividerBefore] draws the destructive-adjacent 1dp divider
 * above the row (design §4: before "Recycle bin"). [enabled] dims + disables single-only actions.
 */
data class GalleryMenuItem(
    val label: String,
    val icon: ImageVector,
    val testTag: String,
    val onClick: () -> Unit,
    val dividerBefore: Boolean = false,
    val enabled: Boolean = true,
)

/**
 * The ⋮ overflow button + its **styled** dropdown menu (design G1-D7 §4). The raw M3 `DropdownMenu`
 * default (`colorScheme.surface`, no shape/elevation) read as unstyled chrome — especially in dark,
 * where it collapsed toward the grid tone. This applies the redlined menu-surface tokens on every
 * tab: 16dp radius, a designed surface a step above the grid, hairline border, soft shadow, 48dp
 * rows with 22dp leading icons.
 *
 * This is the single source of truth for the 3-dot menu across the home/Photos tab, the Albums tab
 * and every album's detail screen, so the menu can't drift between surfaces (APP-499). Per-surface
 * extras that don't fit the flat [GalleryMenuItem] list (e.g. an album's "apply to this album only /
 * all albums" scope toggle) render inside this same styled surface via the optional [footer] slot —
 * so even the extras share the menu's background, shape and spacing. [onDismiss] fires whenever the
 * menu closes, letting a [footer] row dismiss it after acting.
 */
@Composable
fun GalleryOverflowMenu(
    items: List<GalleryMenuItem>,
    modifier: Modifier = Modifier,
    testTag: String = "overflow_action",
    contentDescription: String = "More options",
    footer: (@Composable ColumnScope.(onDismiss: () -> Unit) -> Unit)? = null,
) {
    var expanded by remember { mutableStateOf(false) }
    val dark = isSystemInDarkTheme()
    val surface = if (dark) MenuSurfaceDark else MenuSurfaceLight
    val hairline = if (dark) MenuHairlineDark else MenuHairlineLight
    val labelColor = if (dark) MenuLabelDark else MenuLabelLight
    val iconColor = if (dark) MenuIconDark else MenuIconLight
    val dividerColor = if (dark) MenuHairlineDark else MenuDividerLight

    androidx.compose.foundation.layout.Box(modifier) {
        IconButton(
            onClick = { expanded = true },
            modifier = Modifier.testTag(testTag),
        ) {
            Icon(
                imageVector = Icons.Outlined.MoreVert,
                contentDescription = contentDescription,
                tint = JGalleryColors.Text,
            )
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            offset = androidx.compose.ui.unit.DpOffset(0.dp, 4.dp),
            shape = RoundedCornerShape(16.dp),
            containerColor = surface,
            tonalElevation = 0.dp,
            shadowElevation = 8.dp,
            border = BorderStroke(1.dp, hairline),
        ) {
            items.forEach { item ->
                if (item.dividerBefore) {
                    HorizontalDivider(thickness = 1.dp, color = dividerColor)
                }
                DropdownMenuItem(
                    text = { Text(item.label) },
                    enabled = item.enabled,
                    onClick = { expanded = false; item.onClick() },
                    leadingIcon = {
                        Icon(
                            imageVector = item.icon,
                            contentDescription = null,
                            modifier = Modifier.size(22.dp),
                        )
                    },
                    colors = MenuDefaults.itemColors(
                        textColor = labelColor,
                        leadingIconColor = iconColor,
                    ),
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    modifier = Modifier.testTag(item.testTag),
                )
            }
            footer?.invoke(this) { expanded = false }
        }
    }
}

// Designed menu-surface tokens (design G1-D7 §4). Light is the shipping theme today; dark values are
// carried so the menu is already correct when a dark theme lands (surface sits one step above the grid).
private val MenuSurfaceLight = Color(0xFFFFFFFF)
private val MenuSurfaceDark = Color(0xFF24262E)
private val MenuHairlineLight = Color(0xFFE7E9EF)
private val MenuHairlineDark = Color(0xFF33363F)
private val MenuLabelLight = Color(0xFF1B1C22)
private val MenuLabelDark = Color(0xFFECEEF3)
private val MenuIconLight = Color(0xFF5A6272)
private val MenuIconDark = Color(0xFFAEB4C0)
private val MenuDividerLight = Color(0xFFEEF0F4)
