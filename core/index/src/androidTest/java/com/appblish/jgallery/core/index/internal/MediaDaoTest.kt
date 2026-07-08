package com.appblish.jgallery.core.index.internal

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Exercises the real Room DAO on-device (in-memory DB). Covers the round-trip plus the albums
 * aggregate, whose cover-is-newest-item behavior relies on SQLite's single-MAX bare-column rule and
 * is not reproducible in a plain JVM test.
 */
@RunWith(AndroidJUnit4::class)
class MediaDaoTest {

    private lateinit var db: MediaIndexDatabase
    private lateinit var dao: MediaDao

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, MediaIndexDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        dao = db.mediaDao()
    }

    @After
    fun tearDown() = db.close()

    @Test
    fun upsertThenSignaturesAndCount() = runBlocking {
        dao.upsert(listOf(entity("1"), entity("2")))

        assertEquals(2, dao.count())
        assertEquals(setOf("1", "2"), dao.signatures().map { it.id }.toSet())

        // Upsert replaces on primary key rather than duplicating.
        dao.upsert(listOf(entity("1", size = 999L)))
        assertEquals(2, dao.count())
        assertEquals(999L, dao.signatures().first { it.id == "1" }.sizeBytes)
    }

    @Test
    fun albumsCoverIsNewestItemAndOrderedByRecency() = runBlocking {
        dao.upsert(
            listOf(
                entity("1", bucket = "a", taken = 100L),
                entity("2", bucket = "a", taken = 300L), // newest in bucket a
                entity("3", bucket = "b", taken = 50L),
            ),
        )

        val albums = dao.observeAlbums().first()

        assertEquals(listOf("a", "b"), albums.map { it.bucketId }) // newest bucket first
        val bucketA = albums.first { it.bucketId == "a" }
        assertEquals(2, bucketA.itemCount)
        assertEquals("2", bucketA.coverId) // cover = newest item's id
        assertEquals(300L, bucketA.newestItemMillis)
    }

    @Test
    fun deleteByIdsRemovesOnlyRequestedRows() = runBlocking {
        dao.upsert(listOf(entity("1"), entity("2"), entity("3")))

        dao.deleteByIds(listOf("2", "3"))

        assertEquals(1, dao.count())
        assertEquals(setOf("1"), dao.signatures().map { it.id }.toSet())
    }

    private fun entity(
        id: String,
        bucket: String = "bucket",
        taken: Long = id.toLong(),
        size: Long = 10L,
    ) = MediaEntity(
        id = id,
        displayName = "item-$id",
        type = "IMAGE",
        bucketId = bucket,
        bucketName = bucket.uppercase(),
        dateTakenMillis = taken,
        dateModifiedMillis = 1L,
        sizeBytes = size,
        width = 0,
        height = 0,
        durationMillis = 0L,
        mimeType = "image/jpeg",
    )
}
