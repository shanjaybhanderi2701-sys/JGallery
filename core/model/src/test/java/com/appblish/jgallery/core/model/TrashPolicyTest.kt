package com.appblish.jgallery.core.model

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/** The Recycle Bin retention arithmetic (spec §7.5) — shared by the storage purge and the UI badge. */
class TrashPolicyTest {

    private val day = 24L * 60L * 60L * 1000L

    @Test
    fun `a freshly-trashed item reads 29 days left (floored, honest full-days)`() {
        // Trashed 1ms ago: just under 30 full days remain, so it floors to 29 — matching W2-09.
        assertThat(TrashPolicy.daysLeft(trashedAtMillis = 1_000L, nowMillis = 1_000L + 1)).isEqualTo(29)
    }

    @Test
    fun `days left counts down and never goes negative`() {
        val trashedAt = 0L
        assertThat(TrashPolicy.daysLeft(trashedAt, nowMillis = 25L * day)).isEqualTo(5)
        assertThat(TrashPolicy.daysLeft(trashedAt, nowMillis = 29L * day + day / 2)).isEqualTo(0)
        assertThat(TrashPolicy.daysLeft(trashedAt, nowMillis = 40L * day)).isEqualTo(0)
    }

    @Test
    fun `isExpired flips exactly at the 30-day boundary`() {
        val trashedAt = 100L
        assertThat(TrashPolicy.isExpired(trashedAt, nowMillis = trashedAt + TrashPolicy.RETENTION_MILLIS - 1))
            .isFalse()
        assertThat(TrashPolicy.isExpired(trashedAt, nowMillis = trashedAt + TrashPolicy.RETENTION_MILLIS))
            .isTrue()
    }

    @Test
    fun `isExpiringSoon is true only within the amber warning band and not once expired`() {
        val trashedAt = 0L
        // 6 days left -> not yet warning.
        assertThat(TrashPolicy.isExpiringSoon(trashedAt, nowMillis = 24L * day)).isFalse()
        // 5 days left -> warning band.
        assertThat(TrashPolicy.isExpiringSoon(trashedAt, nowMillis = 25L * day)).isTrue()
        // 0 days left but still held -> warning.
        assertThat(TrashPolicy.isExpiringSoon(trashedAt, nowMillis = 30L * day - 1)).isTrue()
        // Past retention -> expired, not "soon".
        assertThat(TrashPolicy.isExpiringSoon(trashedAt, nowMillis = 31L * day)).isFalse()
    }
}
