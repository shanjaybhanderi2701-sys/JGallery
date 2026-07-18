package com.appblish.jgallery.core.ui.selection

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.appblish.jgallery.core.model.OperationProgress
import com.appblish.jgallery.core.model.OperationResult

/**
 * UI-facing state of a bulk file operation, kept in the screen's ViewModel so it survives config
 * changes while the op runs off the main thread (spec §7.6). [Idle] renders no overlay; [Running]
 * drives the progress dialog; [Finished] drives the result summary.
 */
sealed interface BulkOperationUiState {
    data object Idle : BulkOperationUiState

    /** An op is in flight; [progress] is null until the first item completes (indeterminate spin). */
    data class Running(val action: BulkAction, val progress: OperationProgress?) : BulkOperationUiState

    /** Terminal "X done, Y failed" summary (spec §7.6), shown until the user dismisses it. */
    data class Finished(val action: BulkAction, val summary: OperationResult) : BulkOperationUiState
}

/** Verb for the given action, used in progress + summary copy ("Copying…", "3 copied, 1 failed"). */
private fun BulkAction.presentVerb(): String = when (this) {
    BulkAction.COPY -> "Copying"
    BulkAction.MOVE -> "Moving"
    BulkAction.TRASH -> "Moving to Trash"
    BulkAction.EXPORT -> "Saving a copy"
}

private fun BulkAction.pastVerb(): String = when (this) {
    BulkAction.COPY -> "copied"
    BulkAction.MOVE -> "moved"
    BulkAction.TRASH -> "moved to Trash"
    BulkAction.EXPORT -> "saved"
}

/**
 * At-scale guardrail (design W3-09): a bulk op over [LARGE_SELECTION_THRESHOLD] items — easy to reach
 * with Select-All on a 61,908-item folder — gets one extra "are you sure" step before the normal
 * pick/confirm flow, so a stray tap can't move tens of thousands of files at once.
 */
const val LARGE_SELECTION_THRESHOLD: Int = 500

/** True when [count] is large enough to warrant the extra at-scale confirm (design W3-09). */
fun isLargeSelection(count: Int): Boolean = count >= LARGE_SELECTION_THRESHOLD

/**
 * Extra confirm shown before a large bulk op (design W3-09). Names the action and the count so the
 * user sees the scale before committing; confirming falls through to the normal destination-picker
 * (Copy/Move) or delete-confirm (Trash) flow.
 */
@Composable
fun LargeSelectionWarningDialog(
    count: Int,
    action: BulkAction,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.testTag("large_selection_warning"),
        title = { Text("${action.presentVerb()} $count items?") },
        text = { Text("That's a lot of files. This can take a while — continue?") },
        confirmButton = {
            TextButton(onClick = onConfirm, modifier = Modifier.testTag("large_selection_warning_ok")) {
                Text("Continue")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

/**
 * Confirm dialog before a bulk delete (spec §7.5: delete routes to Trash, which is restorable, so a
 * single confirm — not the 2-step permanent-delete gate — is correct here).
 */
@Composable
fun BulkDeleteConfirmDialog(count: Int, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.testTag("bulk_delete_confirm"),
        title = { Text("Move $count to Trash?") },
        text = { Text("Items go to the Recycle Bin and can be restored later.") },
        confirmButton = {
            TextButton(onClick = onConfirm, modifier = Modifier.testTag("bulk_delete_confirm_ok")) {
                Text("Move to Trash")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

/**
 * Progress dialog for a running bulk op (spec §7.6 "progress"). Determinate once the first item
 * finishes; shows "Copying 3 of 12" plus the current file name. [onCancel] cancels the batch —
 * partial work rolls back item-by-item per the E8 engine contract.
 */
@Composable
fun BulkProgressDialog(state: BulkOperationUiState.Running, onCancel: () -> Unit) {
    AlertDialog(
        onDismissRequest = { /* non-dismissable; use Cancel */ },
        modifier = Modifier.testTag("bulk_progress"),
        title = { Text(state.action.presentVerb()) },
        text = {
            Column(Modifier.fillMaxWidth()) {
                val p = state.progress
                if (p == null || p.total == 0) {
                    LinearProgressIndicator(Modifier.fillMaxWidth())
                } else {
                    LinearProgressIndicator(
                        progress = { p.completed.toFloat() / p.total.toFloat() },
                        modifier = Modifier.fillMaxWidth().testTag("bulk_progress_bar"),
                    )
                    Text(
                        text = "${p.completed} of ${p.total}",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(top = 12.dp).testTag("bulk_progress_label"),
                    )
                    p.currentName?.let {
                        Text(
                            text = it,
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(top = 2.dp),
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onCancel, modifier = Modifier.testTag("bulk_progress_cancel")) {
                Text("Cancel")
            }
        },
    )
}

/**
 * Result summary shown when a bulk op completes (spec §7.6 "result summary"): "X copied, Y failed".
 */
@Composable
fun BulkResultDialog(state: BulkOperationUiState.Finished, onDismiss: () -> Unit) {
    val s = state.summary
    val headline = buildString {
        append("${s.succeeded} ${state.action.pastVerb()}")
        if (s.failed > 0) append(", ${s.failed} failed")
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.testTag("bulk_result"),
        title = { Text("Done") },
        text = { Text(headline, modifier = Modifier.testTag("bulk_result_summary")) },
        confirmButton = {
            TextButton(onClick = onDismiss, modifier = Modifier.testTag("bulk_result_ok")) { Text("OK") }
        },
    )
}
