package com.kalpkari.watermark.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

// Kalpkari brand palette.
val Rust = Color(0xFF7D3208)
val RustDark = Color(0xFF5E2606)
val RustSurface = Color(0xFF8A3A10)
val Cream = Color(0xFFDFD3C2)
val CreamDim = Color(0xFFB7A892)

private val KalpkariColors = darkColorScheme(
    primary = Cream,
    onPrimary = Rust,
    primaryContainer = RustSurface,
    onPrimaryContainer = Cream,
    secondary = CreamDim,
    onSecondary = Rust,
    secondaryContainer = RustSurface,
    onSecondaryContainer = Cream,
    background = Rust,
    onBackground = Cream,
    surface = Rust,
    onSurface = Cream,
    surfaceVariant = RustSurface,
    onSurfaceVariant = CreamDim,
    outline = CreamDim,
    outlineVariant = RustDark,
    error = Color(0xFFF2B8A2),
    onError = RustDark,
)

// Elegant, wide letter-spacing to echo the wordmark.
private val KalpkariType = Typography().run {
    copy(
        headlineSmall = headlineSmall.copy(
            fontWeight = FontWeight.Light,
            letterSpacing = 6.sp,
        ),
        titleMedium = titleMedium.copy(
            fontWeight = FontWeight.Normal,
            letterSpacing = 2.sp,
        ),
        labelLarge = TextStyle(
            fontWeight = FontWeight.Medium,
            fontSize = 14.sp,
            letterSpacing = 1.5.sp,
        ),
    )
}

@Composable
fun KalpkariTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = KalpkariColors,
        typography = KalpkariType,
        content = content,
    )
}
