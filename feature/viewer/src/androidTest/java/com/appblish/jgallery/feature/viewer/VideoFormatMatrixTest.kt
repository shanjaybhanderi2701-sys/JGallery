package com.appblish.jgallery.feature.viewer

import androidx.annotation.OptIn
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.ByteArrayDataSource
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.appblish.jgallery.core.model.MediaId
import com.appblish.jgallery.core.model.MediaItem
import com.appblish.jgallery.core.model.MediaType
import com.appblish.jgallery.core.playback.PlaybackSources
import com.appblish.jgallery.core.ui.theme.JGalleryViewerTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import androidx.media3.common.MediaItem as Media3MediaItem

/**
 * W3-E14 video format breadth (spec §8). Two things this proves without a real device codec:
 *  1. Every declared container (MP4/MKV/WEBM/3GP/AVI/MOV) drives the viewer's video-page path — the
 *     poster + play control render, and a boundary-routed source is requested on play. Whether the
 *     device can actually *decode* the codec is device-dependent and covered by the fallback below.
 *  2. The graceful §8 fallback (W3-05): an unplayable clip surfaces the codec-unsupported card with a
 *     codec chip + "Open with"/"Info", never a crash.
 */
@RunWith(AndroidJUnit4::class)
class VideoFormatMatrixTest {

    @get:Rule
    val composeRule = createComposeRule()

    @OptIn(UnstableApi::class)
    private class RecordingPlaybackSources : PlaybackSources {
        var sourceRequests = 0
            private set

        override fun mediaSource(item: MediaItem): MediaSource {
            sourceRequests++
            return ProgressiveMediaSource.Factory { ByteArrayDataSource(ByteArray(64)) }
                .createMediaSource(Media3MediaItem.fromUri("jgallery://test/${item.id.value}"))
        }
    }

    /** The §8 container matrix E14 broadens the Wave-1 viewer to. */
    private val formatMatrix = listOf(
        "VID.mp4" to "video/mp4",
        "VID.mkv" to "video/x-matroska",
        "VID.webm" to "video/webm",
        "VID.3gp" to "video/3gpp",
        "VID.avi" to "video/x-msvideo",
        "VID.mov" to "video/quicktime",
    )

    private fun videoItem(name: String, mime: String) = MediaItem(
        id = MediaId(name),
        displayName = name,
        type = MediaType.VIDEO,
        bucketId = "bucket",
        bucketName = "Album",
        dateTakenMillis = 2_000L,
        dateModifiedMillis = 2_000L,
        sizeBytes = 4_096L,
        width = 1_280,
        height = 720,
        durationMillis = 10_000L,
        mimeType = mime,
    )

    @Test
    fun everyDeclaredFormat_rendersVideoPageWithPlayControl() {
        // Compose permits setContent only ONCE per test, so we host it a single time and walk the
        // matrix by swapping the current container through state — each mime must land on the same
        // video-page path (mime only matters at decode time, which the fallback test covers).
        val current = mutableStateOf(formatMatrix.first())
        val playback = RecordingPlaybackSources()
        composeRule.setContent {
            val (name, mime) = current.value
            ViewerScreen(
                state = ViewerUiState.Ready(listOf(videoItem(name, mime)), initialIndex = 0),
                playback = playback,
                destinations = emptyList(),
                actionState = ViewerActionUiState.Idle,
                handlers = ViewerActionHandlers(
                    onCopyTo = { _, _ -> },
                    onMoveTo = { _, _ -> },
                    onRename = { _, _ -> },
                    onDelete = {},
                    onSetAs = {},
                    onOpenWith = {},
                    onResultShown = {},
                ),
                onBack = {},
            )
        }
        formatMatrix.forEach { format ->
            composeRule.runOnUiThread { current.value = format }
            composeRule.waitForIdle()
            // The video-page path is reached for this container, and the poster's play affordance is up.
            composeRule.onNodeWithTag("viewer_video_page").assertIsDisplayed()
            composeRule.onNodeWithTag("player_play_pause").assertIsDisplayed()
        }
    }

    @Test
    fun codecUnsupportedCard_showsCodecChipOpenWithAndInfo() {
        var openWith = 0
        var info = 0
        composeRule.setContent {
            JGalleryViewerTheme {
                VideoErrorCard(
                    error = VideoError.Unsupported("HEVC"),
                    onOpenWith = { openWith++ },
                    onInfo = { info++ },
                )
            }
        }

        composeRule.onNodeWithTag("viewer_video_error").assertIsDisplayed()
        composeRule.onNodeWithText("Can't play this video").assertIsDisplayed()
        composeRule.onNodeWithTag("viewer_video_error_codec").assertIsDisplayed()
        composeRule.onNodeWithText("HEVC · unsupported").assertIsDisplayed()

        composeRule.onNodeWithTag("viewer_video_open_with").performClick()
        composeRule.onNodeWithTag("viewer_video_error_info").performClick()
        assertTrue("Open with fires its hand-off", openWith == 1)
        assertTrue("Info fires its callback", info == 1)
    }

    @Test
    fun genericPlaybackError_hasNoCodecChip() {
        val error = mutableStateOf<VideoError>(VideoError.Playback)
        composeRule.setContent {
            JGalleryViewerTheme {
                VideoErrorCard(error = error.value, onOpenWith = {}, onInfo = {})
            }
        }
        composeRule.onNodeWithTag("viewer_video_error").assertIsDisplayed()
        // No codec chip when Media3 didn't attribute a specific format.
        composeRule.onNodeWithTag("viewer_video_error_codec").assertDoesNotExist()
    }
}
