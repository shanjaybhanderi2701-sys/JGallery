package com.appblish.jgallery.benchmark

import android.content.ContentResolver
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import android.os.Build
import androidx.heifwriter.HeifWriter
import android.provider.MediaStore
import android.util.Log
import com.appblish.jgallery.core.model.MediaId
import com.appblish.jgallery.core.model.MediaItem
import com.appblish.jgallery.core.model.MediaType
import java.io.ByteArrayOutputStream
import java.io.File

/**
 * Seeds a corpus of REAL, decodable image files into MediaStore so the [PhotosScrollBenchmark] fling
 * exercises the true thumbnail decode/IO path — the whole point of APP-390.
 *
 * ## Why this exists
 * The original APP-342 harness rendered [MediaItem]s whose ids resolved to nothing (`bench-$i`), so
 * every grid tile MISSED the thumbnail fetcher and drew a placeholder — zero decode, zero IO. That
 * measured pure list/layout machinery and produced a false ~1.9% jank "pass". A real scroll on a
 * device with 10k photos decodes thumbnails on the fly (`ContentResolver.loadThumbnail` → JPEG
 * re-encode → Coil decode, see MediaStoreStorageAccess), which is where the actual jank lives.
 *
 * This seeder writes ≥[DEFAULT_CORPUS_SIZE] real image files (varied JPEG / PNG / HEIC / WebP across
 * resolutions up to full camera-res) into `Pictures/[BUCKET_NAME]` via MediaStore, then queries them
 * back so the activity renders tiles carrying REAL numeric row-ids. Those ids resolve through
 * `MediaStoreStorageAccess.idToUri` → `loadThumbnail`, so a fling now performs genuine decode/IO.
 *
 * ## Cost discipline
 * We do NOT generate 10k distinct 12-megapixel bitmaps (minutes of CPU + gigabytes of RAM churn).
 * Instead we pre-encode a small [palette] of distinct real images (one `byte[]` per spec) and write
 * every corpus file from a cycling palette entry. Decode cost is unaffected: Coil / the thumbnail
 * cache key is per-`MediaId`, so each of the 10k tiles decodes its own file independently even when
 * two files hold identical bytes. Palette images use a gradient + noise pattern so they carry real
 * high-frequency content (they don't compress away to a trivial decode).
 *
 * Seeding is idempotent: if the bucket already holds ≥ the requested count we skip generation and
 * just re-query, so only the first benchmark iteration pays the (unmeasured, setup-time) seed cost.
 *
 * Benchmark build variant ONLY — never compiled into a shipped debug/release APK.
 */
object BenchmarkCorpusSeeder {

    const val TAG = "JGalleryBench"

    /** Bench media lives in its own bucket so seeding/idempotency never touches real device photos. */
    const val BUCKET_NAME = "JGalleryBench"
    private const val RELATIVE_DIR = "Pictures/$BUCKET_NAME"

    /**
     * The ONE selection every count/query/delete uses — strictly scoped to files INSIDE the bench
     * directory (`Pictures/JGalleryBench/…`). The trailing `/%` (not a bare `%`) is deliberate: it can
     * only ever match rows under this exact dir, never a real sibling folder that merely shares the
     * prefix (e.g. `Pictures/JGalleryBenchmark`). This single source of truth is what makes cleanup
     * provably unable to touch real user media — see [cleanup].
     */
    private const val BENCH_SELECTION = "${MediaStore.Images.Media.RELATIVE_PATH} LIKE ?"
    private val BENCH_SELECTION_ARGS = arrayOf("$RELATIVE_DIR/%")

    /** DoD floor: the corpus must be at least 10,000 real files (APP-386 plan §Wave R0). */
    const val DEFAULT_CORPUS_SIZE = 10_000

    /** Encoded formats we emit. HEIC is best-effort — the encoder is absent on some emulators. */
    private enum class Fmt(val mime: String, val ext: String) {
        JPEG("image/jpeg", "jpg"),
        PNG("image/png", "png"),
        HEIC("image/heic", "heic"),
        WEBP("image/webp", "webp"),
    }

    /** One palette recipe: pixel dimensions + encoded format. */
    private data class Spec(val width: Int, val height: Int, val fmt: Fmt)

    /**
     * Varied resolutions and formats, weighted toward large camera-res so a fling decodes
     * non-trivial images (not toy 256px tiles). Deliberately includes portrait + landscape, a few
     * PNGs (heavier to decode than JPEG), and HEIC (the modern default camera format). Aggressive
     * JPEG/HEIC quality keeps the on-disk bytes modest while the DECODED pixel count stays large —
     * decode cost tracks megapixels, not file size, which is exactly the signal we want.
     */
    private val PALETTE: List<Spec> = listOf(
        Spec(4032, 3024, Fmt.JPEG), // 12MP camera landscape
        Spec(3024, 4032, Fmt.JPEG), // 12MP camera portrait
        Spec(4000, 3000, Fmt.HEIC), // 12MP HEIC (best-effort)
        Spec(3264, 2448, Fmt.JPEG), // 8MP
        Spec(1600, 1200, Fmt.PNG), //  2MP PNG (lossless → heavy decode, bounded on-disk size)
        Spec(2560, 1440, Fmt.WEBP), // QHD WebP
        Spec(1920, 1080, Fmt.JPEG), // FHD landscape
        Spec(1080, 1920, Fmt.JPEG), // FHD portrait
        Spec(1600, 1200, Fmt.JPEG), // legacy 2MP
        Spec(1280, 960, Fmt.PNG), //   small PNG
        Spec(2988, 5312, Fmt.HEIC), // tall HEIC (best-effort)
        Spec(3456, 4608, Fmt.JPEG), // 16MP portrait
    )

    /** Base capture time; corpus is spread backwards over days so the timeline builds date headers. */
    private const val BASE_TAKEN_MILLIS = 1_700_000_000_000L // 2023-11-14T22:13:20Z
    private const val DAY_MILLIS = 24L * 60 * 60 * 1_000
    private const val ITEMS_PER_DAY = 30

    private const val JPEG_QUALITY = 72
    private const val HEIC_QUALITY = 80
    private const val WEBP_QUALITY = 75

    /**
     * Ensure the corpus exists (seeding if needed) and return the seeded [MediaItem]s ordered
     * newest-first, exactly as the Photos grid would receive them. Runs on the caller's thread —
     * call it OFF the main thread (it does thousands of file writes on first run).
     *
     * [optedIn] is a hard safety gate (APP-458): seeding writes thousands of files into the shared
     * MediaStore, so it must NEVER happen without an explicit, deliberate opt-in from the caller. A
     * casual tap-launch of the benchmark APK (no opt-in) must not pollute a real photo library — the
     * caller is responsible for only passing `true` from the macrobenchmark / an explicit adb extra.
     */
    fun seed(
        context: Context,
        targetCount: Int = DEFAULT_CORPUS_SIZE,
        optedIn: Boolean,
    ): List<MediaItem> {
        check(optedIn) {
            "refusing to seed $BUCKET_NAME corpus without explicit opt-in — seeding pollutes the " +
                "device MediaStore and must be launched deliberately (see APP-458)"
        }
        val resolver = context.contentResolver
        val existing = countExisting(resolver)
        if (existing >= targetCount) {
            Log.i(TAG, "corpus already seeded: $existing >= $targetCount, skipping generation")
        } else {
            val palette = encodePalette()
            val toWrite = targetCount - existing
            Log.i(TAG, "seeding $toWrite files (have $existing, want $targetCount)…")
            var written = 0
            for (i in existing until targetCount) {
                val spec = PALETTE[i % PALETTE.size]
                val bytes = palette[i % PALETTE.size]
                if (insertFile(resolver, index = i, spec = spec, bytes = bytes)) written++
                if (written % 1_000 == 0 && written > 0) Log.i(TAG, "…seeded $written/$toWrite")
            }
            Log.i(TAG, "seeding complete: wrote $written new files")
        }
        val items = queryCorpus(resolver, targetCount)
        Log.i(TAG, "corpus query returned ${items.size} items (first id=${items.firstOrNull()?.id?.value})")
        return items
    }

    /** Count files already in the bench bucket (idempotency check). */
    private fun countExisting(resolver: ContentResolver): Int =
        resolver.query(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            arrayOf(MediaStore.Images.Media._ID),
            BENCH_SELECTION,
            BENCH_SELECTION_ARGS,
            null,
        )?.use { it.count } ?: 0

    /**
     * Delete every synthetic asset this fixture ever created and return the number of rows removed
     * (APP-458). The delete is scoped by [BENCH_SELECTION] — strictly `Pictures/JGalleryBench/…` — so
     * it can NEVER touch real user media. Idempotent and safe on a clean device (returns 0), so it
     * doubles as:
     *   1. the fixture TEARDOWN the macrobenchmark runs after a run (on success AND on failure/abort);
     *   2. the ONE-SHOT purge for any assets already leaked onto a test device (e.g. JD's library).
     *
     * Emits a grep-able `JGALLERY_BENCH_CLEANUP deleted=N remaining=M` line so a run can prove the
     * library is clean (`remaining=0`) afterwards. Call OFF the main thread.
     */
    fun cleanup(context: Context): Int {
        val resolver = context.contentResolver
        // Defence-in-depth: if the sentinel namespace is ever weakened to something non-specific,
        // refuse to delete rather than risk a broad match against real media.
        check(BUCKET_NAME == "JGalleryBench" && RELATIVE_DIR == "Pictures/JGalleryBench") {
            "refusing to clean up: bench namespace '$RELATIVE_DIR' is not the dedicated sentinel dir"
        }
        val deleted = try {
            resolver.delete(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                BENCH_SELECTION,
                BENCH_SELECTION_ARGS,
            )
        } catch (t: Throwable) {
            Log.w(TAG, "cleanup delete failed: ${t.message}")
            0
        }
        val remaining = countExisting(resolver)
        Log.i(TAG, "JGALLERY_BENCH_CLEANUP deleted=$deleted remaining=$remaining")
        return deleted
    }

    /** Encode one distinct real image per palette spec, reused across all corpus files. */
    private fun encodePalette(): List<ByteArray> = PALETTE.mapIndexed { idx, spec ->
        val bitmap = renderBitmap(spec, seed = idx)
        try {
            encode(bitmap, spec.fmt)
        } finally {
            bitmap.recycle()
        }
    }

    /**
     * A real gradient-plus-blocks image with genuine high-frequency content, so the encoder can't
     * compress it to a near-empty file and the decoder does representative work. [seed] varies the
     * palette so distinct specs don't render identically.
     */
    private fun renderBitmap(spec: Spec, seed: Int): Bitmap {
        val bmp = Bitmap.createBitmap(spec.width, spec.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        paint.shader = LinearGradient(
            0f, 0f, spec.width.toFloat(), spec.height.toFloat(),
            intArrayOf(
                0xFF000000.toInt() or (0x33_66_99 * (seed + 1)),
                0xFFFFFFFF.toInt(),
                0xFF000000.toInt() or (0x11_88_44 * (seed + 3)),
            ),
            null, Shader.TileMode.CLAMP,
        )
        canvas.drawRect(0f, 0f, spec.width.toFloat(), spec.height.toFloat(), paint)
        // High-frequency detail: a deterministic grid of contrasting blocks (defeats trivial compression).
        paint.shader = null
        val step = maxOf(16, spec.width / 48)
        var y = 0
        var toggle = seed
        while (y < spec.height) {
            var x = 0
            while (x < spec.width) {
                toggle = (toggle * 1103515245 + 12345) and 0x7fffffff
                paint.color = 0xFF000000.toInt() or (toggle and 0x00FFFFFF)
                canvas.drawRect(x.toFloat(), y.toFloat(), (x + step / 2).toFloat(), (y + step / 2).toFloat(), paint)
                x += step
            }
            y += step
        }
        return bmp
    }

    /** Encode a bitmap to the requested format; HEIC degrades to JPEG when the encoder is missing. */
    private fun encode(bitmap: Bitmap, fmt: Fmt): ByteArray = when (fmt) {
        Fmt.JPEG -> ByteArrayOutputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, it); it.toByteArray() }
        Fmt.PNG -> ByteArrayOutputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it); it.toByteArray() }
        Fmt.WEBP -> ByteArrayOutputStream().use {
            @Suppress("DEPRECATION")
            bitmap.compress(Bitmap.CompressFormat.WEBP, WEBP_QUALITY, it)
            it.toByteArray()
        }
        Fmt.HEIC -> encodeHeic(bitmap) ?: run {
            Log.w(TAG, "HEIC encode unavailable — falling back to JPEG for this palette slot")
            ByteArrayOutputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, it); it.toByteArray() }
        }
    }

    /** Encode HEIC via [HeifWriter] (API 28+) through a temp file; null if the device can't. */
    private fun encodeHeic(bitmap: Bitmap): ByteArray? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) return null
        val tmp = File.createTempFile("bench-heic", ".heic")
        return try {
            HeifWriter.Builder(tmp.absolutePath, bitmap.width, bitmap.height, HeifWriter.INPUT_MODE_BITMAP)
                .setQuality(HEIC_QUALITY)
                .setMaxImages(1)
                .build()
                .use { writer ->
                    writer.start()
                    writer.addBitmap(bitmap)
                    writer.stop(HEIC_ENCODE_TIMEOUT_MS)
                }
            tmp.readBytes().takeIf { it.isNotEmpty() }
        } catch (t: Throwable) {
            Log.w(TAG, "HeifWriter failed: ${t.message}")
            null
        } finally {
            tmp.delete()
        }
    }

    /** Insert one pending row, stream the bytes, then publish it. Returns false on any failure. */
    private fun insertFile(resolver: ContentResolver, index: Int, spec: Spec, bytes: ByteArray): Boolean {
        val takenMillis = BASE_TAKEN_MILLIS -
            (index / ITEMS_PER_DAY) * DAY_MILLIS -
            (index % ITEMS_PER_DAY) * 60_000L
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, "BENCH_%06d.%s".format(index, spec.fmt.ext))
            put(MediaStore.Images.Media.MIME_TYPE, spec.fmt.mime)
            put(MediaStore.Images.Media.RELATIVE_PATH, RELATIVE_DIR)
            put(MediaStore.Images.Media.WIDTH, spec.width)
            put(MediaStore.Images.Media.HEIGHT, spec.height)
            put(MediaStore.Images.Media.DATE_TAKEN, takenMillis)
            put(MediaStore.Images.Media.IS_PENDING, 1)
        }
        val uri = try {
            resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
        } catch (t: Throwable) {
            Log.w(TAG, "insert failed at $index: ${t.message}"); null
        } ?: return false
        return try {
            resolver.openOutputStream(uri)?.use { it.write(bytes) }
                ?: error("null output stream for $uri")
            values.clear()
            values.put(MediaStore.Images.Media.IS_PENDING, 0)
            resolver.update(uri, values, null, null)
            true
        } catch (t: Throwable) {
            Log.w(TAG, "write failed at $index: ${t.message}")
            runCatching { resolver.delete(uri, null, null) }
            false
        }
    }

    /**
     * Query the seeded corpus back as [MediaItem]s carrying REAL numeric MediaStore row-ids — the
     * exact ids the app's thumbnail fetcher resolves to `content://media/external/…/<id>`. Ordered
     * newest-first to match the Photos timeline.
     */
    private fun queryCorpus(resolver: ContentResolver, limit: Int): List<MediaItem> {
        val projection = arrayOf(
            MediaStore.Images.Media._ID,
            MediaStore.Images.Media.DISPLAY_NAME,
            MediaStore.Images.Media.BUCKET_ID,
            MediaStore.Images.Media.BUCKET_DISPLAY_NAME,
            MediaStore.Images.Media.DATE_TAKEN,
            MediaStore.Images.Media.DATE_MODIFIED,
            MediaStore.Images.Media.SIZE,
            MediaStore.Images.Media.WIDTH,
            MediaStore.Images.Media.HEIGHT,
            MediaStore.Images.Media.MIME_TYPE,
        )
        val out = ArrayList<MediaItem>(minOf(limit, DEFAULT_CORPUS_SIZE))
        resolver.query(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            projection,
            BENCH_SELECTION,
            BENCH_SELECTION_ARGS,
            "${MediaStore.Images.Media.DATE_TAKEN} DESC",
        )?.use { cursor ->
            val idCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
            val nameCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DISPLAY_NAME)
            val bucketIdCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.BUCKET_ID)
            val bucketNameCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.BUCKET_DISPLAY_NAME)
            val takenCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_TAKEN)
            val modCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_MODIFIED)
            val sizeCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.SIZE)
            val widthCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.WIDTH)
            val heightCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.HEIGHT)
            val mimeCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.MIME_TYPE)
            while (cursor.moveToNext() && out.size < limit) {
                out += MediaItem(
                    id = MediaId(cursor.getLong(idCol).toString()),
                    displayName = cursor.getString(nameCol) ?: "",
                    type = MediaType.IMAGE,
                    bucketId = cursor.getString(bucketIdCol) ?: "",
                    bucketName = cursor.getString(bucketNameCol) ?: BUCKET_NAME,
                    dateTakenMillis = cursor.getLong(takenCol),
                    dateModifiedMillis = cursor.getLong(modCol) * 1000L, // MediaStore stores seconds
                    sizeBytes = cursor.getLong(sizeCol),
                    width = cursor.getInt(widthCol),
                    height = cursor.getInt(heightCol),
                    durationMillis = 0L,
                    mimeType = cursor.getString(mimeCol) ?: "image/jpeg",
                )
            }
        }
        return out
    }

    /** Content uri for a seeded row-id — used by the launch-time decode self-check. */
    fun contentUriFor(item: MediaItem) =
        ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, item.id.value.toLong())

    private const val HEIC_ENCODE_TIMEOUT_MS = 10_000L
}
