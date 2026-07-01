package com.kalpkari.watermark.domain

/** Media kind detected from the picked Uri's MIME type. */
enum class MediaType { IMAGE, VIDEO }

/** Predefined watermark placements. */
enum class WatermarkPosition(val label: String) {
    TOP_LEFT("Top Left"),
    TOP_RIGHT("Top Right"),
    CENTER_TOP("Center Top"),
    BOTTOM_LEFT("Bottom Left"),
    BOTTOM_RIGHT("Bottom Right"),
}

/** A bundled logo asset. [assetPath] is relative to the assets/ root. */
data class LogoAsset(
    val id: String,
    val assetPath: String,
)

/**
 * Logo colour choice. [tintColor] is null for the original artwork, or an ARGB
 * int to recolour the (alpha-preserving) silhouette.
 */
enum class LogoTint(val label: String, val tintColor: Int?) {
    ORIGINAL("Original", null),
    RUST("Rust", 0xFF7D3208.toInt()),
}

/**
 * Instagram-safe placement constants, expressed as fractions of the media
 * dimensions so they scale with any resolution. All values are easy to tweak.
 *
 *  - [horizontal]   : left/right padding as a fraction of media WIDTH.
 *  - [topMargin]    : top padding as a fraction of media HEIGHT (clears IG top icons).
 *  - [bottomMargin] : bottom padding as a fraction of media HEIGHT (clears Reels caption/UI).
 *  - [logoWidth]    : logo width as a fraction of media WIDTH (aspect ratio preserved).
 */
object InstagramMargins {
    var horizontal: Float = 0.05f
    var topMargin: Float = 0.07f
    var bottomMargin: Float = 0.15f

    /** Default logo width fraction; the user can override it with the size slider. */
    var logoWidth: Float = 0.22f

    /** Bounds for the manual logo-size slider (fraction of media width). */
    const val MIN_LOGO_WIDTH = 0.08f
    const val MAX_LOGO_WIDTH = 0.90f
}

/** Crop aspect ratio presets. */
enum class CropPreset(val label: String, val aspectRatio: Float?) {
    ORIGINAL("Original", null),
    POST("Post (4:5)", 0.8f),
    STORY("Story/Reel (9:16)", 0.5625f),
}
