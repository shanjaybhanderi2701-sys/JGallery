package com.appblish.jgallery.core.model

/**
 * How JGallery presents an image beyond a plain JPEG/PNG, derived PURELY from indexed metadata
 * (MIME type + file extension + dimensions). Wave 3 §8 broadens format support; the design's
 * governing rule is *"one typed decode result drives tile, viewer and Info — they never disagree."*
 * This is that typed value. It is platform-free so it is unit-testable on the JVM and safe to pass
 * across every layer.
 *
 * A [ImageFormat] does NOT itself mean a decode succeeded — it means "this is the strategy we intend
 * to use". A genuine decode failure at runtime still falls through to E15's unsupported/corrupt
 * placeholder; the format only tells the tile/viewer which affordance (badge, banner, playback) to
 * pre-arrange.
 */
enum class ImageFormat {
    /** JPEG / PNG — decodes fully via the platform. No badge, no banner. */
    STANDARD,

    /** HEIC / HEIF — decodes fully via the platform (API 29+). Treated as standard for the user. */
    HEIF,

    /** WEBP (still or animated) — decodes fully via the platform / animated-image decoder. */
    WEBP,

    /** BMP — decodes fully via the platform. */
    BMP,

    /** Animated GIF — static first-frame tile with a GIF badge; animates in the viewer only. */
    GIF,

    /** SVG — best-effort vector render (coil-svg). Shows a best-effort affordance. */
    SVG,

    /** RAW (DNG / CR2 / NEF / ARW / …) — best-effort embedded-JPEG preview, never a full RAW decode. */
    RAW,

    /** A recognised image we still route through the platform decoder without any special affordance. */
    OTHER,
}

/** The small corner badge a grid tile shows so the user knows a tile is special before opening it. */
enum class FormatBadge { GIF, SVG, RAW, PANO }

/**
 * Best-effort degradation cases (design W3-04): we render *something* but honestly signal the limit.
 * Kept as a typed kind so the viewer supplies the user-facing copy (model stays copy-free).
 */
enum class BestEffortKind {
    /** Showing the embedded JPEG preview extracted from a RAW file, not a full RAW decode. */
    RAW_EMBEDDED_JPEG,

    /** Rendering an SVG as a best-effort raster preview. */
    SVG_PREVIEW,
}

/**
 * An image whose wider edge is at least this many times its shorter edge is treated as a panorama
 * (design W3-03: "aspect ratio ≥ ~2:1"), so the grid letterboxes it and the viewer opens it
 * fit-to-width with horizontal pan.
 */
const val PANORAMA_MIN_ASPECT = 2.0f

/**
 * Classify by MIME first (the authoritative index field), falling back to the file extension when
 * the MIME is missing or generic (`application/octet-stream`, empty) — common for RAW rows that
 * MediaStore leaves untyped. [extension] is the lowercased suffix WITHOUT the dot.
 */
fun classifyImageFormat(mimeType: String, extension: String? = null): ImageFormat {
    val mime = mimeType.trim().lowercase()
    // Accept either a bare extension ("nef") or a whole filename ("shot.nef"); normalise to the
    // suffix after the last dot. A dotless value is taken as-is so a bare extension still works.
    val ext = extension?.trim()?.lowercase()?.substringAfterLast('.')?.takeIf { it.isNotEmpty() }

    when (mime) {
        "image/gif" -> return ImageFormat.GIF
        "image/svg+xml", "image/svg" -> return ImageFormat.SVG
        "image/webp" -> return ImageFormat.WEBP
        "image/bmp", "image/x-ms-bmp" -> return ImageFormat.BMP
        "image/heic", "image/heif", "image/heic-sequence", "image/heif-sequence" -> return ImageFormat.HEIF
        "image/jpeg", "image/jpg", "image/png" -> return ImageFormat.STANDARD
    }
    if (mime in RAW_MIME_TYPES) return ImageFormat.RAW

    // MIME was generic/unknown/empty — decide on the extension.
    when (ext) {
        null, "" -> Unit
        "gif" -> return ImageFormat.GIF
        "svg" -> return ImageFormat.SVG
        "webp" -> return ImageFormat.WEBP
        "bmp", "dib" -> return ImageFormat.BMP
        "heic", "heif", "hif" -> return ImageFormat.HEIF
        "jpg", "jpeg", "png" -> return ImageFormat.STANDARD
        in RAW_EXTENSIONS -> return ImageFormat.RAW
    }

    // Recognised-but-unremarkable, or unknown: route through the platform decoder with no affordance.
    return ImageFormat.OTHER
}

/** Lowercased file extension without the dot, or null when the name carries none. */
val MediaItem.fileExtension: String?
    get() = displayName.substringAfterLast('.', "").lowercase().takeIf { it.isNotEmpty() }

/** The intended presentation strategy for this item (images only; videos are STANDARD-equivalent). */
val MediaItem.imageFormat: ImageFormat
    get() = if (type == MediaType.IMAGE) classifyImageFormat(mimeType, fileExtension) else ImageFormat.STANDARD

/**
 * True for images we animate in the viewer. GIF is the reliable proxy for the index's `isAnimated`
 * flag; animated WEBP/HEIF also animate through the platform decoder but can't be told apart from
 * a still by metadata alone, so they are not badged (they simply animate if the bytes are animated).
 */
val MediaItem.isAnimatedImage: Boolean
    get() = imageFormat == ImageFormat.GIF

/** Wide-aspect image (design W3-03). Requires known dimensions; unknown dims are never panoramas. */
val MediaItem.isPanorama: Boolean
    get() {
        if (type != MediaType.IMAGE || width <= 0 || height <= 0) return false
        val longEdge = maxOf(width, height).toFloat()
        val shortEdge = minOf(width, height).toFloat()
        return longEdge / shortEdge >= PANORAMA_MIN_ASPECT
    }

/**
 * The single grid badge for this item, or null when it is an ordinary tile. Format badges take
 * precedence over the panorama badge (a wide GIF is still first a GIF); at most one badge shows so
 * the tile never becomes noisy (design §W3-02/03/04).
 */
val MediaItem.formatBadge: FormatBadge?
    get() = when (imageFormat) {
        ImageFormat.GIF -> FormatBadge.GIF
        ImageFormat.SVG -> FormatBadge.SVG
        ImageFormat.RAW -> FormatBadge.RAW
        else -> if (isPanorama) FormatBadge.PANO else null
    }

/** The best-effort degradation to surface in the viewer, or null when the render is full-fidelity. */
val MediaItem.bestEffortKind: BestEffortKind?
    get() = when (imageFormat) {
        ImageFormat.RAW -> BestEffortKind.RAW_EMBEDDED_JPEG
        ImageFormat.SVG -> BestEffortKind.SVG_PREVIEW
        else -> null
    }

/** RAW MIME types MediaStore is known to report across vendors. */
private val RAW_MIME_TYPES = setOf(
    "image/x-adobe-dng", "image/dng", "image/x-dng",
    "image/x-canon-cr2", "image/x-canon-cr3", "image/x-canon-crw",
    "image/x-nikon-nef", "image/x-nikon-nrw",
    "image/x-sony-arw", "image/x-sony-sr2", "image/x-sony-srf",
    "image/x-panasonic-rw2", "image/x-panasonic-raw",
    "image/x-fuji-raf", "image/x-fujifilm-raf",
    "image/x-olympus-orf", "image/x-pentax-pef",
    "image/x-samsung-srw", "image/x-sigma-x3f",
    "image/x-minolta-mrw", "image/x-kodak-dcr", "image/x-kodak-kdc",
    "image/raw", "image/x-raw",
)

/** RAW file extensions (the MIME fallback), lowercased without dots. */
private val RAW_EXTENSIONS = setOf(
    "dng", "cr2", "cr3", "crw", "nef", "nrw", "arw", "sr2", "srf",
    "rw2", "raf", "orf", "pef", "srw", "x3f", "mrw", "dcr", "kdc",
    "raw", "3fr", "mef", "iiq", "gpr", "raf",
)
