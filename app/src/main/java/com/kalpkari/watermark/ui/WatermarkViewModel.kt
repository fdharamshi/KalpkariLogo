package com.kalpkari.watermark.ui

import android.app.Application
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.graphics.RectF
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.kalpkari.watermark.data.LogoRepository
import com.kalpkari.watermark.domain.CropPreset
import com.kalpkari.watermark.domain.InstagramMargins
import com.kalpkari.watermark.domain.LogoAsset
import com.kalpkari.watermark.domain.LogoTint
import com.kalpkari.watermark.domain.MediaType
import com.kalpkari.watermark.domain.WatermarkPosition
import com.kalpkari.watermark.media.ImageWatermarker
import com.kalpkari.watermark.media.MediaSaver
import com.kalpkari.watermark.media.VideoWatermarker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class UiState(
    val logos: List<LogoAsset> = emptyList(),
    val selectedLogo: LogoAsset? = null,
    val mediaUri: Uri? = null,
    val mediaType: MediaType? = null,
    val position: WatermarkPosition = WatermarkPosition.BOTTOM_RIGHT,
    val logoWidthFraction: Float = InstagramMargins.logoWidth,
    val tint: LogoTint = LogoTint.ORIGINAL,
    val offsetX: Float = 0f,
    val offsetY: Float = 0f,
    val logoAspect: Float = 1f,
    // Background shown in the preview: a display-scaled source image, or the first video frame.
    // Loaded once when media is picked; placement changes never reload it (no scroll jump).
    val previewBitmap: Bitmap? = null,
    val loadingPreview: Boolean = false,
    val exporting: Boolean = false,
    val progress: Int = 0,
    val resultUri: Uri? = null,
    val resultMime: String? = null,
    val error: String? = null,

    // Queue State (Multi Mode)
    val mediaQueue: List<Uri> = emptyList(),
    val currentQueueIndex: Int = 0,

    // Crop State
    val cropPreset: CropPreset = CropPreset.ORIGINAL,
    val cropRect: RectF? = null, // Normalized crop bounds [0, 1]
    val cropScale: Float = 1.0f,
    val cropPanX: Float = 0f,
    val cropPanY: Float = 0f,

    // Lightbox expanded preview
    val isExpandedPreviewOpen: Boolean = false,
) {
    val canExport: Boolean
        get() = mediaUri != null && selectedLogo != null && previewBitmap != null &&
            !exporting && !loadingPreview
}

class WatermarkViewModel(app: Application) : AndroidViewModel(app) {

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    private var logoBitmap: Bitmap? = null
    private var exportJob: VideoWatermarker.Job? = null

    init {
        val logos = LogoRepository.list(getApplication())
        _state.update { it.copy(logos = logos, selectedLogo = logos.firstOrNull()) }
        loadLogoBitmap()
    }

    fun onMediaPicked(uris: List<Uri>) {
        if (uris.isEmpty()) return
        _state.update {
            it.copy(
                mediaQueue = uris,
                currentQueueIndex = 0,
                resultUri = null,
                resultMime = null,
                error = null
            )
        }
        loadCurrentQueueItem()
    }

    private fun loadCurrentQueueItem() {
        val s = _state.value
        if (s.mediaQueue.isEmpty() || s.currentQueueIndex >= s.mediaQueue.size) return
        val uri = s.mediaQueue[s.currentQueueIndex]
        val mime = getApplication<Application>().contentResolver.getType(uri).orEmpty()
        val type = when {
            mime.startsWith("video") -> MediaType.VIDEO
            mime.startsWith("image") -> MediaType.IMAGE
            else -> null
        }
        if (type == null) {
            _state.update { it.copy(error = "Unsupported media type: $mime") }
            return
        }
        _state.update {
            it.copy(
                mediaUri = uri, mediaType = type,
                previewBitmap = null, loadingPreview = true,
                offsetX = 0f, offsetY = 0f,
                cropPreset = CropPreset.ORIGINAL,
                cropRect = null,
                cropScale = 1.0f,
                cropPanX = 0f,
                cropPanY = 0f,
                resultUri = null, resultMime = null, error = null,
            )
        }
        viewModelScope.launch {
            val bmp = withContext(Dispatchers.IO) {
                runCatching {
                    when (type) {
                        MediaType.IMAGE -> ImageWatermarker.decodePreview(getApplication(), uri)
                        MediaType.VIDEO -> firstFrame(uri)
                    }
                 }.getOrNull()
            }
            _state.update {
                if (it.mediaUri != uri) it // a newer pick superseded this load
                else it.copy(previewBitmap = bmp, loadingPreview = false)
            }
        }
    }

    fun onLogoSelected(logo: LogoAsset) {
        _state.update { it.copy(selectedLogo = logo, resultUri = null) }
        loadLogoBitmap()
    }

    fun onPositionSelected(position: WatermarkPosition) {
        _state.update { it.copy(position = position, resultUri = null) }
    }

    fun onLogoSizeChanged(fraction: Float) {
        _state.update { it.copy(logoWidthFraction = fraction, resultUri = null) }
    }

    fun onTintSelected(tint: LogoTint) {
        _state.update { it.copy(tint = tint, resultUri = null) }
    }

    fun onOffsetChanged(dx: Float, dy: Float) {
        _state.update { it.copy(offsetX = dx, offsetY = dy, resultUri = null) }
    }

    fun resetOffsets() {
        _state.update { it.copy(offsetX = 0f, offsetY = 0f, resultUri = null) }
    }

    fun dismissError() = _state.update { it.copy(error = null) }

    fun dismissResult() {
        _state.update {
            it.copy(
                resultUri = null,
                resultMime = null,
                currentQueueIndex = it.currentQueueIndex + 1
            )
        }
        val s = _state.value
        if (s.currentQueueIndex < s.mediaQueue.size) {
            loadCurrentQueueItem()
        } else {
            // Queue complete, go back to landing screen
            clearMedia()
        }
    }

    fun clearMedia() {
        _state.update {
            it.copy(
                mediaQueue = emptyList(),
                currentQueueIndex = 0,
                mediaUri = null,
                mediaType = null,
                previewBitmap = null,
                offsetX = 0f,
                offsetY = 0f,
                cropPreset = CropPreset.ORIGINAL,
                cropRect = null,
                cropScale = 1.0f,
                cropPanX = 0f,
                cropPanY = 0f,
                resultUri = null,
                resultMime = null,
                error = null
            )
        }
    }

    fun onCropPresetChanged(preset: CropPreset) {
        _state.update { it.copy(cropPreset = preset, resultUri = null) }
    }

    fun updateCropGeometry(scale: Float, panX: Float, panY: Float, rect: RectF?) {
        _state.update {
            it.copy(
                cropScale = scale,
                cropPanX = panX,
                cropPanY = panY,
                cropRect = rect,
                resultUri = null
            )
        }
    }

    fun setExpandedPreviewOpen(open: Boolean) {
        _state.update { it.copy(isExpandedPreviewOpen = open) }
    }

    private fun loadLogoBitmap() {
        val logo = _state.value.selectedLogo ?: return
        viewModelScope.launch {
            val bmp = withContext(Dispatchers.IO) {
                runCatching { LogoRepository.loadBitmap(getApplication(), logo) }.getOrNull()
            }
            logoBitmap = bmp
            if (bmp != null) {
                _state.update { it.copy(logoAspect = bmp.width.toFloat() / bmp.height.toFloat()) }
            }
        }
    }

    private fun firstFrame(uri: Uri): Bitmap? {
        val r = MediaMetadataRetriever()
        return try {
            r.setDataSource(getApplication(), uri)
            r.getFrameAtTime(0, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
        } catch (t: Throwable) {
            null
        } finally {
            r.release()
        }
    }

    fun export() {
        val s = _state.value
        val uri = s.mediaUri ?: return
        val logo = logoBitmap ?: return
        when (s.mediaType) {
            MediaType.IMAGE -> exportImage(uri, logo, s)
            MediaType.VIDEO -> exportVideo(uri, logo, s)
            null -> Unit
        }
    }

    private fun exportImage(uri: Uri, logo: Bitmap, s: UiState) {
        viewModelScope.launch {
            _state.update { it.copy(exporting = true, progress = 0) }
            try {
                val result = withContext(Dispatchers.Default) {
                    val composited = ImageWatermarker.compose(
                        getApplication(), uri, logo, s.position,
                        s.logoWidthFraction, s.offsetX, s.offsetY, s.tint.tintColor,
                        s.cropRect
                    )
                    val mime = getApplication<Application>().contentResolver.getType(uri).orEmpty()
                    val asPng = mime.contains("png") || mime.contains("webp")
                    val out = MediaSaver.saveImage(getApplication(), composited, asPng)
                    out to if (asPng) "image/png" else "image/jpeg"
                }
                _state.update {
                    it.copy(exporting = false, progress = 100, resultUri = result.first, resultMime = result.second)
                }
            } catch (t: Throwable) {
                _state.update { it.copy(exporting = false, error = t.message ?: "Export failed") }
            }
        }
    }

    private fun exportVideo(uri: Uri, logo: Bitmap, s: UiState) {
        _state.update { it.copy(exporting = true, progress = 0) }
        exportJob = try {
            VideoWatermarker.export(
                context = getApplication(),
                sourceUri = uri,
                logo = logo,
                position = s.position,
                logoWidthFraction = s.logoWidthFraction,
                offsetX = s.offsetX,
                offsetY = s.offsetY,
                tintColor = s.tint.tintColor,
                cropRect = s.cropRect,
                onProgress = { p -> _state.update { it.copy(progress = p) } },
                onSuccess = { file ->
                    viewModelScope.launch {
                        try {
                            val out = withContext(Dispatchers.IO) {
                                val u = MediaSaver.saveVideo(getApplication(), file, file.name)
                                file.delete()
                                u
                            }
                            _state.update {
                                it.copy(exporting = false, progress = 100, resultUri = out, resultMime = "video/mp4")
                            }
                        } catch (t: Throwable) {
                            _state.update { it.copy(exporting = false, error = t.message ?: "Saving failed") }
                        }
                    }
                },
                onError = { t ->
                    _state.update { it.copy(exporting = false, error = t.message ?: "Export failed") }
                },
            )
        } catch (t: Throwable) {
            _state.update { it.copy(exporting = false, error = t.message ?: "Export failed") }
            null
        }
    }

    fun cancelExport() {
        exportJob?.cancel()
        exportJob = null
        _state.update { it.copy(exporting = false, progress = 0) }
    }
}
