package com.appblish.jgallery.feature.viewer

import com.appblish.jgallery.core.model.OperationResult

/**
 * The single-item file actions the viewer runs through the §7 E8 core (spec §5/§7): the same
 * operations E11 exposes for multi-select, scoped to the one item on screen. [SET_AS] is a system
 * intent (no [OperationResult]) and only produces a [ViewerActionUiState.Finished] on failure.
 */
internal enum class ViewerActionKind(val doneLabel: String, val failLabel: String) {
    COPY("Copied", "Couldn't copy"),
    MOVE("Moved", "Couldn't move"),
    RENAME("Renamed", "Couldn't rename"),
    TRASH("Moved to Trash", "Couldn't delete"),
    SET_AS("", "Set as unavailable"),
}

/** UI state for the current single-item action; drives the result snackbar (spec §7.6 summary). */
internal sealed interface ViewerActionUiState {
    data object Idle : ViewerActionUiState

    /** An op is streaming/awaiting in the retained scope; the UI can show a busy hint. */
    data class Running(val kind: ViewerActionKind) : ViewerActionUiState

    /** Terminal: the "1 done / reason" summary the viewer surfaces once, then dismisses. */
    data class Finished(val kind: ViewerActionKind, val result: OperationResult) : ViewerActionUiState
}

/** Snackbar text for a finished single-item op — the success label, or the failure reason. */
internal fun ViewerActionUiState.Finished.message(): String =
    if (result.failed == 0) {
        kind.doneLabel
    } else {
        result.failures.firstOrNull()?.reason?.let { "${kind.failLabel}: $it" } ?: kind.failLabel
    }
