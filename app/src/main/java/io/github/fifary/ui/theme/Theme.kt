package io.github.fifary.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.fifary.data.Palette
import io.github.fifary.data.SettingsStore
import io.github.fifary.data.ThemeMode

private val Ink = Color(0xFF1A1B1D)
private val Outline = Color(0xFF8B9099)

private fun lightScheme(p: Palette): ColorScheme = lightColorScheme(
    primary = p.lightAccent,
    onPrimary = Color.White,
    primaryContainer = lerp(p.lightAccent, Color.White, 0.72f),
    onPrimaryContainer = lerp(p.lightAccent, Ink, 0.7f),
    secondary = p.lightAccent,
    background = p.lightBg,
    onBackground = Ink,
    surface = p.lightCard,
    onSurface = Ink,
    surfaceVariant = lerp(p.lightBg, Color.Black, 0.05f),
    onSurfaceVariant = Color(0xFF5B5F66),
    outline = Outline,
    outlineVariant = lerp(p.lightBg, Color.Black, 0.12f),
)

private fun darkScheme(p: Palette): ColorScheme = darkColorScheme(
    primary = p.darkAccent,
    onPrimary = Color(0xFF20180A),
    primaryContainer = lerp(p.darkAccent, p.darkCard, 0.72f),
    onPrimaryContainer = lerp(p.darkAccent, Color.White, 0.55f),
    secondary = p.darkAccent,
    background = p.darkBg,
    onBackground = Color(0xFFE9EAEC),
    surface = p.darkCard,
    onSurface = Color(0xFFE9EAEC),
    surfaceVariant = lerp(p.darkBg, Color.White, 0.09f),
    onSurfaceVariant = Color(0xFFB6BAC0),
    outline = Color(0xFF8B9099),
    outlineVariant = lerp(p.darkBg, Color.White, 0.16f),
)

// 正文 16sp / 行高 1.7（§8.6）
private val AppTypography = Typography(
    headlineLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.ExtraBold,
        fontSize = 38.sp,
        lineHeight = 46.sp,
        letterSpacing = (-0.3).sp,
    ),
    headlineMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Bold,
        fontSize = 28.sp,
        lineHeight = 34.sp,
    ),
    titleLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.SemiBold,
        fontSize = 21.sp,
        lineHeight = 28.sp,
    ),
    titleMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.SemiBold,
        fontSize = 19.sp,
        lineHeight = 26.sp,
    ),
    bodyLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontSize = 16.sp,
        lineHeight = 27.sp,
    ),
    bodyMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontSize = 15.sp,
        lineHeight = 25.sp,
    ),
    labelMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontSize = 14.sp,
        lineHeight = 19.sp,
    ),
    labelSmall = TextStyle(
        fontFamily = FontFamily.Default,
        fontSize = 12.sp,
        lineHeight = 16.sp,
    ),
)

// 卡片 20 / 胶囊 full / 输入 12（对齐参考图）
private val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(20.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

@Composable
fun FifaryTheme(content: @Composable () -> Unit) {
    val systemDark = isSystemInDarkTheme()
    val dark = when (SettingsStore.themeMode) {
        ThemeMode.SYSTEM -> systemDark
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    val palette = SettingsStore.palette
    MaterialTheme(
        colorScheme = if (dark) darkScheme(palette) else lightScheme(palette),
        typography = AppTypography,
        shapes = AppShapes,
        content = content,
    )
}
