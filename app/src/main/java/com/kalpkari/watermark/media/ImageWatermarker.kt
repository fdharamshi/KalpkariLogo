package com.kalpkari.watermark.media

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.graphics.Rect
import android.graphics.RectF
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import com.kalpkari.watermark.domain.WatermarkPosition

/** Composites a logo onto a still image at full source resolution. */
object ImageWatermarker {

    /**
     * Decodes the source (honouring EXIF rotation) and draws the logo on top.
     * Returns a new ARGB_8888 bitmap at the source's pixel resolution.
     */
    fun compose(
        context: Context,
        sourceUri: Uri,
        logo: Bitmap,
        position: WatermarkPosition,
        logoWidthFraction: Float,
        offsetX: Float,
        offsetY: Float,
        tintColor: Int?,
        cropRect: android.graphics.RectF? = null,
    ): Bitmap {
        val source = decodeUpright(context, sourceUri)
        
        // Crop the source bitmap if cropRect is present
        val croppedSource = if (cropRect != null) {
            val left = (cropRect.left * source.width).toInt().coerceIn(0, source.width - 1)
            val top = (cropRect.top * source.height).toInt().coerceIn(0, source.height - 1)
            val right = (cropRect.right * source.width).toInt().coerceIn(left + 1, source.width)
            val bottom = (cropRect.bottom * source.height).toInt().coerceIn(top + 1, source.height)
            
            val cropW = right - left
            val cropH = bottom - top
            
            val cropped = Bitmap.createBitmap(source, left, top, cropW, cropH)
            if (cropped !== source) {
                source.recycle()
            }
            cropped
        } else {
            source
        }

        val out = croppedSource.copy(Bitmap.Config.ARGB_8888, true)
        if (out !== croppedSource) croppedSource.recycle()

        val rect = Placement.compute(
            mediaW = out.width.toFloat(),
            mediaH = out.height.toFloat(),
            logoSrcW = logo.width.toFloat(),
            logoSrcH = logo.height.toFloat(),
            position = position,
            logoWidthFraction = logoWidthFraction,
            offsetX = offsetX,
            offsetY = offsetY,
        )
        val targetW = rect.width.toInt().coerceAtLeast(1)
        val targetH = rect.height.toInt().coerceAtLeast(1)

        // Scale on CPU using high-quality downscaling to EXACTLY the target size.
        val scaledLogo = BitmapScaler.scaleHighQuality(logo, targetW, targetH)

        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            isFilterBitmap = true
            // Recolour the silhouette while keeping the logo's alpha edges.
            if (tintColor != null) colorFilter = PorterDuffColorFilter(tintColor, PorterDuff.Mode.SRC_IN)
        }
        // Draw the pre-scaled bitmap 1-to-1 onto the canvas to ensure maximum crispness.
        // Round coordinates to nearest integer pixels to prevent subpixel interpolation blur.
        Canvas(out).drawBitmap(scaledLogo, Math.round(rect.left).toFloat(), Math.round(rect.top).toFloat(), paint)

        // Clean up the temporary scaled bitmap if one was created.
        if (scaledLogo !== logo) {
            scaledLogo.recycle()
        }
        return out
    }

    /**
     * Decodes a display-sized, upright bitmap for the live preview (not the export).
     * Keeps memory low on huge sources while staying sharp on screen.
     */
    fun decodePreview(context: Context, uri: Uri, maxDim: Int = 1280): Bitmap {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri).use { BitmapFactory.decodeStream(it, null, bounds) }
        var sample = 1
        val longest = maxOf(bounds.outWidth, bounds.outHeight)
        while (longest / sample > maxDim) sample *= 2
        val bitmap = context.contentResolver.openInputStream(uri).use { input ->
            BitmapFactory.decodeStream(
                input, null,
                BitmapFactory.Options().apply {
                    inSampleSize = sample
                    inPreferredConfig = Bitmap.Config.ARGB_8888
                },
            ) ?: error("Cannot decode image")
        }
        return applyExif(context, uri, bitmap)
    }

    private fun decodeUpright(context: Context, uri: Uri): Bitmap {
        val bitmap = context.contentResolver.openInputStream(uri).use { input ->
            BitmapFactory.decodeStream(
                input, null,
                BitmapFactory.Options().apply { inPreferredConfig = Bitmap.Config.ARGB_8888 },
            ) ?: error("Cannot decode image")
        }
        return applyExif(context, uri, bitmap)
    }

    private fun applyExif(context: Context, uri: Uri, bitmap: Bitmap): Bitmap {
        val orientation = context.contentResolver.openInputStream(uri)?.use { input ->
            ExifInterface(input).getAttributeInt(
                ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL,
            )
        } ?: ExifInterface.ORIENTATION_NORMAL

        val matrix = Matrix()
        when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
            ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
            ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.postScale(-1f, 1f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.postScale(1f, -1f)
            else -> return bitmap
        }
        val rotated = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
        if (rotated !== bitmap) bitmap.recycle()
        return rotated
    }
}
