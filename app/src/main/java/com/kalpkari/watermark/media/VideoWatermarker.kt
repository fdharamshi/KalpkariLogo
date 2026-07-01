package com.kalpkari.watermark.media

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Handler
import android.os.Looper
import androidx.annotation.OptIn
import androidx.media3.common.Effect
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.BitmapOverlay
import androidx.media3.effect.Crop
import androidx.media3.effect.OverlayEffect
import androidx.media3.effect.OverlaySettings
import androidx.media3.effect.TextureOverlay
import androidx.media3.transformer.Composition
import androidx.media3.transformer.DefaultEncoderFactory
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.Effects
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.ProgressHolder
import androidx.media3.transformer.Transformer
import androidx.media3.transformer.VideoEncoderSettings
import com.google.common.collect.ImmutableList
import com.kalpkari.watermark.domain.InstagramMargins
import com.kalpkari.watermark.domain.WatermarkPosition
import java.io.File

/**
 * Renders a logo overlay onto a video with Jetpack Media3 [Transformer].
 *
 * Quality strategy: video is GPU-composited and re-encoded (unavoidable for an
 * overlay) at the source's resolution, frame rate, aspect ratio and duration.
 * The video encoder bitrate is matched to the source so visual quality is
 * preserved. Audio is transmuxed (copied) unchanged because no audio effect is
 * applied. Output is H.264/AAC in an MP4 — the safest combo for Instagram.
 */
@OptIn(UnstableApi::class)
object VideoWatermarker {

    /** Handle returned to the caller so an in-flight export can be cancelled. */
    class Job internal constructor(
        private val transformer: Transformer,
        private val handler: Handler,
        private val ticker: Runnable,
    ) {
        fun cancel() {
            handler.removeCallbacks(ticker)
            transformer.cancel()
        }
    }

    fun export(
        context: Context,
        sourceUri: Uri,
        logo: Bitmap,
        position: WatermarkPosition,
        logoWidthFraction: Float,
        offsetX: Float,
        offsetY: Float,
        tintColor: Int?,
        cropRect: android.graphics.RectF?,
        onProgress: (Int) -> Unit,
        onSuccess: (File) -> Unit,
        onError: (Throwable) -> Unit,
    ): Job {
        val (dispW, dispH, srcBitrate) = probe(context, sourceUri)

        val videoEffectsList = mutableListOf<Effect>()

        // 1. If cropRect is present, apply Crop effect first.
        if (cropRect != null) {
            val ndcLeft = -1.0f + 2.0f * cropRect.left
            val ndcRight = -1.0f + 2.0f * cropRect.right
            val ndcBottom = -1.0f + 2.0f * (1.0f - cropRect.bottom)
            val ndcTop = -1.0f + 2.0f * (1.0f - cropRect.top)
            videoEffectsList.add(Crop(ndcLeft, ndcRight, ndcBottom, ndcTop))
        }

        // 2. Build the watermark overlay relative to cropped dimensions.
        val croppedW = if (cropRect != null) (dispW * (cropRect.right - cropRect.left)).toInt().coerceAtLeast(1) else dispW
        val croppedH = if (cropRect != null) (dispH * (cropRect.bottom - cropRect.top)).toInt().coerceAtLeast(1) else dispH

        val overlay = buildOverlay(logo, croppedW, croppedH, position, logoWidthFraction, offsetX, offsetY, tintColor)
        val overlays: ImmutableList<TextureOverlay> = ImmutableList.of(overlay)
        videoEffectsList.add(OverlayEffect(overlays))

        val effects = Effects(/* audioProcessors = */ emptyList(), /* videoEffects = */ videoEffectsList)

        val editedItem = EditedMediaItem.Builder(MediaItem.fromUri(sourceUri))
            .setEffects(effects)
            .build()

        val encoderFactory = DefaultEncoderFactory.Builder(context)
            .setRequestedVideoEncoderSettings(
                VideoEncoderSettings.Builder()
                    .setBitrate(targetBitrate(dispW, dispH, srcBitrate))
                    .build(),
            )
            .setEnableFallback(true)
            .build()

        val outFile = File(context.cacheDir, "kw_${System.currentTimeMillis()}.mp4")
        val handler = Handler(Looper.getMainLooper())

        val transformer = Transformer.Builder(context)
            .setEncoderFactory(encoderFactory)
            .addListener(object : Transformer.Listener {
                override fun onCompleted(composition: Composition, result: ExportResult) {
                    onSuccess(outFile)
                }

                override fun onError(
                    composition: Composition,
                    result: ExportResult,
                    exception: ExportException,
                ) {
                    onError(exception)
                }
            })
            .build()

        // Poll progress on the main thread until export resolves.
        val ticker = object : Runnable {
            private val holder = ProgressHolder()
            override fun run() {
                val state = transformer.getProgress(holder)
                if (state != Transformer.PROGRESS_STATE_NOT_STARTED) {
                    onProgress(holder.progress)
                    handler.postDelayed(this, 200)
                }
            }
        }

        transformer.start(editedItem, outFile.absolutePath)
        handler.postDelayed(ticker, 200)
        return Job(transformer, handler, ticker)
    }

    private fun buildOverlay(
        logo: Bitmap,
        dispW: Int,
        dispH: Int,
        position: WatermarkPosition,
        logoWidthFraction: Float,
        offsetX: Float,
        offsetY: Float,
        tintColor: Int?,
    ): BitmapOverlay {
        // Target logo size in output pixels.
        val targetW = (logoWidthFraction * dispW).toInt().coerceAtLeast(1)
        val targetH = (targetW.toLong() * logo.height / logo.width).toInt().coerceAtLeast(1)

        // Scale on CPU using high-quality downscaling to EXACTLY the target size.
        val scaledBmp = BitmapScaler.scaleHighQuality(logo, targetW, targetH)
        val overlayBmp = tinted(scaledBmp, tintColor)

        // If scaledBmp is a temporary new bitmap and we created a copy in tinted(), recycle scaledBmp.
        if (scaledBmp !== logo && scaledBmp !== overlayBmp) {
            scaledBmp.recycle()
        }

        // Since overlayBmp has width targetW, scale is 1f, which guarantees 1-to-1 pixel mapping in OpenGL!
        val scale = 1.0f

        // Margins expressed in NDC (the [-1,1] frame spans 2 units).
        val mx = 2f * InstagramMargins.horizontal
        val mt = 2f * InstagramMargins.topMargin
        val mb = 2f * InstagramMargins.bottomMargin

        // overlayAnchor = the corner of the LOGO that we pin; bgAnchor = where on
        // the FRAME it lands. NDC: x right = +1, y up = +1.
        val (ox, oy, bx, by) = when (position) {
            WatermarkPosition.TOP_RIGHT -> Quad(1f, 1f, 1f - mx, 1f - mt)
            WatermarkPosition.TOP_LEFT -> Quad(-1f, 1f, -1f + mx, 1f - mt)
            WatermarkPosition.CENTER_TOP -> Quad(0f, 1f, 0f, 1f - mt)
            WatermarkPosition.BOTTOM_RIGHT -> Quad(1f, -1f, 1f - mx, -1f + mb)
            WatermarkPosition.BOTTOM_LEFT -> Quad(-1f, -1f, -1f + mx, -1f + mb)
        }
        // Manual nudge in NDC: +x right, +y down (screen) = -y in NDC.
        // Anchors MUST stay within [-1, 1] or OverlaySettings throws.
        val bxo = (bx + 2f * offsetX).coerceIn(-1f, 1f)
        val byo = (by - 2f * offsetY).coerceIn(-1f, 1f)

        val settings = OverlaySettings.Builder()
            .setScale(scale, scale)
            .setOverlayFrameAnchor(ox, oy)
            .setBackgroundFrameAnchor(bxo, byo)
            .build()
        return BitmapOverlay.createStaticBitmapOverlay(overlayBmp, settings)
    }

    /** Recolours the silhouette to [tintColor] (alpha preserved); original if null. */
    private fun tinted(src: Bitmap, tintColor: Int?): Bitmap {
        if (tintColor == null) return src
        val out = Bitmap.createBitmap(src.width, src.height, Bitmap.Config.ARGB_8888)
        val paint = Paint().apply {
            colorFilter = PorterDuffColorFilter(tintColor, PorterDuff.Mode.SRC_IN)
        }
        Canvas(out).drawBitmap(src, 0f, 0f, paint)
        return out
    }

    private data class Quad(val a: Float, val b: Float, val c: Float, val d: Float)

    /** width, height (rotation-corrected), source bitrate (bps, 0 if unknown). */
    private fun probe(context: Context, uri: Uri): Triple<Int, Int, Int> {
        val r = MediaMetadataRetriever()
        try {
            r.setDataSource(context, uri)
            val w = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull() ?: 1080
            val h = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull() ?: 1920
            val rot = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)?.toIntOrNull() ?: 0
            val bitrate = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_BITRATE)?.toIntOrNull() ?: 0
            return if (rot == 90 || rot == 270) Triple(h, w, bitrate) else Triple(w, h, bitrate)
        } finally {
            r.release()
        }
    }

    /**
     * Re-encoding always loses some quality, so we bias for headroom: take the
     * higher of (1.25x the source bitrate) and a resolution-based quality floor.
     * This keeps sharp logo edges from blocking/"burning" after compression.
     */
    private fun targetBitrate(w: Int, h: Int, srcBitrate: Int): Int {
        val pixels = w.toLong() * h
        val floor = when {
            pixels >= 3840L * 2160 -> 45_000_000
            pixels >= 2560L * 1440 -> 24_000_000
            pixels >= 1920L * 1080 -> 14_000_000
            pixels >= 1280L * 720 -> 7_000_000
            else -> 4_000_000
        }
        val fromSource = (srcBitrate * 1.25).toInt()
        return maxOf(fromSource, floor)
    }
}
