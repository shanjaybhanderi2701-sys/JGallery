package com.appblish.jgallery.core.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.appblish.jgallery.core.ui.theme.JGalleryColors
import com.appblish.jgallery.core.ui.theme.JGalleryDimens

/**
 * The canonical JGallery bottom-sheet scaffold (design **APP-453 / G1-D4**, board D4-01).
 *
 * Every sheet in the app shares this shell so consistency is *structural*, not per-call. It bakes in the
 * three things the D4 audit found each sheet was re-implementing (or getting wrong) by hand:
 *  1. **Surface** — container is always [JGalleryColors.Background] (`#FFFFFF`). The M3 default
 *     `surfaceContainerLow` (a lavender-tinted off-white foreign to our clean-white system) is banned;
 *     bare `ModalBottomSheet` calls that never passed `containerColor` were the D4 surface bug.
 *  2. **Handle** — one 44×5dp [SheetHandle] in `#E3E6EB` everywhere, not M3's thin 32×4 grey pill.
 *  3. **Shape** — [JGalleryDimens.SheetRadius] (26dp top), elevation = scrim + soft top shadow, no border.
 *
 * [title]/[subtitle] render the standard header (20sp/800 title + 12.5sp/600 muted sub-count). Pass
 * `null` when a sheet owns a bespoke header (e.g. the Copy/Move sheet, which restates a live item count
 * and swaps into an inline create step). [content] runs in the sheet's [ColumnScope] and keeps its own
 * gutter/padding, so migrating an existing sheet is just swapping the wrapper — the body is untouched.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun JGallerySheet(
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    title: String? = null,
    subtitle: String? = null,
    // Plain boolean rather than exposing the experimental `SheetState` in the signature — an
    // experimental type in a public signature would force every caller to opt in too. Sheets that must
    // skip the half-expanded stop (e.g. the permission-recovery sheet) pass `true`.
    skipPartiallyExpanded: Boolean = false,
    content: @Composable ColumnScope.() -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        modifier = modifier,
        shape = JGalleryDimens.SheetRadius,
        containerColor = MaterialTheme.colorScheme.surface,
        dragHandle = { SheetHandle() },
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = skipPartiallyExpanded),
    ) {
        if (title != null) {
            SheetHeader(title = title, subtitle = subtitle)
        }
        content()
    }
}

/**
 * The shared 44×5dp grab handle (D4 §D4-05). Centered, `#E3E6EB`, with the 4/14dp margins from the spec.
 * Replaces the two private `GrabHandle()` copies the Sort/Column sheets used to carry.
 */
@Composable
fun SheetHandle() {
    Box(modifier = Modifier.padding(top = 14.dp, bottom = 4.dp)) {
        Box(
            modifier = Modifier
                .size(width = JGalleryDimens.GrabHandleWidth, height = JGalleryDimens.GrabHandleHeight)
                .background(JGalleryColors.SheetHandle, RoundedCornerShape(50)),
        )
    }
}

/** Standard sheet header (D4 §D4-05 type ramp): title 20sp/800, optional muted 12.5sp/600 sub-count. */
@Composable
private fun SheetHeader(title: String, subtitle: String?) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp)
            .padding(top = 4.dp, bottom = 8.dp),
    ) {
        Text(
            text = title,
            fontSize = 20.sp,
            fontWeight = FontWeight.ExtraBold,
            color = MaterialTheme.colorScheme.onSurface,
        )
        if (subtitle != null) {
            Text(
                text = subtitle,
                fontSize = 12.5.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
    }
}
