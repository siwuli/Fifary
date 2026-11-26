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

enum class AppLang { SYSTEM, ZH, EN }

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
    var language by mutableStateOf(AppLang.SYSTEM)
        private set

    // 空 = 默认位置（内部存储）。换过位置就存绝对路径
    var vaultPath by mutableStateOf("")
        private set

    var davUrl by mutableStateOf("")
        private set
    var davUser by mutableStateOf("")
        private set
    // shortcut: WebDAV 密码明文存在 SharedPreferences 里，拿到本机文件就能读到；
    // 要防这个得再套一层 Keystore，等真有人拿它同步敏感内容再说
    var davPass by mutableStateOf("")
        private set
    var davAuto by mutableStateOf(false)
        private set

    // 分组显示顺序，用户拖动排的。不在里面的（新分组）排在后面
    var folderOrder by mutableStateOf<List<String>>(emptyList())
        private set

    // 只存盐和被密码包住的数据密钥，数据密钥本身解不出来就不落盘
    var secretSalt by mutableStateOf<String?>(null)
        private set
    var secretWrapped by mutableStateOf<String?>(null)
        private set
    // 锁的输入方式（文本密码 / 数字键盘 / 九点图形）和 PIN 位数，只为决定用哪块键盘
    var secretMode by mutableStateOf(LockMode.TEXT)
        private set
    var secretPinLen by mutableStateOf(0)
        private set
    // 指纹包住的同一把数据密钥（Keystore GCM，每次都要系统鉴权）
    var secretBio by mutableStateOf<String?>(null)
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
        language = runCatching { AppLang.valueOf(p.getString("language", null) ?: "") }
            .getOrDefault(AppLang.SYSTEM)
        vaultPath = p.getString("vaultPath", "") ?: ""
        davUrl = p.getString("davUrl", "") ?: ""
        davUser = p.getString("davUser", "") ?: ""
        davPass = p.getString("davPass", "") ?: ""
        davAuto = p.getBoolean("davAuto", false)
        folderOrder = (p.getString("folderOrder", "") ?: "").split("\n").filter { it.isNotBlank() }
        secretSalt = p.getString("secretSalt", null)
        secretWrapped = p.getString("secretWrapped", null)
        secretMode = runCatching { LockMode.valueOf(p.getString("secretMode", null) ?: "") }
            .getOrDefault(LockMode.TEXT)
        secretPinLen = p.getInt("secretPinLen", 0)
        // 老格式的指纹 blob（别的 Keystore 钥匙包的）解不开，直接丢掉，别等人去点才发现
        secretBio = p.getString("secretBio", null)?.takeIf { Secret.bioLooksValid(it) }
        if (p.getString("secretBio", null) != null && secretBio == null) {
            p.edit()?.remove("secretBio")?.apply()
        }
    }

    fun updateSecret(salt: String, wrapped: String, mode: LockMode, pinLen: Int) {
        secretSalt = salt
        secretWrapped = wrapped
        secretMode = mode
        secretPinLen = pinLen
        prefs?.edit()
            ?.putString("secretSalt", salt)
            ?.putString("secretWrapped", wrapped)
            ?.putString("secretMode", mode.name)
            ?.putInt("secretPinLen", pinLen)
            ?.apply()
    }

    fun updateSecretBio(blob: String?) {
        secretBio = blob
        prefs?.edit()?.putString("secretBio", blob)?.apply()
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

    fun updateLanguage(value: AppLang) {
        language = value
        prefs?.edit()?.putString("language", value.name)?.apply()
    }

    fun updateVaultPath(path: String) {
        vaultPath = path
        prefs?.edit()?.putString("vaultPath", path)?.apply()
    }

    fun updateDav(url: String, user: String, pass: String, auto: Boolean) {
        davUrl = url
        davUser = user
        davPass = pass
        davAuto = auto
        prefs?.edit()
            ?.putString("davUrl", url)
            ?.putString("davUser", user)
            ?.putString("davPass", pass)
            ?.putBoolean("davAuto", auto)
            ?.apply()
    }

    fun updateFolderOrder(order: List<String>) {
        folderOrder = order
        prefs?.edit()?.putString("folderOrder", order.joinToString("\n"))?.apply()
    }
}
