package com.appblish.jgallery.core.ui.selection

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.appblish.jgallery.core.model.MediaItem
import com.appblish.jgallery.core.model.MediaType
import com.appblish.jgallery.core.ui.theme.JGalleryColors
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * A read-only "Details" summary for a multi-select (design G1-D7 item 11). The host builds the
 * [title] and label→value [rows] from its own model (media items or albums), so this dialog stays a
 * dumb, platform-agnostic renderer shared by the Photos / album-detail media selection and the
 * Albums-tab album selection. Details is **multi-safe**: it works for any selection ≥ 1.
 */
data class SelectionDetails(
    val title: String,
    val rows: List<Pair<String, String>>,
)

/** Renders a [SelectionDetails] as a simple read-only dialog with a single Close action. */
@Composable
fun SelectionDetailsDialog(details: SelectionDetails, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.testTag("selection_details_dialog"),
        title = { Text(details.title) },
        text = {
            Column {
                details.rows.forEach { (label, value) ->
                    Text(
                        text = "$label: $value",
                        style = MaterialTheme.typography.bodyMedium,
                        color = JGalleryColors.Text,
                        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss, modifier = Modifier.testTag("selection_details_close")) {
                Text("Close")
            }
        },
    )
}

/**
 * Aggregate [SelectionDetails] for a media selection (design G1-D7 item 11): item count (split into
 * photos / videos when the selection is mixed), combined size, and the captured-date range. Shared by
 * the Photos tab and album-detail media selection so multi-select Details is identical in both.
 */
fun mediaSelectionDetails(items: List<MediaItem>): SelectionDetails {
    val photos = items.count { it.type == MediaType.IMAGE }
    val videos = items.count { it.type == MediaType.VIDEO }
    val dated = items.map { it.dateTakenMillis }.filter { it > 0L }
    val title = when {
        items.isEmpty() -> "No items"
        videos == 0 -> "$photos ${if (photos == 1) "photo" else "photos"}"
        photos == 0 -> "$videos ${if (videos == 1) "video" else "videos"}"
        else -> "${items.size} items"
    }
    val rows = buildList {
        add("Items" to items.size.toString())
        if (photos > 0 && videos > 0) {
            add("Photos" to photos.toString())
            add("Videos" to videos.toString())
        }
        add("Size" to formatByteSize(items.sumOf { it.sizeBytes }))
        add("Date" to formatDateRange(dated.minOrNull() ?: 0L, dated.maxOrNull() ?: 0L))
    }
    return SelectionDetails(title, rows)
}

/** Human-readable byte size (binary units); "—" when unknown (0). Shared so Details rows format alike. */
fun formatByteSize(bytes: Long): String {
    if (bytes <= 0L) return "—"
    val units = listOf("B", "KB", "MB", "GB", "TB")
    var value = bytes.toDouble()
    var unit = 0
    while (value >= 1024.0 && unit < units.lastIndex) {
        value /= 1024.0
        unit++
    }
    return if (unit == 0) "${bytes} B" else String.format(Locale.getDefault(), "%.1f %s", value, units[unit])
}

/** A short date, or a range "a – b" when the two differ; "—" when [oldest]/[newest] are unknown (≤0). */
fun formatDateRange(oldestMillis: Long, newestMillis: Long): String {
    if (oldestMillis <= 0L && newestMillis <= 0L) return "—"
    val fmt = SimpleDateFormat("MMM d, yyyy", Locale.getDefault())
    val lo = if (oldestMillis > 0L) oldestMillis else newestMillis
    val hi = if (newestMillis > 0L) newestMillis else oldestMillis
    val loStr = fmt.format(Date(lo))
    val hiStr = fmt.format(Date(hi))
    return if (loStr == hiStr) loStr else "$loStr – $hiStr"
}
