package com.appblish.jgallery.core.ui.share

import android.content.Intent
import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * On-device assertions for the actual [Intent] assembled by [ShareIntents.buildSendIntent] — the pure
 * MIME narrowing is unit-tested in `ShareIntentsTest`, but the intent shape needs a real
 * [android.content.Intent] / [Uri], so it lives here (APP-641). The single-item `ACTION_SEND` branch is
 * the one the full-screen viewer's Share fires; both branches are locked so the temporary read grant
 * always reaches the chosen app.
 */
@RunWith(AndroidJUnit4::class)
class ShareIntentsInstrumentedTest {

    private val image = Uri.parse("content://media/external/images/media/42")
    private val other = Uri.parse("content://media/external/images/media/43")

    @Test
    fun singleItem_buildsActionSend_withGrantedStreamAndClipData() {
        val intent = ShareIntents.buildSendIntent(listOf(image), "image/jpeg")

        // A single item shares via ACTION_SEND with one EXTRA_STREAM uri (viewer's Share path).
        assertEquals(Intent.ACTION_SEND, intent.action)
        assertEquals("image/jpeg", intent.type)
        assertEquals(image, intent.getParcelableExtra(Intent.EXTRA_STREAM))

        // The chosen app must be able to read the content uri: the read grant flag is set and the uri
        // rides along as ClipData so the grant propagates on every API level.
        assertTrue(
            "FLAG_GRANT_READ_URI_PERMISSION must be set so the target can read the content uri",
            intent.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION != 0,
        )
        val clip = requireNotNull(intent.clipData) { "clipData must carry the shared uri" }
        assertEquals(1, clip.itemCount)
        assertEquals(image, clip.getItemAt(0).uri)
    }

    @Test
    fun multiItem_buildsActionSendMultiple_withEveryUriInClipData() {
        val intent = ShareIntents.buildSendIntent(listOf(image, other), "image/*")

        assertEquals(Intent.ACTION_SEND_MULTIPLE, intent.action)
        val stream = intent.getParcelableArrayListExtra<Uri>(Intent.EXTRA_STREAM)
        assertEquals(listOf(image, other), stream)
        assertNotEquals(0, intent.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION)
        // Every uri is attached as ClipData so the grant covers the whole EXTRA_STREAM list, not just the
        // primary data uri.
        assertEquals(2, requireNotNull(intent.clipData).itemCount)
    }
}
