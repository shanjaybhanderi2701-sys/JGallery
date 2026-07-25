package com.appblish.jgallery.core.storage.internal

import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import androidx.exifinterface.media.ExifInterface
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.appblish.jgallery.core.model.MediaId
import com.appblish.jgallery.core.model.RotationDirection
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * On-device guard for 90° photo rotation (G3-1 · APP-639). Every assertion runs the REAL
 * [MediaStoreStorageOps.rotate] path — an EXIF `Orientation` rewrite over a genuine `ContentResolver`
 * file descriptor — against app-owned MediaStore rows, so it proves the orientation is **persisted to
 * the file** (what another gallery app / file manager reads), not just an in-session view transform.
 * The pure quarter-turn math is covered exhaustively off-device by `ExifOrientationTest`; this pins
 * the platform write itself, which can only be trusted on a real provider.
 *
 * Sources are this app's own new rows (created here, torn down in [tearDown]); an app may always
 * contribute, read, and rewrite its own scoped-storage entries, so no All Files Access is needed.
 */
@RunWith(AndroidJUnit4::class)
class MediaStoreRotateTest {

    private lateinit var context: Context
    private lateinit var ops: MediaStoreStorageOps
    private val createdUris = mutableListOf<Uri>()

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        ops = MediaStoreStorageOps(context, context.contentResolver, Dispatchers.IO)
    }

    @After
    fun tearDown() {
        val resolver = context.contentResolver
        createdUris.forEach { runCatching { resolver.delete(it, null, null) } }
    }

    @Test
    fun rotate_right_then_left_persists_the_exif_orientation_to_the_file() = runBlocking {
        val (id, uri) = seedJpeg("rotate_rl")
        // A freshly-compressed JPEG carries no orientation tag at all → UNDEFINED, treated as upright.
        assertEquals(ExifInterface.ORIENTATION_UNDEFINED, readOrientation(uri))
        val sizeBefore = readSize(uri)

        assertTrue(ops.rotate(id, RotationDirection.RIGHT))
        assertEquals(ExifInterface.ORIENTATION_ROTATE_90, readOrientation(uri))
        // The bytes on disk really changed (an EXIF APP1 segment was written) — this is what makes the
        // index re-sync (its signature diffs on size) and MediaStore regenerate its thumbnail; it is a
        // persisted file change, not a view-only transform.
        assertTrue("rotate must rewrite the file", readSize(uri) != sizeBefore)

        // Persistence survives a second, independent turn (compounding, not a fixed value).
        assertTrue(ops.rotate(id, RotationDirection.RIGHT))
        assertEquals(ExifInterface.ORIENTATION_ROTATE_180, readOrientation(uri))

        // The opposite turn walks it back — left after two rights lands on 90°.
        assertTrue(ops.rotate(id, RotationDirection.LEFT))
        assertEquals(ExifInterface.ORIENTATION_ROTATE_90, readOrientation(uri))

        // And a full round-trip (4 rights from 90°) returns to exactly where we were.
        repeat(4) { assertTrue(ops.rotate(id, RotationDirection.RIGHT)) }
        assertEquals(ExifInterface.ORIENTATION_ROTATE_90, readOrientation(uri))
    }

    @Test
    fun rotate_rejects_a_non_image_without_touching_it() = runBlocking {
        // A video row: rotate has no meaning for it and must return false before any decode/write.
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, uniqueName("rotate_vid", "mp4"))
            put(MediaStore.MediaColumns.MIME_TYPE, "video/mp4")
            put(MediaStore.MediaColumns.RELATIVE_PATH, "${Environment.DIRECTORY_MOVIES}/$SOURCE_FOLDER/")
        }
        val collection = MediaStore.Video.Media.getContentUri(VOLUME)
        val uri = requireNotNull(context.contentResolver.insert(collection, values))
        createdUris += uri
        context.contentResolver.openOutputStream(uri)!!.use { it.write(byteArrayOf(0, 0, 0, 0)) }
        val id = MediaId(ContentUris.parseId(uri).toString())

        assertFalse(ops.rotate(id, RotationDirection.RIGHT))
    }

    // --- helpers ---

    private fun seedJpeg(tag: String): Pair<MediaId, Uri> {
        val resolver = context.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, uniqueName(tag, "jpg"))
            put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
            put(MediaStore.MediaColumns.RELATIVE_PATH, "${Environment.DIRECTORY_PICTURES}/$SOURCE_FOLDER/")
        }
        val collection = MediaStore.Images.Media.getContentUri(VOLUME)
        val uri = requireNotNull(resolver.insert(collection, values)) { "seed insert failed" }
        // Write a real, decodable JPEG so ExifInterface has a valid container to append an APP1 segment to.
        val bitmap = Bitmap.createBitmap(8, 4, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.BLUE) }
        resolver.openOutputStream(uri)!!.use { bitmap.compress(Bitmap.CompressFormat.JPEG, 90, it) }
        bitmap.recycle()
        createdUris += uri
        return MediaId(ContentUris.parseId(uri).toString()) to uri
    }

    private fun readOrientation(uri: Uri): Int =
        context.contentResolver.openInputStream(uri)!!.use { stream ->
            ExifInterface(stream).getAttributeInt(
                ExifInterface.TAG_ORIENTATION,
                ExifInterface.ORIENTATION_UNDEFINED,
            )
        }

    private fun readSize(uri: Uri): Long =
        context.contentResolver.openFileDescriptor(uri, "r")!!.use { it.statSize }

    private fun uniqueName(tag: String, ext: String): String =
        "jgallery_rotate_${tag}_${System.nanoTime()}.$ext"

    private companion object {
        const val VOLUME = "external"
        const val SOURCE_FOLDER = "JGalleryRotateTest"
    }
}
