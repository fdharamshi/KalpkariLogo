package com.kalpkari.watermark.media

import com.kalpkari.watermark.domain.InstagramMargins
import com.kalpkari.watermark.domain.WatermarkPosition

/** Logo rectangle in pixel space relative to the media's top-left origin. */
data class LogoRect(val left: Float, val top: Float, val width: Float, val height: Float)

/**
 * Single source of truth for where the logo lands. Used identically by the
 * image compositor, the Compose preview, and (converted to NDC) the video path,
 * so what the user previews is what they export.
 */
object Placement {

    fun compute(
        mediaW: Float,
        mediaH: Float,
        logoSrcW: Float,
        logoSrcH: Float,
        position: WatermarkPosition,
        logoWidthFraction: Float = InstagramMargins.logoWidth,
        offsetX: Float = 0f,
        offsetY: Float = 0f,
    ): LogoRect {
        val w = logoWidthFraction * mediaW
        val h = w * (logoSrcH / logoSrcW)
        val mx = InstagramMargins.horizontal * mediaW
        val top = InstagramMargins.topMargin * mediaH
        val bottom = InstagramMargins.bottomMargin * mediaH

        val baseLeft = when (position) {
            WatermarkPosition.TOP_LEFT, WatermarkPosition.BOTTOM_LEFT -> mx
            WatermarkPosition.TOP_RIGHT, WatermarkPosition.BOTTOM_RIGHT -> mediaW - mx - w
            WatermarkPosition.CENTER_TOP -> (mediaW - w) / 2f
        }
        val baseTop = when (position) {
            WatermarkPosition.TOP_LEFT, WatermarkPosition.TOP_RIGHT, WatermarkPosition.CENTER_TOP -> top
            WatermarkPosition.BOTTOM_LEFT, WatermarkPosition.BOTTOM_RIGHT -> mediaH - bottom - h
        }
        // Manual nudge: fraction of media dimension. +x right, +y down.
        return LogoRect(baseLeft + offsetX * mediaW, baseTop + offsetY * mediaH, w, h)
    }
}
