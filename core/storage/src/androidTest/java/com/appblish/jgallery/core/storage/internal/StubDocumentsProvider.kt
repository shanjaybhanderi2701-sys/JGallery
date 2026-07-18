package com.appblish.jgallery.core.storage.internal

import android.content.Context
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.os.CancellationSignal
import android.os.ParcelFileDescriptor
import android.provider.DocumentsContract
import android.provider.DocumentsContract.Document
import android.provider.DocumentsContract.Root
import android.provider.DocumentsProvider
import java.io.File

/**
 * A REAL `DocumentsProvider` — the deterministic stand-in for the system SAF picker used by the
 * export instrumented tests (APP-571). It is *not* a Fake `StorageOps`: `MediaStoreStorageOps.
 * createTreeSink` / `DocumentFileSink` and `namesInTree` run their genuine `DocumentFile` +
 * `ContentResolver` code paths against this provider, so the test exercises the same
 * `DocumentsContract` machinery a device folder pick would (createDocument / openDocument /
 * queryChildDocuments / deleteDocument) — just with a deterministic, on-device tree instead of the
 * flaky DocumentsUI input pipe (see APP-566 for why the UiAutomator pick could not be driven).
 *
 * Document ids are absolute file paths under [rootDir] (the classic AOSP StorageProvider scheme).
 * The provider lives in the androidTest APK and is reached in-process (same uid), so no persisted
 * uri grant is needed — the grant a real pick would mint is orthogonal to the DocumentFile write
 * mechanics this guard is proving.
 */
class StubDocumentsProvider : DocumentsProvider() {

    override fun onCreate(): Boolean = true

    override fun queryRoots(projection: Array<out String>?): Cursor {
        val root = rootDir(ctx())
        root.mkdirs()
        return MatrixCursor(projection ?: DEFAULT_ROOT_PROJECTION).apply {
            newRow()
                .add(Root.COLUMN_ROOT_ID, ROOT_ID)
                .add(Root.COLUMN_DOCUMENT_ID, root.absolutePath)
                .add(Root.COLUMN_TITLE, "JGallery test tree")
                .add(Root.COLUMN_FLAGS, Root.FLAG_SUPPORTS_CREATE)
                .add(Root.COLUMN_ICON, android.R.drawable.ic_menu_save)
        }
    }

    override fun queryDocument(documentId: String, projection: Array<out String>?): Cursor =
        MatrixCursor(projection ?: DEFAULT_DOCUMENT_PROJECTION).apply {
            includeFile(this, File(documentId))
        }

    override fun queryChildDocuments(
        parentDocumentId: String,
        projection: Array<out String>?,
        sortOrder: String?,
    ): Cursor = MatrixCursor(projection ?: DEFAULT_DOCUMENT_PROJECTION).apply {
        File(parentDocumentId).listFiles()?.forEach { includeFile(this, it) }
    }

    /**
     * Create a document under [parentDocumentId]. Mirrors a real provider's contract: when a file of
     * [displayName] already exists it auto-suffixes (`name (1).ext`) rather than clobbering — the
     * backstop that guards a copy from ever overwriting a user's file even if the engine's
     * `namesInTree` reservation were bypassed (APP-571 requirement 2, provider half).
     */
    override fun createDocument(
        parentDocumentId: String,
        mimeType: String,
        displayName: String,
    ): String {
        val parent = File(parentDocumentId)
        parent.mkdirs()
        if (mimeType == Document.MIME_TYPE_DIR) {
            val dir = File(parent, displayName).apply { mkdir() }
            return dir.absolutePath
        }
        val target = File(parent, nonCollidingName(parent, displayName))
        target.createNewFile()
        return target.absolutePath
    }

    override fun deleteDocument(documentId: String) {
        File(documentId).deleteRecursively()
    }

    override fun openDocument(
        documentId: String,
        mode: String,
        signal: CancellationSignal?,
    ): ParcelFileDescriptor =
        ParcelFileDescriptor.open(File(documentId), ParcelFileDescriptor.parseMode(mode))

    override fun isChildDocument(parentDocumentId: String, documentId: String): Boolean =
        File(documentId).absolutePath.startsWith(File(parentDocumentId).absolutePath + File.separator)

    override fun getDocumentType(documentId: String): String = mimeOf(File(documentId))

    // --- internals ---

    private fun ctx(): Context = requireNotNull(context) { "provider has no context" }

    private fun includeFile(cursor: MatrixCursor, file: File) {
        val isDir = file.isDirectory
        val flags = if (isDir) {
            Document.FLAG_DIR_SUPPORTS_CREATE
        } else {
            Document.FLAG_SUPPORTS_WRITE or Document.FLAG_SUPPORTS_DELETE
        }
        cursor.newRow()
            .add(Document.COLUMN_DOCUMENT_ID, file.absolutePath)
            .add(Document.COLUMN_DISPLAY_NAME, file.name)
            .add(Document.COLUMN_SIZE, file.length())
            .add(Document.COLUMN_MIME_TYPE, mimeOf(file))
            .add(Document.COLUMN_LAST_MODIFIED, file.lastModified())
            .add(Document.COLUMN_FLAGS, flags)
    }

    private fun mimeOf(file: File): String = when {
        file.isDirectory -> Document.MIME_TYPE_DIR
        file.name.endsWith(".jpg", true) || file.name.endsWith(".jpeg", true) -> "image/jpeg"
        file.name.endsWith(".png", true) -> "image/png"
        file.name.endsWith(".mp4", true) -> "video/mp4"
        else -> "application/octet-stream"
    }

    private fun nonCollidingName(parent: File, displayName: String): String {
        if (!File(parent, displayName).exists()) return displayName
        val dot = displayName.lastIndexOf('.')
        val base = if (dot > 0) displayName.substring(0, dot) else displayName
        val ext = if (dot > 0) displayName.substring(dot) else ""
        var n = 1
        while (File(parent, "$base ($n)$ext").exists()) n++
        return "$base ($n)$ext"
    }

    companion object {
        /** Authority declared in the androidTest manifest for this provider. */
        const val AUTHORITY = "com.appblish.jgallery.core.storage.test.documents"
        private const val ROOT_ID = "stub-root"

        /** The real on-device directory this provider serves as its SAF tree. */
        fun rootDir(context: Context): File = File(context.filesDir, "stub-saf-tree")

        /** The `content://` tree uri a folder pick would hand `exportCopy`, for [rootDir]. */
        fun treeUri(context: Context): Uri =
            DocumentsContract.buildTreeDocumentUri(AUTHORITY, rootDir(context).absolutePath)

        private val DEFAULT_ROOT_PROJECTION = arrayOf(
            Root.COLUMN_ROOT_ID,
            Root.COLUMN_DOCUMENT_ID,
            Root.COLUMN_TITLE,
            Root.COLUMN_FLAGS,
            Root.COLUMN_ICON,
        )

        private val DEFAULT_DOCUMENT_PROJECTION = arrayOf(
            Document.COLUMN_DOCUMENT_ID,
            Document.COLUMN_DISPLAY_NAME,
            Document.COLUMN_SIZE,
            Document.COLUMN_MIME_TYPE,
            Document.COLUMN_LAST_MODIFIED,
            Document.COLUMN_FLAGS,
        )
    }
}
