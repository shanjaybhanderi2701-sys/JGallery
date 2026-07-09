package com.appblish.jgallery.core.storage.internal

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/** The pure half of create-album (spec §6): name validation, before any filesystem touch. */
class AlbumNamesTest {

    @Test
    fun `trims surrounding whitespace and accepts a normal name`() {
        val result = AlbumNames.validate("  Trip 2026  ")
        assertThat(result).isInstanceOf(AlbumNames.Result.Valid::class.java)
        assertThat((result as AlbumNames.Result.Valid).name).isEqualTo("Trip 2026")
    }

    @Test
    fun `an internal space is preserved (valid)`() {
        assertThat(AlbumNames.validate("Holiday 2026")).isInstanceOf(AlbumNames.Result.Valid::class.java)
    }

    @Test
    fun `blank or whitespace-only names are rejected`() {
        assertThat(AlbumNames.validate("")).isInstanceOf(AlbumNames.Result.Invalid::class.java)
        assertThat(AlbumNames.validate("   ")).isInstanceOf(AlbumNames.Result.Invalid::class.java)
    }

    @Test
    fun `dot and dot-dot are rejected so a name can't escape its parent`() {
        assertThat(AlbumNames.validate(".")).isInstanceOf(AlbumNames.Result.Invalid::class.java)
        assertThat(AlbumNames.validate("..")).isInstanceOf(AlbumNames.Result.Invalid::class.java)
    }

    @Test
    fun `path separators and illegal characters are rejected`() {
        val illegal = listOf("a/b", "a\\b", "a:b", "a*b", "a?b", "a\"b", "a<b", "a>b", "a|b")
        for (bad in illegal) {
            assertThat(AlbumNames.validate(bad)).isInstanceOf(AlbumNames.Result.Invalid::class.java)
        }
    }

    @Test
    fun `control characters are rejected`() {
        val tab = "a" + Char(9) + "b"
        val newline = "a" + Char(10) + "b"
        assertThat(AlbumNames.validate(tab)).isInstanceOf(AlbumNames.Result.Invalid::class.java)
        assertThat(AlbumNames.validate(newline)).isInstanceOf(AlbumNames.Result.Invalid::class.java)
    }

    @Test
    fun `names longer than the segment limit are rejected`() {
        assertThat(AlbumNames.validate("x".repeat(256))).isInstanceOf(AlbumNames.Result.Invalid::class.java)
        assertThat(AlbumNames.validate("x".repeat(255))).isInstanceOf(AlbumNames.Result.Valid::class.java)
    }
}
