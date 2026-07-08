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
