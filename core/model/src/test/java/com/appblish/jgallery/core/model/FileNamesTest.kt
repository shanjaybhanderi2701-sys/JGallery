package com.appblish.jgallery.core.model

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/** The shared rename/create name policy (APP-590): validation + media extension preservation. */
class FileNamesTest {

    private fun rename(raw: String, current: String) = FileNames.normalizeRename(raw, current)

    private fun validName(result: FileNames.Result): String {
        assertThat(result).isInstanceOf(FileNames.Result.Valid::class.java)
        return (result as FileNames.Result.Valid).name
    }

    // --- validate (album names + base names) ---

    @Test
    fun `trims and accepts a normal name`() {
        assertThat(validName(FileNames.validate("  Trip 2026  "))).isEqualTo("Trip 2026")
    }

    @Test
    fun `rejects blank, dot, control chars, illegal chars and over-length`() {
        assertThat(FileNames.validate("")).isInstanceOf(FileNames.Result.Invalid::class.java)
        assertThat(FileNames.validate("   ")).isInstanceOf(FileNames.Result.Invalid::class.java)
        assertThat(FileNames.validate(".")).isInstanceOf(FileNames.Result.Invalid::class.java)
        assertThat(FileNames.validate("..")).isInstanceOf(FileNames.Result.Invalid::class.java)
        assertThat(FileNames.validate("a\tb")).isInstanceOf(FileNames.Result.Invalid::class.java)
        for (bad in listOf("a/b", "a\\b", "a:b", "a*b", "a?b", "a\"b", "a<b", "a>b", "a|b")) {
            assertThat(FileNames.validate(bad)).isInstanceOf(FileNames.Result.Invalid::class.java)
        }
        assertThat(FileNames.validate("x".repeat(256))).isInstanceOf(FileNames.Result.Invalid::class.java)
        assertThat(FileNames.validate("x".repeat(255))).isInstanceOf(FileNames.Result.Valid::class.java)
    }

    @Test
    fun `albumNameError is null for a good name and a message for a bad one`() {
        assertThat(FileNames.albumNameError("Holiday")).isNull()
        assertThat(FileNames.albumNameError("a/b")).isNotNull()
    }

    // --- media rename: extension preservation ---

    @Test
    fun `a base-only rename keeps the original extension`() {
        assertThat(validName(rename("Sunset", "IMG_0001.jpg"))).isEqualTo("Sunset.jpg")
    }

    @Test
    fun `retyping the original extension does not double it (case-insensitive)`() {
        assertThat(validName(rename("Sunset.jpg", "IMG_0001.jpg"))).isEqualTo("Sunset.jpg")
        assertThat(validName(rename("Sunset.JPG", "IMG_0001.jpg"))).isEqualTo("Sunset.jpg")
    }

    @Test
    fun `a different typed extension can never replace the real one`() {
        // The user typing ".png" over a JPEG must not corrupt the file's type; the original wins.
        assertThat(validName(rename("Sunset.png", "IMG_0001.jpg"))).isEqualTo("Sunset.png.jpg")
    }

    @Test
    fun `an extensionless file renames without inventing an extension`() {
        assertThat(validName(rename("notes", "README"))).isEqualTo("notes")
    }

    @Test
    fun `multi-dot names preserve only the last extension`() {
        assertThat(validName(rename("clip", "video.tar.gz"))).isEqualTo("clip.gz")
    }

    @Test
    fun `a blank or illegal base is rejected even with a valid extension source`() {
        assertThat(rename("   ", "IMG.jpg")).isInstanceOf(FileNames.Result.Invalid::class.java)
        assertThat(rename("a/b", "IMG.jpg")).isInstanceOf(FileNames.Result.Invalid::class.java)
        assertThat(FileNames.renameError("a?b", "IMG.jpg")).isNotNull()
        assertThat(FileNames.renameError("Fine", "IMG.jpg")).isNull()
    }

    @Test
    fun `the assembled name with extension is length-bounded`() {
        val result = rename("x".repeat(255), "IMG.jpg") // base ok alone, but base + ".jpg" > 255
        assertThat(result).isInstanceOf(FileNames.Result.Invalid::class.java)
    }
}
