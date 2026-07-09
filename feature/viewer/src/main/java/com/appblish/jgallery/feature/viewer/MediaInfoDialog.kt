package com.appblish.jgallery.feature.viewer

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.appblish.jgallery.core.model.MediaItem
import com.appblish.jgallery.core.ui.theme.JGalleryColors
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

/** One label/value pair in the Info sheet (spec §5.1). */
internal data class InfoRow(val label: String, val value: String)

private const val UNKNOWN = "—"

/**
 * Read-only Info / Details dialog (spec §5.1): Name, Format, Path, Size, Resolution, Last Modified.
 * Everything shown comes straight off the cached [MediaItem] — no boundary call, no path leaks into
 * feature code beyond the human-readable folder name the index already carries (§1.6).
 */
@Composable
internal fun MediaInfoDialog(item: MediaItem, onDismiss: () -> Unit) {
    val rows = mediaInfoRows(item)
    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.testTag("viewer_info_dialog"),
        containerColor = JGalleryColors.ViewerSheet,
        titleContentColor = Color.White,
        textContentColor = Color.White,
        title = { Text("Details") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                rows.forEach { row ->
                    Row(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            text = row.label,
                            modifier = Modifier.width(96.dp),
                            color = Color.White.copy(alpha = 0.6f),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Text(
                            text = row.value,
                            modifier = Modifier
                                .weight(1f)
                                .padding(start = 12.dp),
                            color = Color.White,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss, modifier = Modifier.testTag("viewer_info_close")) {
                Text("Close", color = Color.White)
            }
        },
    )
}

// --- pure, platform-free row builders (JVM-unit-tested; no Android formatters) ---

private val MODIFIED_FORMAT: DateTimeFormatter =
    DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT)

/** Build the exact §5.1 row set, in order. `zone` is injectable so tests are timezone-stable. */
internal fun mediaInfoRows(item: MediaItem, zone: ZoneId = ZoneId.systemDefault()): List<InfoRow> =
    listOf(
        InfoRow("Name", item.displayName.ifBlank { UNKNOWN }),
        InfoRow("Format", formatFormat(item)),
        InfoRow("Path", item.bucketName.ifBlank { UNKNOWN }),
        InfoRow("Size", formatFileSize(item.sizeBytes)),
        InfoRow("Resolution", formatResolution(item.width, item.height)),
        InfoRow("Modified", formatModified(item.dateModifiedMillis, zone)),
    )

/** Uppercased container type, from the MIME subtype (`image/jpeg` → JPEG) or the file extension. */
internal fun formatFormat(item: MediaItem): String {
    val fromMime = item.mimeType.substringAfterLast('/', "").trim()
    val raw = fromMime.ifEmpty { item.displayName.substringAfterLast('.', "").trim() }
    return raw.uppercase(Locale.US).ifEmpty { UNKNOWN }
}

/** Base-1024 size with one decimal for KB and up; bytes stay whole. 0/negative → unknown. */
internal fun formatFileSize(bytes: Long): String {
    if (bytes <= 0L) return UNKNOWN
    if (bytes < 1024L) return "$bytes B"
    val units = listOf("KB", "MB", "GB", "TB")
    var value = bytes.toDouble() / 1024.0
    var unitIndex = 0
    while (value >= 1024.0 && unitIndex < units.lastIndex) {
        value /= 1024.0
        unitIndex++
    }
    return String.format(Locale.US, "%.1f %s", value, units[unitIndex])
}

/** `W × H` px, or unknown when the index has no dimensions (0). */
internal fun formatResolution(width: Int, height: Int): String =
    if (width > 0 && height > 0) "$width × $height" else UNKNOWN

/** Localized medium-date / short-time; non-positive epoch (unset) → unknown. */
internal fun formatModified(epochMillis: Long, zone: ZoneId): String =
    if (epochMillis <= 0L) {
        UNKNOWN
    } else {
        MODIFIED_FORMAT.format(Instant.ofEpochMilli(epochMillis).atZone(zone))
    }
