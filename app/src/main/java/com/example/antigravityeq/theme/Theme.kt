package com.example.antigravityeq.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

private val DarkColorScheme = darkColorScheme(
    primary = PixelCyan,
    onPrimary = PixelBackground,
    primaryContainer = PixelCyanContainer,
    onPrimaryContainer = PixelOnCyanContainer,
    secondary = PixelViolet,
    onSecondary = PixelBackground,
    secondaryContainer = PixelVioletContainer,
    tertiary = PixelAmber,
    onTertiary = PixelBackground,
    tertiaryContainer = PixelAmberContainer,
    background = PixelBackground,
    onBackground = PixelTextPrimary,
    surface = PixelSurface,
    onSurface = PixelTextPrimary,
    surfaceVariant = PixelSurfaceHigh,
    onSurfaceVariant = PixelTextSecondary,
    outline = PixelSurfaceBorder,
    error = PixelError
)

@Composable
fun AntigravityEQTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false, // Preserve polished Google Pixel dark matte/glassmorphic palette
    content: @Composable () -> Unit,
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        else -> DarkColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}

