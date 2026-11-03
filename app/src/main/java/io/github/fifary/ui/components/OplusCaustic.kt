package io.github.fifary.ui.components

import android.content.Context
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.viewinterop.AndroidView

// 参考 App 那圈「按钮外面下方透过去的高光 + 两侧阴影」是 ColorOS 系统的焦散阴影：
// 解包 com.coui.appcompat.causticshadow.COUICausticShadowEffect 里，它反射
// com.oplus.view.material.OplusMaterialUtil.setOutlineCausticShadow*。
// 真机上这个类和方法都在（调试页能看到），但直接调会闪退，所以：
//  · 默认关，只有用户在调试页手动打开才调；
//  · 打开时先写"未确认"，活过 8 秒才转成"已确认"；下次启动如果看到"未确认"，说明上次崩了，
//    自动关掉并记录下来——这样即使系统接口有毒，也只是崩一次，不会变成开不了机。
private val INT: Class<*> = Int::class.javaPrimitiveType!!
private val FLOAT: Class<*> = Float::class.javaPrimitiveType!!

internal object OplusCaustic {
    private const val PREFS = "caustic"
    private const val KEY_ON = "on"
    private const val KEY_UNCONFIRMED = "unconfirmed"

    private val util: Class<*>? by lazy {
        runCatching { Class.forName("com.oplus.view.material.OplusMaterialUtil") }.getOrNull()
    }
    private val setColor by lazy { method("setOutlineCausticShadowColor", INT) }
    private val getColor by lazy {
        runCatching { util?.getMethod("getOutlineCausticShadowColor", View::class.java) }.getOrNull()
    }
    private val setLayout by lazy { method("setOutlineCausticShadowLayout", INT, INT, INT, INT) }
    private val setParams by lazy {
        method("setOutlineCausticShadowParams", FLOAT, FLOAT, FLOAT, FLOAT, FLOAT, FLOAT)
    }

    private fun method(name: String, vararg types: Class<*>): java.lang.reflect.Method? =
        runCatching { util?.getMethod(name, View::class.java, *types) }.getOrNull()

    private fun prefs(ctx: Context) = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    var report by mutableStateOf("未探测")
        private set
    var enabled by mutableStateOf(false)
        private set

    private var appContext: Context? = null

    fun boot(ctx: Context) {
        appContext = ctx.applicationContext
        val sp = prefs(ctx)
        if (sp.getBoolean(KEY_ON, false) && sp.getBoolean(KEY_UNCONFIRMED, false)) {
            // 上次开了以后没活过 8 秒 = 崩了，直接关掉
            sp.edit().putBoolean(KEY_ON, false).putBoolean(KEY_UNCONFIRMED, false).apply()
            report = "上次开启后崩溃，已自动关闭"
        }
        enabled = sp.getBoolean(KEY_ON, false)
        if (util == null) report = "无系统类"
    }

    fun toggle(ctx: Context) {
        val sp = prefs(ctx)
        val on = !sp.getBoolean(KEY_ON, false)
        sp.edit()
            .putBoolean(KEY_ON, on)
            .putBoolean(KEY_UNCONFIRMED, on)
            .apply()
        enabled = on
        if (!on) {
            report = "已关闭"
            return
        }
        report = "已开启，等待确认"
        // 活下来就算确认：8 秒后把"未确认"清掉
        Handler(Looper.getMainLooper()).postDelayed({
            prefs(ctx).edit().putBoolean(KEY_UNCONFIRMED, false).apply()
            report = "已开启（已确认）"
        }, 8000)
    }

    fun apply(view: View, color: Color) {
        if (!enabled) return
        val set = setColor ?: return
        if (view.width <= 0 || view.height <= 0 || !view.isAttachedToWindow) return
        val d = view.resources.displayMetrics.density
        runCatching {
            // 先配 sdf，再上色，最后给位移；顺序照 COUICausticShadowEffect 里那几个 setter 的用法
            setParams?.invoke(null, view, 9.23f * d, 0.6f, 0.6f, 18f, 0.45f, 0.6f)
            set.invoke(null, view, color.toArgb())
            setLayout?.invoke(null, view, 0, 0, 0, (4.3f * d).toInt())
            // 回读一次：系统认了才有意义，没认说明接口对第三方是空的
            val want = color.toArgb()
            val got = runCatching { getColor?.invoke(null, view) as? Int }.getOrNull()
            report = when (got) {
                null -> "已设置,无回读"
                want -> "已生效"
                else -> "回读=" + Integer.toHexString(got)
            }
        }.getOrElse { report = "异常:" + (it.message ?: it.javaClass.simpleName).take(40) }
    }
}

// 透明的轮廓载体：系统把焦散画在这个 View 的 outline 外面，本体还是 Compose 画的
@Composable
internal fun CausticOutline(shape: Shape, color: Color, modifier: Modifier) {
    val density = LocalDensity.current
    val direction = LocalLayoutDirection.current
    val currentColor by rememberUpdatedState(color)
    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            View(ctx).apply {
                isEnabled = false
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

