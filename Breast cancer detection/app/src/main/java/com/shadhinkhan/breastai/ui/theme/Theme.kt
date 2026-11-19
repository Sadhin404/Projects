package com.shadhinkhan.breastai.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/** Minimal premium palette: calm paper light theme, deep-ink dark theme,
 *  one deep-teal primary. Semantic class colours live in core/Constants. */
private val LightColors = lightColorScheme(
    primary = Color(0xFF0E7490),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFDFF2F8),
    onPrimaryContainer = Color(0xFF083344),
    secondary = Color(0xFF7C5CD6),
    onSecondary = Color.White,
    background = Color(0xFFF5F7F9),
    onBackground = Color(0xFF15222B),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF15222B),
    surfaceVariant = Color(0xFFEDF1F4),
    onSurfaceVariant = Color(0xFF5A6B75),
    outline = Color(0xFFCBD6DD),
    outlineVariant = Color(0xFFE2EAF0),
    error = Color(0xFFDC2626),
    onError = Color.White,
    errorContainer = Color(0xFFFDECEC),
    onErrorContainer = Color(0xFF7F1D1D),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF67BFD9),
    onPrimary = Color(0xFF06222E),
    primaryContainer = Color(0xFF0E2A38),
    onPrimaryContainer = Color(0xFFBEE7F2),
    secondary = Color(0xFFA78BFA),
    onSecondary = Color(0xFF1E1040),
    background = Color(0xFF070B12),
    onBackground = Color(0xFFE9EFF3),
    surface = Color(0xFF0E1620),
    onSurface = Color(0xFFE9EFF3),
    surfaceVariant = Color(0xFF182230),
    onSurfaceVariant = Color(0xFF9DB2C0),
    outline = Color(0xFF33434F),
    outlineVariant = Color(0xFF1E2C3A),
    error = Color(0xFFF87171),
    onError = Color(0xFF2A0A0A),
    errorContainer = Color(0xFF3A1518),
    onErrorContainer = Color(0xFFFECACA),
)

private val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(22.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

@Composable
fun BreastAITheme(darkTheme: Boolean, content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        shapes = AppShapes,
        content = content,
    )
}
