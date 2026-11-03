package io.github.fifary.ui.components

import android.content.Context
import android.graphics.Color
import android.graphics.Outline
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.ViewOutlineProvider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.viewinterop.AndroidView

// 参考 App 的悬浮气泡效果，是照它解包里的 COUIFABShadowUtils 一步步抄下来的：
//  1. View.setElevation(8dp) + setOutlineAmbient/SpotShadowColor(#1E000000)
//  2. new OplusView(view).setOverrideLightSourceGeometry(-1, -600dp, 0, 600dp, 0)  ← 光照几何，没有这步焦散不画
//  3. setOutlineCausticShadowColor(argb(153, 归一化后的强调色))
//     setOutlineCausticShadowLayout(4dp, 0, -4dp, 11.2dp)
//     setOutlineCausticShadowParams(14dp, 0.3, 0.2, 1.4, 0.45, 0.6)
// 数值全部来自参考包的资源（coui_shadow_*_caustic_fab / coui_caustic_layout_*_level6）。
// 只有 ColorOS 有这些系统类；拿不到就整块跳过。默认关闭，调试页可开；开启后 8 秒内没崩才算确认。
private val INT: Class<*> = Int::class.javaPrimitiveType!!
private val FLOAT: Class<*> = Float::class.javaPrimitiveType!!

internal object OplusCaustic {
    private const val PREFS = "caustic"
    private const val KEY_ON = "on"
    private const val KEY_UNCONFIRMED = "unconfirmed"

    private val util: Class<*>? by lazy {
        runCatching { Class.forName("com.oplus.view.material.OplusMaterialUtil") }.getOrNull()
    }
    private val oplusView: Class<*>? by lazy {
        runCatching { Class.forName("com.oplus.view.OplusView") }.getOrNull()
    }

    private fun method(name: String, vararg types: Class<*>): java.lang.reflect.Method? =
        runCatching { util?.getMethod(name, View::class.java, *types) }.getOrNull()

    private val setColor by lazy { method("setOutlineCausticShadowColor", INT) }
    private val setLayout by lazy { method("setOutlineCausticShadowLayout", INT, INT, INT, INT) }
    private val setParams by lazy {
        method("setOutlineCausticShadowParams", FLOAT, FLOAT, FLOAT, FLOAT, FLOAT, FLOAT)
    }
    private val lightGeometry by lazy {
        val cls = oplusView ?: return@lazy null
        runCatching {
            cls.getMethod("setOverrideLightSourceGeometry", FLOAT, INT, INT, INT, INT)
        }.getOrNull()
    }

    private fun prefs(ctx: Context) = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    var report by mutableStateOf("未探测")
        private set
    var enabled by mutableStateOf(false)
        private set

    fun boot(ctx: Context) {
        val sp = prefs(ctx)
        if (sp.getBoolean(KEY_ON, false) && sp.getBoolean(KEY_UNCONFIRMED, false)) {
            sp.edit().putBoolean(KEY_ON, false).putBoolean(KEY_UNCONFIRMED, false).apply()
            report = "上次开启后崩溃，已自动关闭"
        } else {
            report = if (util == null) "无系统类" else "未挂载"
        }
        enabled = sp.getBoolean(KEY_ON, false)
    }

    fun toggle(ctx: Context) {
        val sp = prefs(ctx)
        val on = !sp.getBoolean(KEY_ON, false)
        sp.edit().putBoolean(KEY_ON, on).putBoolean(KEY_UNCONFIRMED, on).apply()
        enabled = on
        if (!on) {
            report = "已关闭"
            return
        }
        report = "已开启，等待确认"
        Handler(Looper.getMainLooper()).postDelayed({
            prefs(ctx).edit().putBoolean(KEY_UNCONFIRMED, false).apply()
            report = "已开启（已确认）"
        }, 8000)
    }

    // 参考包 COUICausticShadowEffect.convertCausticShadowColor：把颜色压到亮度 80，免得焦散过曝
    private fun causticColor(argb: Int): Int {
        val r = Color.red(argb).toFloat()
        val g = Color.green(argb).toFloat()
        val b = Color.blue(argb).toFloat()
        val luma = 0.299f * r + 0.587f * g + 0.114f * b
        if (luma == 0f) return Color.parseColor("#828282")
        if (luma == 255f) return Color.parseColor("#0B0B0B")
        fun clamp(v: Float) = v.coerceIn(0f, 255f).toInt()
        return Color.rgb(clamp(r * 80f / luma), clamp(g * 80f / luma), clamp(b * 80f / luma))
    }

    fun apply(view: View, color: androidx.compose.ui.graphics.Color) {
        if (!enabled) return
        val set = setColor ?: return
        if (view.width <= 0 || view.height <= 0 || !view.isAttachedToWindow) return
        val d = view.resources.displayMetrics.density
        val e = causticColor(color.toArgb())
        report = runCatching {
            view.elevation = 8f * d
            view.outlineAmbientShadowColor = 0x1E000000
            view.outlineSpotShadowColor = 0x1E000000
            // 光照几何：没有这一笔，焦散只是被系统存下来，不会画
            val cls = oplusView
            if (cls != null) {
                val wrapper = cls.getConstructor(View::class.java).newInstance(view)
                lightGeometry?.invoke(wrapper, -1f, (-600f * d).toInt(), 0, (600f * d).toInt(), 0)
            }
            set.invoke(null, view, Color.argb(153, Color.red(e), Color.green(e), Color.blue(e)))
            setLayout?.invoke(null, view, (4f * d).toInt(), 0, (-4f * d).toInt(), (11.2f * d).toInt())
            setParams?.invoke(null, view, 14f * d, 0.3f, 0.2f, 1.4f, 0.45f, 0.6f)
            view.invalidate()
            "已挂载(光照=" + if (oplusView == null) "无" else "有" + ")"
        }.getOrElse { "异常:" + (it.message ?: it.javaClass.simpleName).take(40) }
    }
}

// 轮廓载体：本体还是 Compose 画的，这层只把 outline 和系统光影借过来
@Composable
internal fun CausticOutline(shape: Shape, color: androidx.compose.ui.graphics.Color, modifier: Modifier) {
    val density = LocalDensity.current
    val direction = LocalLayoutDirection.current
    val currentColor by rememberUpdatedState(color)
    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            View(ctx).apply {
                isClickable = false
                isFocusable = false
                background = android.graphics.drawable.GradientDrawable().apply { setColor(0x01FFFFFF) }
                addOnLayoutChangeListener { v, _, _, _, _, _, _, _, _ ->
                    OplusCaustic.apply(v, currentColor)
                }
                outlineProvider = object : ViewOutlineProvider() {
                    override fun getOutline(view: View, outline: Outline) {
                        runCatching {
                            val size = Size(view.width.toFloat(), view.height.toFloat())
                            when (val o = shape.createOutline(size, direction, density)) {
                                is androidx.compose.ui.graphics.Outline.Rounded -> {
                                    val r = o.roundRect
                                    outline.setRoundRect(
                                        r.left.toInt(),
                                        r.top.toInt(),
                                        r.right.toInt(),
                                        r.bottom.toInt(),
                                        r.topLeftCornerRadius.x,
                                    )
                                }
                                is androidx.compose.ui.graphics.Outline.Rectangle ->
                                    outline.setRect(0, 0, view.width, view.height)
                                else -> Unit
                            }
                        }
                    }
                }
            }
        },
    )
}

