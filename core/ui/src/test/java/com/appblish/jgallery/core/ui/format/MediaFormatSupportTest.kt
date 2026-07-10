package com.appblish.jgallery.core.ui.format

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * The decode-free classifier (spec §8, APP-364) is the substance of graceful degradation: it decides
 * from cheap index metadata alone whether a file is renderable, a healthy-but-unsupported format, or
 * unreadable — WITHOUT probing bytes (which §1 forbids at index time). These cover the branches a
 * device test would be flaky about, in pure JVM.
 */
class MediaFormatSupportTest {

    @Test fun `extension is lower-cased and dot-stripped`() {
        assertThat(MediaFormatSupport.extensionOf("Holiday.JPG")).isEqualTo("jpg")
        assertThat(MediaFormatSupport.extensionOf("archive.tar.gz")).isEqualTo("gz")
        assertThat(MediaFormatSupport.extensionOf("no_extension")).isEqualTo("")
    }

    @Test fun `zero-byte file is corrupt regardless of a healthy-looking name`() {
        val state = MediaFormatSupport.preClassify("photo.jpg", "image/jpeg", sizeBytes = 0L)
        assertThat(state).isEqualTo(MediaDecodeState.Corrupt("jpg"))
    }

    @Test fun `negative size is treated as corrupt`() {
        val state = MediaFormatSupport.preClassify("clip.mp4", "video/mp4", sizeBytes = -1L)
        assertThat(state).isInstanceOf(MediaDecodeState.Corrupt::class.java)
    }

    @Test fun `known document container is unsupported, not corrupt`() {
        val state = MediaFormatSupport.preClassify("resume.pdf", "application/pdf", sizeBytes = 4_096L)
        assertThat(state).isEqualTo(MediaDecodeState.Unsupported("pdf"))
    }

    @Test fun `non-media mime with a real subtype is unsupported`() {
        val state = MediaFormatSupport.preClassify("mystery.dat", "application/zip", sizeBytes = 10L)
        assertThat(state).isEqualTo(MediaDecodeState.Unsupported("dat"))
    }

    @Test fun `renderable image defers to the decoder (null = try to decode)`() {
        assertThat(MediaFormatSupport.preClassify("photo.jpg", "image/jpeg", 2_000L)).isNull()
        assertThat(MediaFormatSupport.preClassify("clip.mp4", "video/mp4", 9_000L)).isNull()
    }

    @Test fun `unknown extension with unknown mime still gets an honest decode attempt`() {
        // We must NOT hide a possibly-decodable file behind a placeholder up front — only fall back
        // to Corrupt if the decoder actually errors at render time.
        assertThat(MediaFormatSupport.preClassify("frame.heic", "", 5_000L)).isNull()
        assertThat(MediaFormatSupport.preClassify("frame.xyz", "application/octet-stream", 5_000L)).isNull()
    }

    @Test fun `placeholder states report the extension for the tile label`() {
        assertThat(MediaDecodeState.Unsupported("psd").isPlaceholder).isTrue()
        assertThat(MediaDecodeState.Corrupt("raw").isPlaceholder).isTrue()
        assertThat(MediaDecodeState.Rendered.isPlaceholder).isFalse()
        assertThat(MediaDecodeState.BestEffort("dng", "embedded JPEG").isPlaceholder).isFalse()
    }
}
