package io.github.fifary.ui.components

import android.graphics.Outline
import android.graphics.drawable.GradientDrawable
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
// com.oplus.view.material.OplusMaterialUtil.setOutlineCausticShadow*（要求 ColorOS >= 40.12，
// 参数取自 coui_caustic_color_*、coui_caustic_layout_*）。这个系统类不在 App 里，
// 所以只有 ColorOS 借得到；拿不到就整块不挂，其它系统保持原来的气泡样式。
private val INT: Class<*> = Int::class.javaPrimitiveType!!
private val FLOAT: Class<*> = Float::class.javaPrimitiveType!!

internal object OplusCaustic {
    private val util: Class<*>? by lazy {
        runCatching { Class.forName("com.oplus.view.material.OplusMaterialUtil") }.getOrNull()
    }

    private fun method(name: String, vararg types: Class<*>) =
        runCatching { util?.getMethod(name, *types) }.getOrNull()

    private val setColor by lazy { method("setOutlineCausticShadowColor", View::class.java, INT) }
    private val setLayout by lazy { method("setOutlineCausticShadowLayout", View::class.java, INT, INT, INT, INT) }
    private val setParams by lazy {
        method("setOutlineCausticShadowParams", View::class.java, FLOAT, FLOAT, FLOAT, FLOAT, FLOAT, FLOAT)
    }

    val available: Boolean get() = setColor != null

    // 真机上没效果时靠它定位：类在不在、方法名对不对、调用有没有抛
    var report by mutableStateOf("未尝试")
        private set

    private fun causticMethods(): String = runCatching {
        util?.methods?.map { it.name }?.filter { it.contains("Caustic", ignoreCase = true) }?.distinct()
            ?.joinToString(",").orEmpty()
    }.getOrNull().orEmpty()

    fun apply(view: View, color: Color) {
        val set = setColor
        if (set == null) {
            report = if (util == null) "无系统类" else "无方法:" + causticMethods().take(60)
            return
        }
        val d = view.resources.displayMetrics.density
        report = runCatching {
            set.invoke(null, view, color.toArgb())
            // level1 的布局位移（下 4.3dp）与 sdf 宽度（9.23dp），其余取 COUICausticShadowEffect 的默认值
            setLayout?.invoke(null, view, 0, 0, 0, (4.3f * d).toInt())
            setParams?.invoke(null, view, 9.23f * d, 0.6f, 0.6f, 18f, 0.45f, 0.6f)
            "ok"
        }.getOrElse { "异常:" + (it.message ?: it.javaClass.simpleName).take(60) }
    }
}

// 透明的轮廓载体：系统把焦散画在这个 View 的 outline 外面，本体还是 Compose 画的。
// 背景给 1/255 的白——纯透明 View 有些实现直接不进渲染，光就挂不上去。
@Composable
internal fun CausticOutline(shape: Shape, color: Color, modifier: Modifier) {
    val density = LocalDensity.current
    val direction = LocalLayoutDirection.current
    // 布局完成后再挂一次：组合阶段 View 还是 0 尺寸，用 0 尺寸挂上去等于没挂
    val latest by rememberUpdatedState(color)
    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            View(ctx).apply {
                isEnabled = false
                background = GradientDrawable().apply { setColor(0x01FFFFFF) }
                addOnLayoutChangeListener { v, _, _, _, _, _, _, _, _ -> OplusCaustic.apply(v, latest) }
                outlineProvider = object : ViewOutlineProvider() {
                    override fun getOutline(view: View, outline: Outline) {
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
                                (view.background as? GradientDrawable)?.cornerRadius = r.topLeftCornerRadius.x
                            }
                            is androidx.compose.ui.graphics.Outline.Rectangle ->
                                outline.setRect(0, 0, view.width, view.height)
                            else -> Unit
                        }
                    }
                }
            }
        },
        update = { v ->
            v.invalidateOutline()
            OplusCaustic.apply(v, color)
        },
    )
}
