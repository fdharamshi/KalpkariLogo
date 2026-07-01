package com.kalpkari.watermark.media

import android.graphics.Bitmap

/**
 * High-quality progressive downscaling for bitmaps to avoid aliasing and blurriness
 * caused by scaling down large assets in a single step.
 */
object BitmapScaler {

    /**
     * Scales the source bitmap to the target dimensions using progressive downscaling.
     * Returns either the original bitmap (if dimensions match) or a new scaled bitmap.
     *
     * Note: Caller is responsible for recycling the returned bitmap if it's a new instance.
     */
    fun scaleHighQuality(src: Bitmap, dstW: Int, dstH: Int): Bitmap {
        val w = dstW.coerceAtLeast(1)
        val h = dstH.coerceAtLeast(1)

        if (src.width == w && src.height == h) {
            return src
        }

        // If we are scaling up, standard bilinear filtering is sufficient and safe.
        if (w >= src.width || h >= src.height) {
            return Bitmap.createScaledBitmap(src, w, h, /* filter = */ true)
        }

        // Progressive downscaling (halving size iteratively) to simulate mipmapping.
        var currentBitmap = src
        var currentW = src.width
        var currentH = src.height

        while (currentW > w * 2 && currentH > h * 2) {
            val nextW = currentW / 2
            val nextH = currentH / 2
            val nextBmp = Bitmap.createScaledBitmap(currentBitmap, nextW, nextH, /* filter = */ true)
            
            // Clean up temporary intermediate bitmaps.
            if (currentBitmap !== src) {
                currentBitmap.recycle()
            }
            currentBitmap = nextBmp
            currentW = nextW
            currentH = nextH
        }

        // Final precise scale step.
        val finalBmp = Bitmap.createScaledBitmap(currentBitmap, w, h, /* filter = */ true)
        if (currentBitmap !== src) {
            currentBitmap.recycle()
        }

        return finalBmp
    }
}
