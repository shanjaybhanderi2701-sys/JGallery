package com.appblish.jgallery.core.ui.format

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.appblish.jgallery.core.ui.theme.JGalleryTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Instrumented proof of the §8 graceful-degradation contract (APP-364, DoD §11): unsupported,
 * zero-byte and undecodable files render the D3 placeholder in the grid and the viewer, never crash,
 * and don't disturb scrolling. The pure classifier branches are covered separately in
 * `MediaFormatSupportTest` (JVM); this exercises the Compose rendering + Coil fallback path.
 */
@RunWith(AndroidJUnit4::class)
class MediaDecodeBoxTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Composable
    private fun Tile(name: String, mime: String, size: Long, model: Any? = Any()) {
        MediaDecodeBox(
            model = model,
            displayName = name,
            mimeType = mime,
            sizeBytes = size,
            contentDescription = name,
            modifier = Modifier.aspectRatio(1f),
            placeholder = { MediaDecodeTilePlaceholder(it) },
        )
    }

    @Test fun unknownDocumentExtension_showsUnsupportedTile_withoutDecoding() {
        composeRule.setContent {
            JGalleryTheme { Tile(name = "report.pdf", mime = "application/pdf", size = 8_192L) }
        }
        // Classified up front → placeholder, never a decode attempt.
        composeRule.onNodeWithTag(MediaPlaceholderTags.UNSUPPORTED_TILE).assertIsDisplayed()
    }

    @Test fun zeroByteFile_showsCorruptTile() {
        composeRule.setContent {
            JGalleryTheme { Tile(name = "photo.jpg", mime = "image/jpeg", size = 0L) }
        }
        composeRule.onNodeWithTag(MediaPlaceholderTags.CORRUPT_TILE).assertIsDisplayed()
    }

    @Test fun renderableFileThatFailsToDecode_fallsBackToCorruptTile_noCrash() {
        // A would-be-renderable image (preClassify = null) whose model Coil cannot resolve → Coil emits
        // an Error state, which the hook promotes to the shared Corrupt placeholder rather than crashing.
        composeRule.setContent {
            JGalleryTheme { Tile(name = "shot.jpg", mime = "image/jpeg", size = 4_096L, model = Any()) }
        }
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithTag(MediaPlaceholderTags.CORRUPT_TILE)
                .fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag(MediaPlaceholderTags.CORRUPT_TILE).assertIsDisplayed()
    }

    @Test fun viewerCard_rendersForUnsupported_withOpenWithPrimary() {
        composeRule.setContent {
            JGalleryTheme {
                ViewerUnsupportedCard(
                    state = MediaDecodeState.Unsupported("psd"),
                    fileName = "layers.psd",
                    formatLabel = "PSD",
                    sizeLabel = "12 MB",
                    primaryLabel = "Open with",
                    onPrimary = {},
                    onInfo = {},
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
        composeRule.onNodeWithTag(MediaPlaceholderTags.VIEWER_CARD).assertIsDisplayed()
        composeRule.onNodeWithText("Can't preview this file").assertIsDisplayed()
        composeRule.onNodeWithText("Open with").assertIsDisplayed()
    }

    @Test fun viewerCard_rendersForCorrupt_withDamagedHeadline() {
        composeRule.setContent {
            JGalleryTheme {
                ViewerUnsupportedCard(
                    state = MediaDecodeState.Corrupt("jpg"),
                    fileName = "broken.jpg",
                    formatLabel = "JPG",
                    sizeLabel = "0 B",
                    primaryLabel = "Delete",
                    onPrimary = {},
                    onInfo = {},
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
        composeRule.onNodeWithTag(MediaPlaceholderTags.VIEWER_CARD).assertIsDisplayed()
        composeRule.onNodeWithText("This file appears to be damaged").assertIsDisplayed()
    }

    @Test fun mixedGrid_scrollsPastPlaceholders_withoutCrashing() {
        val names = List(60) { i ->
            when (i % 3) {
                0 -> Triple("good_$i.jpg", "image/jpeg", 5_000L)   // decodes-or-falls-back
                1 -> Triple("doc_$i.pdf", "application/pdf", 5_000L) // unsupported
                else -> Triple("empty_$i.jpg", "image/jpeg", 0L)     // corrupt
            }
        }
        composeRule.setContent {
            JGalleryTheme {
                LazyColumn(Modifier.fillMaxSize().testTag("decode_grid")) {
                    items(names) { (name, mime, size) ->
                        Column(Modifier.fillMaxWidth().height(120.dp)) {
                            Tile(name = name, mime = mime, size = size)
                            Text(name)
                        }
                    }
                }
            }
        }
        // Scrolling across a run of degraded tiles must not throw — the whole point of §8.
        composeRule.onNodeWithTag("decode_grid").performScrollToIndex(names.lastIndex)
        composeRule.onNodeWithText(names.last().first).assertIsDisplayed()
    }
}
