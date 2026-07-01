package com.kalpkari.watermark.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.kalpkari.watermark.domain.LogoAsset

/** Loads the bundled transparent logos from assets/logos. */
object LogoRepository {

    private const val DIR = "logos"

    /** Lists logo assets, sorted by filename. */
    fun list(context: Context): List<LogoAsset> {
        val files = context.assets.list(DIR)?.sorted().orEmpty()
        return files
            .filter { it.endsWith(".png", true) || it.endsWith(".webp", true) }
            .map { LogoAsset(id = it, assetPath = "$DIR/$it") }
    }

    /** Decodes a logo asset to an ARGB_8888 bitmap (transparency preserved). */
    fun loadBitmap(context: Context, asset: LogoAsset): Bitmap {
        context.assets.open(asset.assetPath).use { stream ->
            val opts = BitmapFactory.Options().apply { inPreferredConfig = Bitmap.Config.ARGB_8888 }
            return BitmapFactory.decodeStream(stream, null, opts)
                ?: error("Failed to decode logo ${asset.assetPath}")
        }
    }
}
