package io.github.fifary.data

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color

enum class ThemeMode { SYSTEM, LIGHT, DARK }

enum class ViewMode { LIST, GRID }

enum class SortMode { UPDATED, CREATED }

// 一套配色 = 页面底色 + 卡片色 + 强调色，日间/夜间各一组
@Immutable
data class Palette(
    val id: String,
    val name: String,
    val lightBg: Color,
    val lightCard: Color,
    val lightAccent: Color,
    val darkBg: Color,
    val darkCard: Color,
    val darkAccent: Color,
)

val Palettes = listOf(
    Palette(
        id = "amber", name = "活力橙",
        lightBg = Color(0xFFF2F3F5), lightCard = Color(0xFFFFFFFF), lightAccent = Color(0xFFFFB300),
        darkBg = Color(0xFF101113), darkCard = Color(0xFF1B1D20), darkAccent = Color(0xFFFFC233),
    ),
    Palette(
        id = "paper", name = "米白",
        lightBg = Color(0xFFF6F3EA), lightCard = Color(0xFFFFFDF7), lightAccent = Color(0xFFC29B45),
        darkBg = Color(0xFF14120D), darkCard = Color(0xFF211E17), darkAccent = Color(0xFFD9B667),
    ),
    Palette(
        id = "mint", name = "薄荷",
        lightBg = Color(0xFFEDF4F0), lightCard = Color(0xFFFFFFFF), lightAccent = Color(0xFF3FA37A),
        darkBg = Color(0xFF0D1411), darkCard = Color(0xFF18211D), darkAccent = Color(0xFF6FD1A6),
    ),
    Palette(
        id = "sky", name = "天蓝",
        lightBg = Color(0xFFECF2F9), lightCard = Color(0xFFFFFFFF), lightAccent = Color(0xFF4A8FD4),
        darkBg = Color(0xFF0C1117), darkCard = Color(0xFF171E26), darkAccent = Color(0xFF7FB8EF),
    ),
    Palette(
        id = "sakura", name = "樱粉",
        lightBg = Color(0xFFF9EEF1), lightCard = Color(0xFFFFFFFF), lightAccent = Color(0xFFE0698E),
        darkBg = Color(0xFF150D10), darkCard = Color(0xFF23181C), darkAccent = Color(0xFFF58FAE),
    ),
    Palette(
        id = "graphite", name = "石墨",
        lightBg = Color(0xFFEFEFF1), lightCard = Color(0xFFFFFFFF), lightAccent = Color(0xFF5A5F66),
        darkBg = Color(0xFF0F0F10), darkCard = Color(0xFF1A1A1C), darkAccent = Color(0xFF9BA1A8),
    ),
)

// 外部存储还没接，设置先落 SharedPreferences（量小、同步读、无依赖）
object SettingsStore {

    private const val FILE = "fifary_settings"
    private var prefs: SharedPreferences? = null

    var themeMode by mutableStateOf(ThemeMode.SYSTEM)
        private set
    var palette by mutableStateOf(Palettes.first())
        private set
    var viewMode by mutableStateOf(ViewMode.LIST)
        private set
    var sortMode by mutableStateOf(SortMode.UPDATED)
        private set

    fun load(context: Context) {
        if (prefs != null) return
        val p = context.getSharedPreferences(FILE, Context.MODE_PRIVATE)
        prefs = p
        themeMode = runCatching { ThemeMode.valueOf(p.getString("themeMode", null) ?: "") }
            .getOrDefault(ThemeMode.SYSTEM)
        palette = Palettes.firstOrNull { it.id == p.getString("palette", null) } ?: Palettes.first()
        viewMode = runCatching { ViewMode.valueOf(p.getString("viewMode", null) ?: "") }
            .getOrDefault(ViewMode.LIST)
        sortMode = runCatching { SortMode.valueOf(p.getString("sortMode", null) ?: "") }
            .getOrDefault(SortMode.UPDATED)
    }

    fun updateThemeMode(mode: ThemeMode) {
        themeMode = mode
        prefs?.edit()?.putString("themeMode", mode.name)?.apply()
    }

    fun updatePalette(value: Palette) {
        palette = value
        prefs?.edit()?.putString("palette", value.id)?.apply()
    }

    fun updateViewMode(mode: ViewMode) {
        viewMode = mode
        prefs?.edit()?.putString("viewMode", mode.name)?.apply()
    }

    fun updateSortMode(mode: SortMode) {
        sortMode = mode
        prefs?.edit()?.putString("sortMode", mode.name)?.apply()
    }
}
