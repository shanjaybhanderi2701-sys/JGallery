package com.appblish.jgallery.feature.search

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Place
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.TextFields
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.appblish.jgallery.core.ui.component.PlaceholderHero
import com.appblish.jgallery.core.ui.component.PlaceholderScreenScaffold
import com.appblish.jgallery.core.ui.component.PreviewChip
import com.appblish.jgallery.core.ui.component.PreviewSearchBar
import com.appblish.jgallery.core.ui.component.PreviewSectionHeader

/**
 * Search — an **intentional preview** of the Phase G3 layout (spec §0, §12; design §4). The
 * search bar is visible per spec §0 but inert this phase; the Time / Places / text-in-photo rows
 * preview the filters G3 will make live. Everything is desaturated, non-tappable, "Soon"-badged —
 * a deliberate scaffold, not a broken screen, and no dead-end taps.
 *
 * Since C1-01 item 10, Search is no longer a tab — it opens full-screen from the Photos/Collections
 * header. [onBack] (non-null on that route) shows the back affordance; left null it renders bare.
 */
@Composable
fun SearchScreen(
    modifier: Modifier = Modifier,
    onBack: (() -> Unit)? = null,
) {
    PlaceholderScreenScaffold(
        title = "Search",
        modifier = modifier.testTag("search_screen"),
        navigation = onBack?.let {
            {
                IconButton(onClick = it, modifier = Modifier.testTag("search_back")) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                        contentDescription = "Back",
                    )
                }
            }
        },
    ) {
        PreviewSearchBar(
            hint = "Search text, location, time",
            icon = Icons.Outlined.Search,
        )

        PlaceholderHero(
            icon = Icons.Outlined.Search,
            headline = "Smart Search",
            body = "Search by name and date first, then by place and even text inside your photos — " +
                "processed on your device, never uploaded.",
        )

        PreviewSectionHeader("By time")
        ChipRow("Today", "This week", "This month", "2024", "2023", icon = Icons.Outlined.Schedule)

        PreviewSectionHeader("By place")
        ChipRow("Nearby", "Cities", "Countries", "On a map", icon = Icons.Outlined.Place)

        PreviewSectionHeader("Text in photos")
        ChipRow("Receipts", "Notes", "Screenshots", "Signs", icon = Icons.Outlined.TextFields)
    }
}

@Composable
private fun ChipRow(vararg labels: String, icon: androidx.compose.ui.graphics.vector.ImageVector) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        labels.forEach { label -> PreviewChip(label, icon = icon) }
    }
}
