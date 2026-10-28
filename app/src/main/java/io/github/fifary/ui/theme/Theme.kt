package io.github.fifary.ui.theme

import android.app.Activity
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Surface
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import io.github.fifary.data.Palette
import io.github.fifary.ui.applyLang
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
    // 深色下面一档要更暗（浅色那边是比卡片更深的灰）：胶囊选中=卡片色、未选中=更暗，
    // 否则选中的那颗反而比没选中的暗，看着像被按下去了
    surfaceVariant = lerp(p.darkCard, Color.Black, 0.38f),
    onSurfaceVariant = Color(0xFFB6BAC0),
    outline = Color(0xFF8B9099),
    outlineVariant = lerp(p.darkBg, Color.White, 0.16f),
)

// 字号全部取参考 App 的 COUI 字阶（couiText*）：页面标题 22 / 弹层 20 / 小标题 18 / 强调 16 / 正文 14 / 说明 12
private val AppTypography = Typography(
    headlineLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.SemiBold,
        fontSize = 22.sp,
        lineHeight = 26.sp,
    ),
    headlineMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Medium,
        fontSize = 20.sp,
        lineHeight = 22.sp,
    ),
    titleLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Medium,
        fontSize = 18.sp,
        lineHeight = 21.sp,
    ),
    titleMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Medium,
        fontSize = 16.sp,
        lineHeight = 18.sp,
    ),
    titleSmall = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
        lineHeight = 15.sp,
    ),
    bodyLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontSize = 16.sp,
        lineHeight = 18.sp,
    ),
    bodyMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontSize = 14.sp,
        lineHeight = 15.sp,
    ),
    bodySmall = TextStyle(
        fontFamily = FontFamily.Default,
        fontSize = 12.sp,
        lineHeight = 12.sp,
    ),
    labelMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontSize = 14.sp,
        lineHeight = 15.sp,
    ),
    labelSmall = TextStyle(
        fontFamily = FontFamily.Default,
        fontSize = 12.sp,
        lineHeight = 12.sp,
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
    // 状态栏/导航栏的图标颜色只认系统那边设的，跟 Compose 里的深浅不一定同一步，
    // 得自己按当前的深浅主题改一次，不然浅色栏配白图标就看不见了
    val view = LocalView.current
    val window = (LocalContext.current as? Activity)?.window
    if (window != null && !view.isInEditMode) {
        SideEffect {
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = !dark
                isAppearanceLightNavigationBars = !dark
            }
        }
    }
    val palette = SettingsStore.palette
    // 语言也挂在主题这里读：设置里一改，整棵树跟着重组，所有 S() 拿到新语言
    applyLang(SettingsStore.language)
    CompositionLocalProvider(LocalDarkTheme provides dark) {
        MaterialTheme(
            colorScheme = if (dark) darkScheme(palette) else lightScheme(palette),
            typography = AppTypography,
            shapes = AppShapes,
        ) {
            // 铺满一层的底色：之前页面各自不画背景，深色模式下露的是系统窗口的白底
            Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                content()
            }
        }
    }
}

// 深浅主题由设置决定，不一定等于系统的夜间模式（编辑页的 WebView 要跟着这个走）
val LocalDarkTheme = staticCompositionLocalOf { false }

