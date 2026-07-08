package com.appblish.jgallery.core.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.appblish.jgallery.core.ui.theme.JGalleryColors

/**
 * Building blocks for the Collections (G4) and Search (G3) tabs, which ship in Phase G1 as
 * **intentional previews of their final layout** — not a generic "coming soon" wall (design
 * `w1-design-spec` §4). They mirror the geometry the live features will use so that when the ML
 * content lands there is zero reflow, and they are deliberately **non-interactive**: nothing here
 * is `clickable`, so there are no dead-end taps.
 *
 * Integrity: any hero copy passed to [PlaceholderHero] must be true today (on-device, no upload) —
 * never promise a capability that is not yet built (spec §9.3 integrity rule, applied to feature copy).
 */

/** Neutral tile fill for preview thumbnails — never blank white, never a shimmer (design §6). */
private val PreviewTileColor = Color(0xFFEDEFF3)

private val SectionTitleWeight = FontWeight.Bold

/**
 * Root of a placeholder tab: the 32sp tab title (design §1/§3) over a vertically scrollable body of
 * preview sections. The caller supplies the screen [Modifier] (and its `testTag`).
 */
@Composable
fun PlaceholderScreenScaffold(
    title: String,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp)
            .padding(top = 28.dp, bottom = 40.dp),
        verticalArrangement = Arrangement.spacedBy(28.dp),
    ) {
        Text(text = title, style = MaterialTheme.typography.displaySmall)
        content()
    }
}

/** Small accent-soft "Soon" pill that marks every previewed-but-not-yet-live surface. */
@Composable
fun SoonBadge(modifier: Modifier = Modifier, text: String = "Soon") {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(percent = 50))
            .background(JGalleryColors.AccentSoft)
            .padding(horizontal = 10.dp, vertical = 4.dp),
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
            color = JGalleryColors.Accent,
        )
    }
}

/**
 * The hero card at the top of a placeholder tab: an accent-soft panel that names what is coming and
 * states plainly that it runs on-device. [body] is the product-integrity commitment — keep it true.
 */
@Composable
fun PlaceholderHero(
    icon: ImageVector,
    headline: String,
    body: String,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(JGalleryColors.AccentSoft)
            .padding(20.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = JGalleryColors.Accent,
            modifier = Modifier.size(28.dp),
        )
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = headline,
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = JGalleryColors.Text,
                    modifier = Modifier.weight(1f, fill = false),
                )
                Spacer(Modifier.width(8.dp))
                SoonBadge()
            }
            Text(
                text = body,
                style = MaterialTheme.typography.bodySmall,
                color = JGalleryColors.TextSecondary,
            )
        }
    }
}

/** A preview-section label, optionally trailed by a [SoonBadge]. */
@Composable
fun PreviewSectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    showSoon: Boolean = true,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium.copy(fontSize = 18.sp, fontWeight = SectionTitleWeight),
            color = JGalleryColors.Text,
        )
        if (showSoon) SoonBadge()
    }
}

/**
 * A desaturated preview thumbnail with a centered glyph + label and a corner "Soon" badge —
 * the shape a real cover/category tile will take. Non-interactive by construction.
 */
@Composable
fun PreviewTile(
    icon: ImageVector,
    label: String,
    modifier: Modifier = Modifier,
    aspectRatio: Float = 1f,
    showSoon: Boolean = true,
) {
    Box(
        modifier = modifier
            .aspectRatio(aspectRatio)
            .clip(RoundedCornerShape(14.dp))
            .background(PreviewTileColor), // decorative; carries no click action
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = JGalleryColors.TextSecondary,
                modifier = Modifier.size(30.dp),
            )
            Spacer(Modifier.height(10.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.bodySmall,
                color = JGalleryColors.TextSecondary,
                textAlign = TextAlign.Center,
            )
        }
        if (showSoon) {
            SoonBadge(modifier = Modifier.align(Alignment.TopEnd).padding(8.dp))
        }
    }
}

/** A desaturated, non-tappable filter/utility pill (Places, Duplicates, "Today", …). */
@Composable
fun PreviewChip(
    label: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(percent = 50))
            .background(JGalleryColors.Surface)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = JGalleryColors.TextSecondary,
                modifier = Modifier.size(16.dp),
            )
        }
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = JGalleryColors.TextSecondary,
        )
    }
}

/** A static, non-focusable search field — visible on the Search tab per spec §0, but inert this phase. */
@Composable
fun PreviewSearchBar(
    hint: String,
    icon: ImageVector,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(52.dp)
            .clip(RoundedCornerShape(percent = 50))
            .background(JGalleryColors.Surface)
            .padding(horizontal = 18.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = JGalleryColors.TextSecondary,
            modifier = Modifier.size(22.dp),
        )
        Text(
            text = hint,
            style = MaterialTheme.typography.bodyLarge,
            color = JGalleryColors.TextSecondary,
        )
    }
}
