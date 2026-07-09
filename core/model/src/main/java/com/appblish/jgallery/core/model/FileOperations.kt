package com.appblish.jgallery.core.model

/** Progress for a bulk file operation (spec §1 rule 7 / §7.6). Emitted off the main thread. */
data class OperationProgress(
    val completed: Int,
    val total: Int,
    val currentName: String?,
)

/** Terminal "X copied, Y failed" summary shown after a bulk op (spec §7.6). */
data class OperationResult(
    val succeeded: Int,
    val failed: Int,
    val failures: List<Failure> = emptyList(),
) {
    val total: Int get() = succeeded + failed
    data class Failure(val id: MediaId, val reason: String)
}

/**
 * A single event in a bulk file operation's stream (copy / move / trash / delete — spec §7).
 *
 * A bulk flow emits zero-or-more [InProgress] events as items complete, then exactly one terminal
 * [Completed] carrying the "X done, Y failed" [OperationResult]. Modelling both in one stream lets
 * E11's bulk bar and the single-item flows consume live progress *and* the final summary from the
 * same subscription — without a second call or a side channel. If the collector cancels, the flow
 * terminates via `CancellationException` and no [Completed] is emitted (partial work is rolled back
 * item-by-item), which is how callers distinguish "finished" from "cancelled".
 */
sealed interface FileOperationEvent {
    /** Live progress after an item finishes; [progress] carries completed/total + current name. */
    data class InProgress(val progress: OperationProgress) : FileOperationEvent

    /** Terminal summary. Always the last event a completed (non-cancelled) bulk flow emits. */
    data class Completed(val summary: OperationResult) : FileOperationEvent
}
