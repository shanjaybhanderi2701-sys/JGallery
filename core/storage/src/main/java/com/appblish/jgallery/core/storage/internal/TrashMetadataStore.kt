package com.appblish.jgallery.core.storage.internal

import com.appblish.jgallery.core.model.MediaId
import com.appblish.jgallery.core.model.MediaType
import com.appblish.jgallery.core.model.TrashEntry
import kotlinx.coroutines.flow.Flow
import java.util.Base64

/**
 * The persistent record of what JGallery has moved to its Recycle Bin (spec §7.5). This is the
 * "app-managed" half of Trash: it survives process death and outlives any provider-side trash flag,
 * so the bin only ever shows items *this app* trashed, days-left is computed from the app's own
 * [com.appblish.jgallery.core.model.TrashPolicy], and a restore always knows the true origin path.
 *
 * An interface so [TrashEngine] unit-tests against an in-memory fake; the persistent binding lives in
 * the DI module.
 */
internal interface TrashMetadataStore {

    /** The current bin contents, newest-first, re-emitted on every change. */
    fun observe(): Flow<List<TrashEntry>>

    /** Snapshot of the current bin contents (no ordering guarantees). */
    suspend fun current(): List<TrashEntry>

    /** Record (or overwrite by id) one trashed item. */
    suspend fun put(entry: TrashEntry)

    /** Forget the given ids (after a restore or permanent delete). */
    suspend fun remove(ids: Collection<MediaId>)

    /** Forget everything (Empty bin). */
    suspend fun clear()
}

/**
 * Pure-Kotlin (de)serialization for the bin's metadata — no Android, no JSON dependency, so it is
 * unit-testable on the JVM. Each [TrashEntry] is one line of tab-separated fields; the free-text
 * fields (names, path, mime) are Base64-encoded so a tab, newline, or any other byte in a file name
 * can never corrupt the record boundaries. Unparseable lines are skipped rather than throwing, so a
 * single bad record can't wipe the whole bin.
 */
internal object TrashRecordCodec {

    fun encode(entries: List<TrashEntry>): String =
        entries.joinToString("\n") { e ->
            listOf(
                e.id.value.enc(),
                e.displayName.enc(),
                e.type.name,
                e.mimeType.enc(),
                e.originalBucketId.enc(),
                e.originalBucketName.enc(),
                e.originalRelativePath.enc(),
                e.trashedAtMillis.toString(),
                e.sizeBytes.toString(),
                e.width.toString(),
                e.height.toString(),
                e.durationMillis.toString(),
            ).joinToString("\t")
        }

    fun decode(text: String): List<TrashEntry> =
        text.lineSequence()
            .filter { it.isNotBlank() }
            .mapNotNull { line -> runCatching { parse(line) }.getOrNull() }
            .toList()

    private fun parse(line: String): TrashEntry {
        val f = line.split("\t")
        require(f.size == FIELD_COUNT) { "expected $FIELD_COUNT fields, got ${f.size}" }
        return TrashEntry(
            id = MediaId(f[0].dec()),
            displayName = f[1].dec(),
            type = MediaType.valueOf(f[2]),
            mimeType = f[3].dec(),
            originalBucketId = f[4].dec(),
            originalBucketName = f[5].dec(),
            originalRelativePath = f[6].dec(),
            trashedAtMillis = f[7].toLong(),
            sizeBytes = f[8].toLong(),
            width = f[9].toInt(),
            height = f[10].toInt(),
            durationMillis = f[11].toLong(),
        )
    }

    private const val FIELD_COUNT = 12

    private fun String.enc(): String = Base64.getEncoder().encodeToString(toByteArray(Charsets.UTF_8))
    private fun String.dec(): String = String(Base64.getDecoder().decode(this), Charsets.UTF_8)
}
