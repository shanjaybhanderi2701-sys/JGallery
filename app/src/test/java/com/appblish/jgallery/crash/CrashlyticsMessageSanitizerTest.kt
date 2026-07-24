package com.appblish.jgallery.crash

import com.google.common.truth.Truth.assertThat
import java.io.IOException
import org.junit.After
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for [CrashlyticsMessageSanitizer] (APP-626 / APP-619 Finding 3).
 *
 * Runs on the JVM: the sanitizer is pure `java.lang` reflection + regex, and the JVM `Throwable`
 * carries the same `detailMessage` field the in-place scrub mutates on Android, so the on-device
 * behaviour is exercised here. The handler-chaining test saves/restores the process-wide default
 * uncaught-exception handler.
 */
class CrashlyticsMessageSanitizerTest {

    private var previousDefault: Thread.UncaughtExceptionHandler? = null

    @Before
    fun captureDefaultHandler() {
        previousDefault = Thread.getDefaultUncaughtExceptionHandler()
    }

    @After
    fun restoreDefaultHandler() {
        Thread.setDefaultUncaughtExceptionHandler(previousDefault)
    }

    // --- scrub(): paths -----------------------------------------------------

    @Test
    fun scrub_redactsSharedStorageAbsolutePath() {
        val msg = "open failed: EACCES (Permission denied): /storage/emulated/0/DCIM/private.jpg"
        val out = CrashlyticsMessageSanitizer.scrub(msg)
        assertThat(out).isEqualTo("open failed: EACCES (Permission denied): /<redacted-path>")
        // Exception "type"-carrying words are preserved; only the path is gone.
        assertThat(out).contains("EACCES")
        assertThat(out).doesNotContain("DCIM")
        assertThat(out).doesNotContain("private.jpg")
    }

    @Test
    fun scrub_redactsAllStorageRoots() {
        val roots = listOf(
            "/storage/emulated/0/Movies/clip.mp4",
            "/sdcard/Download/report.pdf",
            "/mnt/media_rw/ABCD-1234/a.png",
            "/data/user/0/com.appblish.jgallery/files/cache.bin",
        )
        for (path in roots) {
            val out = CrashlyticsMessageSanitizer.scrub("ENOENT: $path")
            assertThat(out).isEqualTo("ENOENT: /<redacted-path>")
        }
    }

    @Test
    fun scrub_stopsPathAtWhitespaceAndPunctuation() {
        val out = CrashlyticsMessageSanitizer.scrub("failed /storage/emulated/0/x.jpg, retrying")
        assertThat(out).isEqualTo("failed /<redacted-path>, retrying")
    }

    // --- scrub(): bare filenames -------------------------------------------

    @Test
    fun scrub_redactsBareMediaFilename() {
        val out = CrashlyticsMessageSanitizer.scrub("could not decode vacation.heic")
        assertThat(out).isEqualTo("could not decode <redacted-file>")
    }

    @Test
    fun scrub_redactsVaultkeySentinelFilename() {
        // APP-574: the .vaultkey extension is the canonical private-content sentinel.
        val out = CrashlyticsMessageSanitizer.scrub("missing private.vaultkey")
        assertThat(out).isEqualTo("missing <redacted-file>")
    }

    // --- scrub(): no-op cases ----------------------------------------------

    @Test
    fun scrub_leavesContentFreeMessagesUntouched() {
        for (msg in listOf(
            "item no longer exists",
            "copied, but the source could not be removed",
            "java.lang.IllegalStateException",
            null,
            "",
        )) {
            assertThat(CrashlyticsMessageSanitizer.scrub(msg)).isEqualTo(msg)
        }
    }

    @Test
    fun scrub_doesNotRedactVersionOrClassLikeTokens() {
        // Guard against over-redaction: dotted tokens without a file extension must survive.
        val msg = "OkHttp/4.12.0 failed at MediaStore.Images.Media query"
        assertThat(CrashlyticsMessageSanitizer.scrub(msg)).isEqualTo(msg)
    }

    // --- sanitizeChain(): type / frames / cause preserved -------------------

    @Test
    fun sanitizeChain_scrubsMessageButPreservesTypeAndStackFrames() {
        val original = IOException("EACCES: /storage/emulated/0/DCIM/secret.jpg")
        val framesBefore = original.stackTrace

        val result = CrashlyticsMessageSanitizer.sanitizeChain(original)

        // Same object, same type, same frames — only the message changed.
        assertThat(result).isSameInstanceAs(original)
        assertThat(result).isInstanceOf(IOException::class.java)
        assertThat(result.message).isEqualTo("EACCES: /<redacted-path>")
        assertThat(result.stackTrace).isEqualTo(framesBefore)
    }

    @Test
    fun sanitizeChain_scrubsCauseChain() {
        val cause = IOException("open failed on /sdcard/DCIM/leak.png")
        val top = RuntimeException("wrap of /storage/emulated/0/notes.txt", cause)

        CrashlyticsMessageSanitizer.sanitizeChain(top)

        assertThat(top.message).isEqualTo("wrap of /<redacted-path>")
        assertThat(top.cause?.message).isEqualTo("open failed on /<redacted-path>")
        assertThat(top.cause).isInstanceOf(IOException::class.java)
    }

    @Test
    fun sanitizeChain_scrubsSuppressed() {
        val a = IOException("a: /storage/emulated/0/a.jpg")
        val b = IOException("b: /storage/emulated/0/b.jpg")
        a.addSuppressed(b)

        CrashlyticsMessageSanitizer.sanitizeChain(a)

        assertThat(a.message).isEqualTo("a: /<redacted-path>")
        assertThat(a.suppressed.single().message).isEqualTo("b: /<redacted-path>")
    }

    @Test
    fun sanitizeChain_terminatesOnCauseCycle() {
        // Defensive: a hand-built cause cycle must not loop forever.
        val x = RuntimeException("x /storage/emulated/0/x.jpg")
        val y = RuntimeException("y /storage/emulated/0/y.jpg")
        x.initCause(y)
        y.initCause(x) // x -> y -> x

        CrashlyticsMessageSanitizer.sanitizeChain(x) // must terminate

        assertThat(x.message).isEqualTo("x /<redacted-path>")
        assertThat(y.message).isEqualTo("y /<redacted-path>")
    }

    // --- install(): chaining + delegation ----------------------------------

    @Test
    fun install_forwardsSanitizedThrowableToPreviousHandler() {
        var received: Throwable? = null
        Thread.setDefaultUncaughtExceptionHandler { _, t -> received = t }

        CrashlyticsMessageSanitizer.install()

        val handler = Thread.getDefaultUncaughtExceptionHandler()!!
        val thrown = IOException("EACCES: /storage/emulated/0/DCIM/private.jpg")
        handler.uncaughtException(Thread.currentThread(), thrown)

        // The delegate (stand-in for Crashlytics) saw the crash, sanitized, type/instance preserved.
        assertThat(received).isSameInstanceAs(thrown)
        assertThat(received).isInstanceOf(IOException::class.java)
        assertThat(received!!.message).isEqualTo("EACCES: /<redacted-path>")
    }

    @Test
    fun install_isIdempotent() {
        Thread.setDefaultUncaughtExceptionHandler { _, _ -> }
        CrashlyticsMessageSanitizer.install()
        val first = Thread.getDefaultUncaughtExceptionHandler()
        CrashlyticsMessageSanitizer.install()
        assertThat(Thread.getDefaultUncaughtExceptionHandler()).isSameInstanceAs(first)
    }

    @Test
    fun install_stillDeliversCrashWithNoPreviousHandler() {
        // No delegate installed: the handler must not throw when forwarding.
        Thread.setDefaultUncaughtExceptionHandler(null)
        CrashlyticsMessageSanitizer.install()
        val handler = Thread.getDefaultUncaughtExceptionHandler()!!
        // Should be a no-op (null delegate) rather than an NPE.
        handler.uncaughtException(Thread.currentThread(), IOException("/storage/emulated/0/x.jpg"))
    }
}
