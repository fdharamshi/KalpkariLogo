package com.kalpkari.watermark.ui

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.RectF
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.BoxWithConstraintsScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ColorLens
import androidx.compose.material.icons.filled.Crop
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import com.kalpkari.watermark.R
import com.kalpkari.watermark.domain.CropPreset
import com.kalpkari.watermark.domain.InstagramMargins
import com.kalpkari.watermark.domain.LogoAsset
import com.kalpkari.watermark.domain.LogoTint
import com.kalpkari.watermark.domain.MediaType
import com.kalpkari.watermark.domain.WatermarkPosition

enum class EditorTab(val label: String) {
    LOGOS("Logo"),
    COLOR("Color"),
    POSITION("Position"),
    ADJUST("Adjust"),
    CROP("Crop")
}

@Composable
fun WatermarkScreen(vm: WatermarkViewModel = viewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()
    val context = LocalContext.current

    val picker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickMultipleVisualMedia(),
    ) { uris: List<Uri> -> vm.onMediaPicked(uris) }

    val filePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri: Uri? -> uri?.let { vm.onMediaPicked(listOf(it)) } }

    var activeTab by remember { mutableStateOf(EditorTab.LOGOS) }
    var exoPlayer by remember { mutableStateOf<ExoPlayer?>(null) }
    var isPlaying by remember { mutableStateOf(true) }

    val mediaUri = state.mediaUri
    DisposableEffect(mediaUri) {
        if (mediaUri != null && state.mediaType == MediaType.VIDEO) {
            val player = ExoPlayer.Builder(context).build().apply {
                setMediaItem(MediaItem.fromUri(mediaUri))
                repeatMode = Player.REPEAT_MODE_ALL
                volume = 0f
                prepare()
                playWhenReady = isPlaying
            }
            exoPlayer = player
        } else {
            exoPlayer?.release()
            exoPlayer = null
        }
        onDispose {
            exoPlayer?.release()
            exoPlayer = null
        }
    }

    fun togglePlayPause() {
        isPlaying = !isPlaying
        exoPlayer?.let { player -> player.playWhenReady = isPlaying }
    }

    DialogOverlays(state, vm, context, exoPlayer)

    Column(
        Modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .background(MaterialTheme.colorScheme.background)
    ) {
        if (state.mediaUri == null) {
            LandingLayout(picker, filePicker)
        } else {
            EditorLayout(
                state = state,
                vm = vm,
                activeTab = activeTab,
                onTabSelect = { activeTab = it },
                exoPlayer = exoPlayer,
                isPlaying = isPlaying,
                togglePlayPause = ::togglePlayPause
            )
        }
    }
}

@Composable
private fun LandingLayout(
    picker: androidx.activity.result.ActivityResultLauncher<PickVisualMediaRequest>,
    filePicker: androidx.activity.result.ActivityResultLauncher<Array<String>>
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Image(
            painter = painterResource(R.drawable.kalpkari_lockup),
            contentDescription = "Kalpkari",
            modifier = Modifier.height(112.dp),
        )
        Text(
            "WATERMARK STUDIO",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        
        Spacer(Modifier.height(48.dp))

        Box(
            Modifier
                .fillMaxWidth()
                .height(280.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(16.dp)) {
                Image(
                    painter = painterResource(R.drawable.kalpkari_mark),
                    contentDescription = null,
                    modifier = Modifier.size(96.dp),
                )
                Spacer(Modifier.height(16.dp))
                Text(
                    "Pick images or videos to begin batch watermarking",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }

        Spacer(Modifier.height(32.dp))

        Button(
            onClick = {
                picker.launch(
                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo),
                )
            },
            modifier = Modifier.fillMaxWidth().height(48.dp)
        ) {
            Text("Pick from Gallery", style = MaterialTheme.typography.titleMedium)
        }
        
        Spacer(Modifier.height(12.dp))
        
        OutlinedButton(
            onClick = { filePicker.launch(arrayOf("image/*", "video/*")) },
            modifier = Modifier.fillMaxWidth().height(48.dp)
        ) {
            Text("Browse files", style = MaterialTheme.typography.titleMedium)
        }
    }
}

@Composable
private fun EditorLayout(
    state: UiState,
    vm: WatermarkViewModel,
    activeTab: EditorTab,
    onTabSelect: (EditorTab) -> Unit,
    exoPlayer: ExoPlayer?,
    isPlaying: Boolean,
    togglePlayPause: () -> Unit
) {
    var isPanelExpanded by remember { mutableStateOf(true) }

    // Backup crop states to support Discard/Cancel functionality
    var backupPreset by remember { mutableStateOf<CropPreset?>(null) }
    var backupScale by remember { mutableStateOf(1.0f) }
    var backupPanX by remember { mutableStateOf(0f) }
    var backupPanY by remember { mutableStateOf(0f) }
    var backupRect by remember { mutableStateOf<RectF?>(null) }

    LaunchedEffect(activeTab) {
        if (activeTab == EditorTab.CROP) {
            backupPreset = state.cropPreset
            backupScale = state.cropScale
            backupPanX = state.cropPanX
            backupPanY = state.cropPanY
            backupRect = state.cropRect
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        val preview = state.previewBitmap

        // 1. Preview Workspace (Fits area above the bottom panel overlay)
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .padding(
                    top = 56.dp,
                    bottom = if (isPanelExpanded) 264.dp else 80.dp
                ),
            contentAlignment = Alignment.Center
        ) {
            if (state.loadingPreview || preview == null) {
                BrandLoader(Modifier.fillMaxSize())
            } else {
                PreviewWorkspace(
                    preview = preview,
                    state = state,
                    vm = vm,
                    activeTab = activeTab,
                    exoPlayer = exoPlayer,
                    isPlaying = isPlaying,
                    togglePlayPause = togglePlayPause
                )
            }
        }

        // 2. Header Bar Overlay
        Row(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .height(56.dp)
                .background(MaterialTheme.colorScheme.background.copy(alpha = 0.9f))
                .padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val totalCount = state.mediaQueue.size
            val currentIndex = state.currentQueueIndex + 1

            IconButton(onClick = vm::clearMedia) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = MaterialTheme.colorScheme.primary
                )
            }
            
            Column(
                modifier = Modifier.weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "WATERMARK STUDIO",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                if (totalCount > 1) {
                    Text(
                        text = "Editing $currentIndex of $totalCount",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Button(
                onClick = vm::export,
                enabled = state.canExport,
                modifier = Modifier.padding(end = 8.dp)
            ) {
                Text("Export")
            }
        }

        // 3. Premium Glassmorphic Bottom Panel
        val chipColors = FilterChipDefaults.filterChipColors(
            containerColor = Color.White.copy(alpha = 0.12f),
            labelColor = Color.White,
            selectedContainerColor = MaterialTheme.colorScheme.primary,
            selectedLabelColor = MaterialTheme.colorScheme.onPrimary
        )

        Surface(
            tonalElevation = 8.dp,
            color = Color.Black.copy(alpha = 0.72f),
            contentColor = Color.White,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .clip(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
                .border(1.dp, Color.White.copy(alpha = 0.15f), RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp)
            ) {
                // Minimize drag line
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(24.dp)
                        .clickable { isPanelExpanded = !isPanelExpanded },
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp, 4.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(Color.White.copy(alpha = 0.4f))
                    )
                }

                if (isPanelExpanded) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(160.dp)
                            .padding(horizontal = 20.dp, vertical = 4.dp),
                        contentAlignment = Alignment.CenterStart
                    ) {
                        when (activeTab) {
                            EditorTab.LOGOS -> {
                                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                    SectionLabel("Select Logo")
                                    LogoRow(state.logos, state.selectedLogo, onSelect = vm::onLogoSelected)
                                }
                            }
                            EditorTab.COLOR -> {
                                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                    SectionLabel("Logo Color")
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        LogoTint.entries.forEach { tint ->
                                            FilterChip(
                                                selected = tint == state.tint,
                                                onClick = { vm.onTintSelected(tint) },
                                                label = { Text(tint.label) },
                                                colors = chipColors
                                            )
                                        }
                                    }
                                }
                            }
                            EditorTab.POSITION -> {
                                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                     SectionLabel("Quick Position")
                                     PositionRow(state.position, onSelect = vm::onPositionSelected)
                                }
                            }
                            EditorTab.ADJUST -> {
                                Column(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .verticalScroll(rememberScrollState()),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        SectionLabel("Size & Position Adjust")
                                        OutlinedButton(
                                            onClick = vm::resetOffsets,
                                            enabled = !state.exporting && (state.offsetX != 0f || state.offsetY != 0f),
                                            modifier = Modifier.height(30.dp)
                                        ) {
                                            Text("Reset", style = MaterialTheme.typography.bodySmall, color = Color.White)
                                        }
                                    }

                                    Column {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Text("Logo Size", style = MaterialTheme.typography.bodyMedium, color = Color.White.copy(alpha = 0.8f))
                                            Text("${(state.logoWidthFraction * 100).toInt()}%", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                                        }
                                        Slider(
                                            value = state.logoWidthFraction,
                                            onValueChange = vm::onLogoSizeChanged,
                                            valueRange = InstagramMargins.MIN_LOGO_WIDTH..InstagramMargins.MAX_LOGO_WIDTH,
                                            enabled = !state.exporting,
                                            colors = SliderDefaults.colors(thumbColor = Color.White, activeTrackColor = MaterialTheme.colorScheme.primary)
                                        )
                                    }

                                    Column {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Text("Horizontal Nudge", style = MaterialTheme.typography.bodyMedium, color = Color.White.copy(alpha = 0.8f))
                                            Text(String.format("%.0f%%", state.offsetX * 100), style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                                        }
                                        Slider(
                                            value = state.offsetX,
                                            onValueChange = { vm.onOffsetChanged(it, state.offsetY) },
                                            valueRange = -0.4f..0.4f,
                                            enabled = !state.exporting,
                                            colors = SliderDefaults.colors(thumbColor = Color.White, activeTrackColor = MaterialTheme.colorScheme.primary)
                                        )
                                    }

                                    Column {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Text("Vertical Nudge", style = MaterialTheme.typography.bodyMedium, color = Color.White.copy(alpha = 0.8f))
                                            Text(String.format("%.0f%%", state.offsetY * 100), style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                                        }
                                        Slider(
                                            value = state.offsetY,
                                            onValueChange = { vm.onOffsetChanged(state.offsetX, it) },
                                            valueRange = -0.4f..0.4f,
                                            enabled = !state.exporting,
                                            colors = SliderDefaults.colors(thumbColor = Color.White, activeTrackColor = MaterialTheme.colorScheme.primary)
                                        )
                                    }
                                }
                            }
                            EditorTab.CROP -> {
                                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                    SectionLabel("Crop Preset")
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        CropPreset.entries.forEach { preset ->
                                            FilterChip(
                                                selected = preset == state.cropPreset,
                                                onClick = {
                                                    vm.onCropPresetChanged(preset)
                                                },
                                                label = { Text(preset.label) },
                                                colors = chipColors
                                            )
                                        }
                                    }
                                    
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.End,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        OutlinedButton(
                                            onClick = {
                                                backupPreset?.let { vm.onCropPresetChanged(it) }
                                                vm.updateCropGeometry(backupScale, backupPanX, backupPanY, backupRect)
                                                onTabSelect(EditorTab.LOGOS)
                                            },
                                            modifier = Modifier.height(36.dp)
                                        ) {
                                            Text("Cancel", color = Color.White)
                                        }
                                        Spacer(Modifier.width(8.dp))
                                        Button(
                                            onClick = { onTabSelect(EditorTab.LOGOS) },
                                            modifier = Modifier.height(36.dp)
                                        ) {
                                            Text("Apply")
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                TabBar(activeTab = activeTab, onTabSelect = { tab ->
                    if (activeTab == tab) {
                        isPanelExpanded = !isPanelExpanded
                    } else {
                        onTabSelect(tab)
                        isPanelExpanded = true
                    }
                })
            }
        }
    }
}

@Composable
private fun TabBar(activeTab: EditorTab, onTabSelect: (EditorTab) -> Unit) {
    TabRow(
        selectedTabIndex = activeTab.ordinal,
        containerColor = Color.Transparent,
        contentColor = Color.White,
        modifier = Modifier.fillMaxWidth()
    ) {
        EditorTab.entries.forEach { tab ->
            Tab(
                selected = activeTab == tab,
                onClick = { onTabSelect(tab) },
                text = { Text(tab.label, style = MaterialTheme.typography.labelMedium, color = Color.White) },
                icon = {
                    val icon = when (tab) {
                        EditorTab.LOGOS -> Icons.Default.Image
                        EditorTab.COLOR -> Icons.Default.ColorLens
                        EditorTab.POSITION -> Icons.Default.Place
                        EditorTab.ADJUST -> Icons.Default.Tune
                        EditorTab.CROP -> Icons.Default.Crop
                    }
                    Icon(imageVector = icon, contentDescription = tab.label, tint = Color.White)
                }
            )
        }
    }
}

@Composable
private fun BoxWithConstraintsScope.PreviewWorkspace(
    preview: Bitmap,
    state: UiState,
    vm: WatermarkViewModel,
    activeTab: EditorTab,
    exoPlayer: ExoPlayer?,
    isPlaying: Boolean,
    togglePlayPause: () -> Unit
) {
    val mediaW = preview.width.toFloat()
    val mediaH = preview.height.toFloat()
    val mediaAspect = mediaW / mediaH
    val parentAspect = maxWidth.value / maxHeight.value

    val (defaultW, defaultH) = if (mediaAspect > parentAspect) {
        maxWidth to (maxWidth.value / mediaAspect).dp
    } else {
        (maxHeight.value * mediaAspect).dp to maxHeight
    }

    val cropPreset = state.cropPreset
    val (cropW, cropH) = if (cropPreset != CropPreset.ORIGINAL) {
        val cropAspect = cropPreset.aspectRatio!!
        if (cropAspect > mediaAspect) {
            defaultW to (defaultW.value / cropAspect).dp
        } else {
            (defaultH.value * cropAspect).dp to defaultH
        }
    } else {
        defaultW to defaultH
    }

    if (activeTab == EditorTab.CROP && state.cropPreset != CropPreset.ORIGINAL) {
        val minScale = maxOf(
            cropW.value / defaultW.value.coerceAtLeast(1f),
            cropH.value / defaultH.value.coerceAtLeast(1f)
        ).coerceAtLeast(1.0f)
        
        val scaleState = remember(state.cropPreset, state.mediaUri) { mutableStateOf(state.cropScale) }
        val offsetState = remember(state.cropPreset, state.mediaUri) { mutableStateOf(Offset(state.cropPanX, state.cropPanY)) }

        LaunchedEffect(scaleState.value, offsetState.value) {
            val rect = calculateNormalizedCropRect(
                defaultW = defaultW.value,
                defaultH = defaultH.value,
                cropW = cropW.value,
                cropH = cropH.value,
                scale = scaleState.value,
                panX = offsetState.value.x,
                panY = offsetState.value.y
            )
            vm.updateCropGeometry(scaleState.value, offsetState.value.x, offsetState.value.y, rect)
        }

        val density = LocalDensity.current

        Box(
            modifier = Modifier
                .size(defaultW, defaultH)
                .clip(RoundedCornerShape(12.dp))
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(12.dp)),
            contentAlignment = Alignment.Center
        ) {
            MediaViewport(
                background = preview,
                mediaType = state.mediaType ?: MediaType.IMAGE,
                exoPlayer = exoPlayer,
                scale = scaleState.value,
                panX = offsetState.value.x,
                panY = offsetState.value.y,
                defaultW = defaultW,
                defaultH = defaultH
            )

            // Transparent overlay box to intercept and handle gestures (prevents AndroidView touch consumption)
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(state.cropPreset, state.mediaUri) {
                        detectTransformGestures { _, pan, zoom, _ ->
                            val currentScale = scaleState.value
                            val currentOffset = offsetState.value
                            val newScale = (currentScale * zoom).coerceIn(minScale, 5.0f)
                            val maxPanX = (defaultW.value * newScale - cropW.value) / 2f
                            val maxPanY = (defaultH.value * newScale - cropH.value) / 2f
                            val newPanX = (currentOffset.x + pan.x / density.density).coerceIn(-maxPanX, maxPanX)
                            val newPanY = (currentOffset.y + pan.y / density.density).coerceIn(-maxPanY, maxPanY)
                            scaleState.value = newScale
                            offsetState.value = Offset(newPanX, newPanY)
                        }
                    }
            )

            val cropBoxLeft = (defaultW.value - cropW.value) / 2f
            val cropBoxTop = (defaultH.value - cropH.value) / 2f

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer(compositingStrategy = CompositingStrategy.Offscreen)
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val boxLeftPx = cropBoxLeft * density.density
                    val boxTopPx = cropBoxTop * density.density
                    val boxWPx = cropW.value * density.density
                    val boxHPx = cropH.value * density.density
                    
                    val boxSize = androidx.compose.ui.geometry.Size(boxWPx, boxHPx)
                    val boxTopLeft = Offset(boxLeftPx, boxTopPx)

                    drawRect(Color.Black.copy(alpha = 0.65f))
                    drawRect(
                        color = Color.Transparent,
                        topLeft = boxTopLeft,
                        size = boxSize,
                        blendMode = BlendMode.Clear
                    )
                    drawRect(
                        color = Color.White,
                        topLeft = boxTopLeft,
                        size = boxSize,
                        style = Stroke(width = 2.dp.toPx())
                    )
                    
                    val x1 = boxLeftPx + boxWPx / 3f
                    val x2 = boxLeftPx + 2f * boxWPx / 3f
                    val y1 = boxTopPx + boxHPx / 3f
                    val y2 = boxTopPx + 2f * boxHPx / 3f

                    drawLine(Color.White.copy(alpha = 0.4f), Offset(x1, boxTopPx), Offset(x1, boxTopPx + boxHPx), 1.dp.toPx())
                    drawLine(Color.White.copy(alpha = 0.4f), Offset(x2, boxTopPx), Offset(x2, boxTopPx + boxHPx), 1.dp.toPx())
                    drawLine(Color.White.copy(alpha = 0.4f), Offset(boxLeftPx, y1), Offset(boxLeftPx + boxWPx, y1), 1.dp.toPx())
                    drawLine(Color.White.copy(alpha = 0.4f), Offset(boxLeftPx, y2), Offset(boxLeftPx + boxWPx, y2), 1.dp.toPx())
                }
            }

            if (state.mediaType == MediaType.VIDEO && exoPlayer != null) {
                PlayPauseOverlay(isPlaying, togglePlayPause)
            }
        }
    } else {
        Box(
            modifier = Modifier
                .size(cropW, cropH)
                .clickable { vm.setExpandedPreviewOpen(true) },
            contentAlignment = Alignment.Center
        ) {
            WatermarkPreview(
                background = preview,
                logo = state.selectedLogo,
                position = state.position,
                logoAspect = state.logoAspect,
                logoWidthFraction = state.logoWidthFraction,
                offsetX = state.offsetX,
                offsetY = state.offsetY,
                tintColor = state.tint.tintColor,
                cropPreset = cropPreset,
                cropScale = state.cropScale,
                cropPanX = state.cropPanX,
                cropPanY = state.cropPanY,
                defaultW = defaultW,
                defaultH = defaultH,
                cropW = cropW,
                cropH = cropH,
                mediaType = state.mediaType ?: MediaType.IMAGE,
                exoPlayer = exoPlayer
            )

            if (state.mediaType == MediaType.VIDEO && exoPlayer != null) {
                PlayPauseOverlay(isPlaying, togglePlayPause)
            }
        }
    }
}

@Composable
private fun PlayPauseOverlay(isPlaying: Boolean, togglePlayPause: () -> Unit) {
    Box(
        modifier = Modifier
            .size(48.dp)
            .clip(RoundedCornerShape(24.dp))
            .background(Color.Black.copy(alpha = 0.5f))
            .clickable { togglePlayPause() },
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
            contentDescription = "Play/Pause",
            tint = Color.White,
            modifier = Modifier.size(28.dp)
        )
    }
}

@Composable
private fun MediaViewport(
    background: Bitmap,
    mediaType: MediaType,
    exoPlayer: ExoPlayer?,
    scale: Float,
    panX: Float,
    panY: Float,
    defaultW: Dp,
    defaultH: Dp,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        if (mediaType == MediaType.VIDEO && exoPlayer != null) {
            AndroidView(
                factory = { ctx ->
                    PlayerView(ctx).apply {
                        useController = false
                        player = exoPlayer
                        resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                    }
                },
                modifier = Modifier
                    .size(defaultW * scale, defaultH * scale)
                    .offset(panX.dp, panY.dp)
            )
        } else {
            Image(
                bitmap = background.asImageBitmap(),
                contentDescription = null,
                modifier = Modifier
                    .size(defaultW * scale, defaultH * scale)
                    .offset(panX.dp, panY.dp),
                contentScale = ContentScale.Fit
            )
        }
    }
}

@Composable
private fun WatermarkPreview(
    background: Bitmap,
    logo: LogoAsset?,
    position: WatermarkPosition,
    logoAspect: Float,
    logoWidthFraction: Float,
    offsetX: Float,
    offsetY: Float,
    tintColor: Int?,
    cropPreset: CropPreset,
    cropScale: Float,
    cropPanX: Float,
    cropPanY: Float,
    defaultW: Dp,
    defaultH: Dp,
    cropW: Dp,
    cropH: Dp,
    mediaType: MediaType,
    exoPlayer: ExoPlayer?,
    modifier: Modifier = Modifier
) {
    BoxWithConstraints(
        modifier
            .clip(RoundedCornerShape(12.dp))
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(12.dp)),
    ) {
        if (cropPreset != CropPreset.ORIGINAL) {
            val scaleFactor = maxWidth.value / cropW.value
            MediaViewport(
                background = background,
                mediaType = mediaType,
                exoPlayer = exoPlayer,
                scale = cropScale,
                panX = cropPanX * scaleFactor,
                panY = cropPanY * scaleFactor,
                defaultW = (defaultW.value * scaleFactor).dp,
                defaultH = (defaultH.value * scaleFactor).dp
            )
        } else {
            MediaViewport(
                background = background,
                mediaType = mediaType,
                exoPlayer = exoPlayer,
                scale = 1.0f,
                panX = 0f,
                panY = 0f,
                defaultW = maxWidth,
                defaultH = maxHeight
            )
        }

        if (logo != null) {
            val boxW = maxWidth
            val boxH = maxHeight
            val logoW = boxW * logoWidthFraction
            val logoH = logoW / logoAspect
            val baseLeft = when (position) {
                WatermarkPosition.TOP_LEFT, WatermarkPosition.BOTTOM_LEFT ->
                    boxW * InstagramMargins.horizontal
                WatermarkPosition.TOP_RIGHT, WatermarkPosition.BOTTOM_RIGHT ->
                    boxW - boxW * InstagramMargins.horizontal - logoW
                WatermarkPosition.CENTER_TOP -> (boxW - logoW) / 2
            }
            val baseTop = when (position) {
                WatermarkPosition.TOP_LEFT, WatermarkPosition.TOP_RIGHT, WatermarkPosition.CENTER_TOP ->
                    boxH * InstagramMargins.topMargin
                WatermarkPosition.BOTTOM_LEFT, WatermarkPosition.BOTTOM_RIGHT ->
                    boxH - boxH * InstagramMargins.bottomMargin - logoH
            }
            AsyncImage(
                model = "file:///android_asset/${logo.assetPath}",
                contentDescription = "Logo",
                colorFilter = tintColor?.let { ColorFilter.tint(Color(it)) },
                modifier = Modifier
                    .offset(baseLeft + boxW * offsetX, baseTop + boxH * offsetY)
                    .size(logoW, logoH),
            )
        }
    }
}

@Composable
private fun DialogOverlays(
    state: UiState,
    vm: WatermarkViewModel,
    context: Context,
    exoPlayer: ExoPlayer?
) {
    if (state.exporting) {
        Dialog(
            onDismissRequest = {},
            properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false)
        ) {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    verticalArrangement = Arrangement.spacedBy(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = if (state.mediaType == MediaType.VIDEO) "Exporting Video..." else "Exporting Image...",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )

                    Box(contentAlignment = Alignment.Center, modifier = Modifier.size(100.dp)) {
                        CircularProgressIndicator(
                            progress = { state.progress / 100f },
                            modifier = Modifier.size(88.dp),
                            strokeWidth = 6.dp,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = "${state.progress}%",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    OutlinedButton(
                        onClick = vm::cancelExport,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Cancel")
                    }
                }
            }
        }
    }

    val result = state.resultUri
    if (result != null) {
        Dialog(onDismissRequest = vm::dismissResult) {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "Saved to Gallery ✓",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )

                    Image(
                        painter = painterResource(R.drawable.kalpkari_mark),
                        contentDescription = null,
                        modifier = Modifier.size(80.dp),
                    )

                    val isNextAvailable = state.currentQueueIndex + 1 < state.mediaQueue.size
                    Text(
                        text = if (state.mediaType == MediaType.VIDEO)
                            "Saved in Movies/KalpkariWatermark"
                        else
                            "Saved in Pictures/KalpkariWatermark",
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        OutlinedButton(
                            onClick = vm::dismissResult,
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(if (isNextAvailable) "Next" else "Done")
                        }
                        Button(
                            onClick = { shareMedia(context, result, state.resultMime ?: "*/*") },
                            modifier = Modifier.weight(1.2f)
                        ) {
                            Text("Share")
                        }
                    }
                }
            }
        }
    }

    state.error?.let { msg ->
        Dialog(onDismissRequest = vm::dismissError) {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Text(
                        text = "Error",
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.error
                    )
                    Text(text = msg, style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.height(8.dp))
                    Button(
                        onClick = vm::dismissError,
                        modifier = Modifier.align(Alignment.End)
                    ) {
                        Text("Dismiss")
                    }
                }
            }
        }
    }

    if (state.isExpandedPreviewOpen && state.previewBitmap != null) {
        Dialog(
            onDismissRequest = { vm.setExpandedPreviewOpen(false) },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Surface(
                color = Color.Black,
                modifier = Modifier.fillMaxSize()
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .clickable { vm.setExpandedPreviewOpen(false) }
                        .padding(16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    val preview = state.previewBitmap
                    val mediaW = preview.width.toFloat()
                    val mediaH = preview.height.toFloat()
                    
                    BoxWithConstraints(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        val mediaAspect = mediaW / mediaH
                        val parentAspect = maxWidth.value / maxHeight.value

                        val (fitW, fitH) = if (mediaAspect > parentAspect) {
                            maxWidth to (maxWidth.value / mediaAspect).dp
                        } else {
                            (maxHeight.value * mediaAspect).dp to maxHeight
                        }

                        val cropPreset = state.cropPreset
                        val (croppedW, croppedH) = if (cropPreset != CropPreset.ORIGINAL) {
                            val cropAspect = cropPreset.aspectRatio!!
                            if (cropAspect > mediaAspect) {
                                fitW to (fitW.value / cropAspect).dp
                            } else {
                                (fitH.value * cropAspect).dp to fitH
                            }
                        } else {
                            fitW to fitH
                        }

                        WatermarkPreview(
                            background = preview,
                            logo = state.selectedLogo,
                            position = state.position,
                            logoAspect = state.logoAspect,
                            logoWidthFraction = state.logoWidthFraction,
                            offsetX = state.offsetX,
                            offsetY = state.offsetY,
                            tintColor = state.tint.tintColor,
                            cropPreset = cropPreset,
                            cropScale = state.cropScale,
                            cropPanX = state.cropPanX,
                            cropPanY = state.cropPanY,
                            defaultW = fitW,
                            defaultH = fitH,
                            cropW = croppedW, // In fullscreen lightbox, the crop box size matches the cropped container size!
                            cropH = croppedH,
                            mediaType = state.mediaType ?: MediaType.IMAGE,
                            exoPlayer = exoPlayer,
                            modifier = Modifier.size(croppedW, croppedH)
                        )
                    }

                    Text(
                        text = "Tap anywhere to close",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White.copy(alpha = 0.7f),
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(bottom = 32.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun BrandLoader(modifier: Modifier = Modifier) {
    Box(modifier, contentAlignment = Alignment.Center) {
        CircularProgressIndicator(
            modifier = Modifier.size(96.dp),
            strokeWidth = 2.dp,
            color = MaterialTheme.colorScheme.primary,
        )
        Image(
            painter = painterResource(R.drawable.kalpkari_mark),
            contentDescription = "Loading",
            modifier = Modifier.size(56.dp),
        )
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(text, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
}

@Composable
private fun LogoRow(logos: List<LogoAsset>, selected: LogoAsset?, onSelect: (LogoAsset) -> Unit) {
    LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        items(logos, key = { it.id }) { logo ->
            val isSel = logo.id == selected?.id
            AsyncImage(
                model = "file:///android_asset/${logo.assetPath}",
                contentDescription = logo.id,
                modifier = Modifier
                    .size(72.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color.White.copy(alpha = 0.12f))
                    .border(
                        width = if (isSel) 3.dp else 1.dp,
                        color = if (isSel) MaterialTheme.colorScheme.primary else Color.LightGray,
                        shape = RoundedCornerShape(12.dp),
                    )
                    .clickable { onSelect(logo) }
                    .padding(6.dp),
            )
        }
    }
}

@Composable
private fun PositionRow(
    selected: WatermarkPosition,
    onSelect: (WatermarkPosition) -> Unit
) {
    val colors = FilterChipDefaults.filterChipColors(
        containerColor = Color.White.copy(alpha = 0.12f),
        labelColor = Color.White,
        selectedContainerColor = MaterialTheme.colorScheme.primary,
        selectedLabelColor = MaterialTheme.colorScheme.onPrimary
    )
    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items(WatermarkPosition.entries.toList()) { pos ->
            FilterChip(
                selected = pos == selected,
                onClick = { onSelect(pos) },
                label = { Text(pos.label) },
                colors = colors
            )
        }
    }
}

private fun calculateNormalizedCropRect(
    defaultW: Float,
    defaultH: Float,
    cropW: Float,
    cropH: Float,
    scale: Float,
    panX: Float,
    panY: Float
): RectF {
    val imgW = defaultW * scale
    val imgH = defaultH * scale

    val cropLeft = (imgW - cropW) / 2f - panX
    val cropTop = (imgH - cropH) / 2f - panY

    val leftFraction = (cropLeft / imgW).coerceIn(0f, 1f)
    val rightFraction = ((cropLeft + cropW) / imgW).coerceIn(0f, 1f)
    val topFraction = (cropTop / imgH).coerceIn(0f, 1f)
    val bottomFraction = ((cropTop + cropH) / imgH).coerceIn(0f, 1f)

    return RectF(leftFraction, topFraction, rightFraction, bottomFraction)
}

private fun shareMedia(context: Context, uri: Uri, mime: String) {
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = mime
        putExtra(Intent.EXTRA_STREAM, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(intent, "Share via"))
}
