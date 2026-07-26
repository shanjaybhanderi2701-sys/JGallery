package com.appblish.jgallery.core.model

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * Redaction-coverage tests for [PathRedactor] (APP-628 — the repro/coverage suite the Security
 * review asks for). The invariant under test: after redaction, no user path/URI/name survives,
 * while the errno/reason text is preserved for diagnostics.
 */
class PathRedactorTest {

    @Test
    fun `redacts an absolute path but keeps the reason`() {
        assertThat(PathRedactor.redact("Cannot rename: /storage/emulated/0/DCIM/holiday.jpg"))
            .isEqualTo("Cannot rename: ${PathRedactor.REDACTED_PATH}")
    }

    @Test
    fun `redacts a bare absolute path with no surrounding reason`() {
        assertThat(PathRedactor.redact("/storage/emulated/0/Download/tax-return-2025.pdf"))
            .isEqualTo(PathRedactor.REDACTED_PATH)
    }

    // The finding's motivating example: APP-574 `EACCES … .vaultkey`.
    @Test
    fun `security repro - EACCES message loses the path but keeps the errno`() {
        val raw = "open failed: EACCES (Permission denied): /data/user/0/com.appblish.jgallery/files/.vaultkey"
        val redacted = PathRedactor.redact(raw)

        assertThat(redacted).doesNotContain(".vaultkey")
        assertThat(redacted).doesNotContain("/data/user")
        assertThat(redacted).contains("EACCES")
        assertThat(redacted).contains("Permission denied")
        assertThat(redacted)
            .isEqualTo("open failed: EACCES (Permission denied): ${PathRedactor.REDACTED_PATH}")
    }

    // The JGallery write path throws with a SAF/content URI (MediaStoreStorageOps sinks).
    @Test
    fun `redacts content and file and http URIs including an encoded SAF path`() {
        assertThat(
            PathRedactor.redact(
                "Unable to open destination stream for " +
                    "content://com.android.externalstorage.documents/tree/primary%3ADCIM%2Fsecret.jpg",
            ),
        ).isEqualTo("Unable to open destination stream for ${PathRedactor.REDACTED_URI}")

        assertThat(PathRedactor.redact("Cannot open file:///storage/emulated/0/Movies/private.mp4"))
            .isEqualTo("Cannot open ${PathRedactor.REDACTED_URI}")
    }

    @Test
    fun `redacts several paths in one message`() {
        assertThat(
            PathRedactor.redact("copy /storage/emulated/0/a/b.txt to /storage/emulated/0/c/d.txt failed"),
        ).isEqualTo("copy ${PathRedactor.REDACTED_PATH} to ${PathRedactor.REDACTED_PATH} failed")
    }

    @Test
    fun `preserves non-path text - MIME types, prose, and numbers`() {
        // A single-slash token is not a path: MIME types and prose must survive verbatim.
        assertThat(PathRedactor.redact("Unsupported type application/octet-stream"))
            .isEqualTo("Unsupported type application/octet-stream")
        assertThat(PathRedactor.redact("retry now and/or cancel")).isEqualTo("retry now and/or cancel")
        assertThat(PathRedactor.redact("failed after 3 attempts (code 13)"))
            .isEqualTo("failed after 3 attempts (code 13)")
    }

    @Test
    fun `redactName keeps extension as a type hint but drops the stem`() {
        assertThat(PathRedactor.redactName("holiday.jpg")).isEqualTo("${PathRedactor.REDACTED_PATH}.jpg")
        assertThat(PathRedactor.redactName("Bank Statement.pdf")).isEqualTo("${PathRedactor.REDACTED_PATH}.pdf")
    }

    @Test
    fun `redactName fully drops names with no usable extension and relative bucket paths`() {
        assertThat(PathRedactor.redactName("Secret Folder")).isEqualTo(PathRedactor.REDACTED_PATH)
        assertThat(PathRedactor.redactName(".hidden")).isEqualTo(PathRedactor.REDACTED_PATH)
        assertThat(PathRedactor.redactName("archive.")).isEqualTo(PathRedactor.REDACTED_PATH)
        // A relative album/bucket path (no leading slash) that redact() would NOT touch is fully
        // dropped by redactName — this is why the MediaStore insert throw site uses redactName.
        assertThat(PathRedactor.redactName("Pictures/My Private Album")).isEqualTo(PathRedactor.REDACTED_PATH)
        assertThat(PathRedactor.redactName(null)).isEqualTo(PathRedactor.REDACTED_PATH)
        assertThat(PathRedactor.redactName("")).isEqualTo(PathRedactor.REDACTED_PATH)
    }

    @Test
    fun `redactOrNull passes through null and empty`() {
        assertThat(PathRedactor.redactOrNull(null)).isNull()
        assertThat(PathRedactor.redactOrNull("")).isEqualTo("")
    }
}
