package com.appblish.jgallery.core.ui.selection

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.appblish.jgallery.core.model.MediaId
import com.appblish.jgallery.core.model.OperationProgress
import com.appblish.jgallery.core.model.OperationResult
import com.appblish.jgallery.core.ui.theme.JGalleryColors
import kotlin.math.roundToInt

/** Copy vs Move — swaps the verb only (C1-04 "Copy variant swaps the verb only"). */
enum class AlbumOpVerb { COPY, MOVE }

/** "Moving" / "Copying" — present participle for the running header. */
fun AlbumOpVerb.presentParticiple(): String = when (this) {
    AlbumOpVerb.COPY -> "Copying"
    AlbumOpVerb.MOVE -> "Moving"
}

/** "Moved" / "Copied" — capitalised past tense for the result headline. */
fun AlbumOpVerb.pastTense(): String = when (this) {
    AlbumOpVerb.COPY -> "Copied"
    AlbumOpVerb.MOVE -> "Moved"
}

/** "moved" / "copied" — lowercase past participle for inline copy ("couldn't be moved"). */
fun AlbumOpVerb.pastParticiple(): String = when (this) {
    AlbumOpVerb.COPY -> "copied"
    AlbumOpVerb.MOVE -> "moved"
}

/**
 * Static context of a whole-album copy/move (C1-04, item 13): what's running, so the dialog can name
 * the album + destination + total up front instead of an indeterminate mystery spinner.
 *
 * @param sizeLabel human byte size ("1.4 GB"), optional — omitted when unknown.
 * @param destinationLabel where it lands ("Internal storage · Pictures").
 */
data class AlbumOperationContext(
    val verb: AlbumOpVerb,
    val albumName: String,
    val destinationLabel: String,
    val total: Int,
    val sizeLabel: String? = null,
)

/**
 * UI-facing state of a whole-album op, derived from the engine's `Flow<FileOperationEvent>` by the
 * caller's ViewModel so it survives config changes while the op runs off the main thread (C1-04
 * callout #3). [Running] drives the progress dialog; [Finished] drives the terminal summary.
 */
sealed interface AlbumOpUiState {
    /** In flight; [progress] is null until the first item completes. [cancelling] once Cancel is hit. */
    data class Running(
        val context: AlbumOperationContext,
        val progress: OperationProgress?,
        val cancelling: Boolean = false,
    ) : AlbumOpUiState

    /** Terminal: success (all done) / partial (some failed) / cancelled (stopped early). */
    data class Finished(
        val context: AlbumOperationContext,
        val summary: OperationResult,
        val cancelled: Boolean = false,
    ) : AlbumOpUiState
}

/** 0..1 progress fraction; 0 until the first item finishes so the bar is honest, not a fake spin. */
fun OperationProgress?.fraction(): Float {
    if (this == null || total <= 0) return 0f
    return (completed.toFloat() / total.toFloat()).coerceIn(0f, 1f)
}

/** Whole-number percent for the running header ("59%" for 184/312, rounded to match the redline). */
fun OperationProgress?.percent(): Int = (fraction() * 100f).roundToInt()

/** "184 of 312" running counter (C1-04 callout #1). */
fun runningCounter(progress: OperationProgress?, total: Int): String {
    val done = progress?.completed ?: 0
    return "$done of $total"
}

/**
 * Terminal headline (C1-04 callout #5 / States row):
 *  - clean success → "Moved 312"
 *  - partial       → "Moved 309 of 312"
 *  - cancelled     → "Moved 184 before cancel"
 */
fun finishedHeadline(state: AlbumOpUiState.Finished): String {
    val verb = state.context.verb.pastTense()
    val s = state.summary
    return when {
        state.cancelled -> "$verb ${s.succeeded} before cancel"
        s.failed > 0 -> "$verb ${s.succeeded} of ${state.context.total}"
        else -> "$verb ${s.succeeded}"
    }
}

/**
 * Cancellable, determinate progress dialog for a whole-album copy/move (C1-04, item 13). Modal over a
 * dimmed screen, but the op itself runs on a background worker — rotating/backgrounding doesn't kill it
 * and the caller re-attaches this dialog to the still-running job. Bind [state] to the
 * `Flow<FileOperationEvent>` from `FileOperationEngine`; [onCancel] calls the engine's cooperative
 * cancel (already-copied items stay; a Move only removes each source after its copy is confirmed, so a
 * cancel never loses data — §7.2). The terminal summary reuses the bulk-result vocabulary so
 * copy/move/delete all resolve the same way.
 *
 * @param nameFor resolves a failed item's display name for the itemised failure list; falls back to
 *   the raw id when null.
 */
@Composable
fun AlbumOpProgressDialog(
    state: AlbumOpUiState,
    onCancel: () -> Unit,
    onDone: () -> Unit,
    onViewSkipped: () -> Unit = {},
    nameFor: (MediaId) -> String? = { null },
) {
    // Running is non-dismissable (use Cancel); Finished dismisses via Done — either way, tapping
    // outside or Back must not silently abandon a live op or an unread summary.
    Dialog(
        onDismissRequest = { if (state is AlbumOpUiState.Finished) onDone() },
        properties = DialogProperties(
            dismissOnBackPress = state is AlbumOpUiState.Finished,
            dismissOnClickOutside = false,
        ),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(28.dp))
                .background(MaterialTheme.colorScheme.surface)
                .padding(24.dp),
        ) {
            when (state) {
                is AlbumOpUiState.Running -> RunningBody(state, onCancel)
                is AlbumOpUiState.Finished -> FinishedBody(state, onDone, onViewSkipped, nameFor)
            }
        }
    }
}

@Composable
private fun RunningBody(state: AlbumOpUiState.Running, onCancel: () -> Unit) {
    val ctx = state.context
    Column(Modifier.fillMaxWidth().testTag("album_op_progress")) {
        Text(
            text = "${ctx.verb.presentParticiple()} “${ctx.albumName}”",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            text = buildString {
                append("${ctx.total} items")
                ctx.sizeLabel?.let { append(" · $it") }
                append(" to ${ctx.destinationLabel}")
            },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp),
        )
        Spacer(Modifier.height(20.dp))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = runningCounter(state.progress, ctx.total),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f).testTag("album_op_counter"),
            )
            Text(
                text = "${state.progress.percent()}%",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        Spacer(Modifier.height(8.dp))
        if (state.progress == null || state.progress.total == 0) {
            LinearProgressIndicator(
                Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
                color = MaterialTheme.colorScheme.primary,
            )
        } else {
            LinearProgressIndicator(
                progress = { state.progress.fraction() },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .testTag("album_op_bar"),
                color = MaterialTheme.colorScheme.primary,
            )
        }
        state.progress?.currentName?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 8.dp).testTag("album_op_current"),
            )
        }
        Spacer(Modifier.height(20.dp))
        // Cancel is a full-width, always-reachable button (callout #2); once hit it finishes the
        // in-flight item then stops, so the label flips to "Cancelling…" and disables.
        OutlinedButton(
            onClick = onCancel,
            enabled = !state.cancelling,
            modifier = Modifier.fillMaxWidth().testTag("album_op_cancel"),
        ) {
            Text(if (state.cancelling) "Cancelling…" else "Cancel")
        }
    }
}

@Composable
private fun FinishedBody(
    state: AlbumOpUiState.Finished,
    onDone: () -> Unit,
    onViewSkipped: () -> Unit,
    nameFor: (MediaId) -> String?,
) {
    val partial = state.summary.failed > 0
    Column(
        Modifier.fillMaxWidth().testTag("album_op_result"),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        val badge = if (partial || state.cancelled) JGalleryColors.Warn else JGalleryColors.TrustGreen
        Icon(
            imageVector = if (partial || state.cancelled) Icons.Filled.WarningAmber else Icons.Filled.CheckCircle,
            contentDescription = null,
            tint = badge,
            modifier = Modifier.size(44.dp),
        )
        Spacer(Modifier.height(12.dp))
        Text(
            text = finishedHeadline(state),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.testTag("album_op_result_headline"),
        )
        if (partial) {
            Spacer(Modifier.height(6.dp))
            Text(
                text = "${state.summary.failed} items couldn't be " +
                    "${state.context.verb.pastParticiple()} and were left in place.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 8.dp),
            )
            Spacer(Modifier.height(16.dp))
            // Per-item failures are itemised with a real reason (callout #6), never a generic error.
            Column(
                Modifier
                    .fillMaxWidth()
                    .heightIn(max = 200.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                state.summary.failures.forEach { failure ->
                    FailureRow(name = nameFor(failure.id) ?: failure.id.value, reason = failure.reason)
                }
            }
        }
        Spacer(Modifier.height(20.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            if (partial) {
                OutlinedButton(
                    onClick = onViewSkipped,
                    modifier = Modifier.weight(1f).testTag("album_op_view_skipped"),
                ) { Text("View skipped") }
            }
            Button(
                onClick = onDone,
                modifier = Modifier.weight(1f).testTag("album_op_done"),
            ) { Text("Done") }
        }
    }
}

@Composable
private fun FailureRow(name: String, reason: String) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(JGalleryColors.CorruptFill)
            .padding(horizontal = 14.dp, vertical = 10.dp),
    ) {
        Text(
            text = name,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = reason,
            style = MaterialTheme.typography.bodySmall,
            color = JGalleryColors.Danger,
        )
    }
}
