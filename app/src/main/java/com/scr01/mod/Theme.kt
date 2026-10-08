package com.scr01.mod

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.dp

private val AppTypography = Typography(
    headlineSmall = TextStyle(fontSize = 20.sp, lineHeight = 26.sp, fontWeight = FontWeight.Medium),
    titleLarge = TextStyle(fontSize = 16.sp, lineHeight = 22.sp, fontWeight = FontWeight.Medium),
    titleMedium = TextStyle(fontSize = 16.sp, lineHeight = 22.sp, fontWeight = FontWeight.Medium),
    bodyLarge = TextStyle(fontSize = 16.sp, lineHeight = 22.sp),
    bodyMedium = TextStyle(fontSize = 14.sp, lineHeight = 20.sp),
    bodySmall = TextStyle(fontSize = 12.sp, lineHeight = 17.sp),
    labelLarge = TextStyle(fontSize = 13.sp, lineHeight = 18.sp, fontWeight = FontWeight.Medium),
)

private val LightColors = lightColorScheme(
    primary = Color(0xFF3E6473), onPrimary = Color.White,
    primaryContainer = Color(0xFFDDEAF0), onPrimaryContainer = Color(0xFF244854),
    background = Color(0xFFF1F3F4), onBackground = Color(0xFF202629),
    surface = Color.White, onSurface = Color(0xFF202629),
    surfaceVariant = Color(0xFFE2E8EB), onSurfaceVariant = Color(0xFF56636A),
    secondaryContainer = Color(0xFFDDEAF0), onSecondaryContainer = Color(0xFF244854),
    outline = Color(0xFF77858D), outlineVariant = Color(0xFFD5DEE3),
)
private val DarkColors = darkColorScheme(
    primary = Color(0xFFAECFDB), onPrimary = Color(0xFF173642),
    primaryContainer = Color(0xFF2D4955), onPrimaryContainer = Color(0xFFDDEAF0),
    background = Color(0xFF101619), onBackground = Color(0xFFE2E8EB),
    surface = Color(0xFF1B2328), onSurface = Color(0xFFE2E8EB),
    surfaceVariant = Color(0xFF29343A), onSurfaceVariant = Color(0xFFAFBDC4),
    secondaryContainer = Color(0xFF2D4955), onSecondaryContainer = Color(0xFFDDEAF0),
    outline = Color(0xFF77858D), outlineVariant = Color(0xFF3B494F),
)

@Composable
fun Scr01ModTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) DarkColors else LightColors,
        typography = AppTypography,
        shapes = androidx.compose.material3.Shapes(
            small = AppUi.ControlShape,
            medium = AppUi.ControlShape,
            large = AppUi.CardShape,
            extraLarge = AppUi.DialogShape,
        ),
        content = content,
    )
}
