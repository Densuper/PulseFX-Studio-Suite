package com.example.antigravityeq.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val DarkColorScheme = darkColorScheme(
    primary = PixelCyan,
    onPrimary = Color(0xFF003644),
    primaryContainer = PixelCyanContainer,
    onPrimaryContainer = PixelOnCyanContainer,
    secondary = PixelViolet,
    onSecondary = Color(0xFF28134E),
    secondaryContainer = PixelVioletContainer,
    tertiary = PixelAmber,
    onTertiary = Color(0xFF452B00),
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

private val LightColorScheme = androidx.compose.material3.lightColorScheme(
    primary = Color(0xFF00687A),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFA6EEFF),
    onPrimaryContainer = Color(0xFF001F26),
    secondary = Color(0xFF6750A4),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFEADDFF),
    tertiary = Color(0xFF825500),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFFFDDB3),
    background = Color(0xFFFBFCFE),
    onBackground = Color(0xFF191C1E),
    surface = Color(0xFFF1F4F9),
    onSurface = Color(0xFF191C1E),
    surfaceVariant = Color(0xFFDBE4E8),
    onSurfaceVariant = Color(0xFF3F484B),
    outline = Color(0xFF70797C),
    error = Color(0xFFBA1A1A)
)

@Composable
fun AntigravityEQTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true, // Conforms directly to Google Material 3 Dynamic Color standards
    content: @Composable () -> Unit,
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}

