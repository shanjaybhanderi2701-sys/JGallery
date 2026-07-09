package com.appblish.jgallery.core.playback.internal

import com.google.common.truth.Truth.assertThat
import java.io.ByteArrayInputStream
import java.io.EOFException
import java.io.FilterInputStream
import java.io.InputStream
import org.junit.Assert.assertThrows
import org.junit.Test

class StreamIoTest {

    // --- StreamMath.bytesRemaining: the window Media3's extractor is told it can read ---

    @Test
    fun `explicit spec length wins over index size`() {
        assertThat(StreamMath.bytesRemaining(position = 100, length = 50, sizeBytes = 9_999))
            .isEqualTo(50)
    }

    @Test
    fun `unset length derives remaining from index size and position`() {
        assertThat(StreamMath.bytesRemaining(position = 300, length = StreamMath.LENGTH_UNSET, sizeBytes = 1_000))
            .isEqualTo(700)
    }

    @Test
    fun `position past stale index size clamps to zero instead of going negative`() {
        assertThat(StreamMath.bytesRemaining(position = 1_500, length = StreamMath.LENGTH_UNSET, sizeBytes = 1_000))
            .isEqualTo(0)
    }

    @Test
    fun `unknown index size stays unset`() {
        assertThat(StreamMath.bytesRemaining(position = 0, length = StreamMath.LENGTH_UNSET, sizeBytes = 0))
            .isEqualTo(StreamMath.LENGTH_UNSET)
    }

    // --- StreamMath.clampReadLength / afterRead: reads never overrun the window ---

    @Test
    fun `read is clamped to the remaining window`() {
        assertThat(StreamMath.clampReadLength(requested = 8_192, bytesRemaining = 10)).isEqualTo(10)
        assertThat(StreamMath.clampReadLength(requested = 8_192, bytesRemaining = StreamMath.LENGTH_UNSET))
            .isEqualTo(8_192)
    }

    @Test
    fun `window shrinks by bytes read and unset stays unset`() {
        assertThat(StreamMath.afterRead(bytesRemaining = 100, read = 40)).isEqualTo(60)
        assertThat(StreamMath.afterRead(StreamMath.LENGTH_UNSET, read = 40))
            .isEqualTo(StreamMath.LENGTH_UNSET)
    }

    // --- skipFully: seeking = reopen + skip; must make progress even on skip()-shy streams ---

    @Test
    fun `skipFully advances exactly to the requested position`() {
        val stream = ByteArrayInputStream(ByteArray(100) { it.toByte() })
        stream.skipFully(42)
        assertThat(stream.read()).isEqualTo(42)
    }

    @Test
    fun `skipFully drains via read when skip makes no progress`() {
        val stream = noSkipStream(ByteArray(100) { it.toByte() })
        stream.skipFully(64)
        assertThat(stream.read()).isEqualTo(64)
    }

    @Test
    fun `skipFully past EOF throws EOFException`() {
        val stream = ByteArrayInputStream(ByteArray(10))
        assertThrows(EOFException::class.java) { stream.skipFully(11) }
    }

    @Test
    fun `skipFully zero is a no-op`() {
        val stream = ByteArrayInputStream(byteArrayOf(7))
        stream.skipFully(0)
        assertThat(stream.read()).isEqualTo(7)
    }

    /** A stream whose skip() always reports no progress, like some content-provider streams. */
    private fun noSkipStream(bytes: ByteArray): InputStream =
        object : FilterInputStream(ByteArrayInputStream(bytes)) {
            override fun skip(n: Long): Long = 0
        }
}
