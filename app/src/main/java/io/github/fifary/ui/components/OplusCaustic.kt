package io.github.fifary.ui.components

import android.graphics.Outline
import android.view.View
import android.view.ViewOutlineProvider
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.geometry.Size
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

    private val setColor by lazy {
        method("setOutlineCausticShadowColor", View::class.java, INT)
    }
    private val setLayout by lazy {
        method(
            "setOutlineCausticShadowLayout",
            View::class.java,
            INT,
            INT,
            INT,
            INT,
        )
    }
    private val setParams by lazy {
        method(
            "setOutlineCausticShadowParams",
            View::class.java,
            FLOAT,
            FLOAT,
            FLOAT,
            FLOAT,
            FLOAT,
            FLOAT,
        )
    }

    val available: Boolean get() = setColor != null

    fun apply(view: View, color: Color) {
        val set = setColor ?: return
        val d = view.resources.displayMetrics.density
        runCatching {
            set.invoke(null, view, color.toArgb())
            // level1 的布局位移（下 4.3dp）与 sdf 宽度（9.23dp），其余取 COUICausticShadowEffect 的默认值
            setLayout?.invoke(null, view, 0, 0, 0, (4.3f * d).toInt())
            setParams?.invoke(null, view, 9.23f * d, 0.6f, 0.6f, 18f, 0.45f, 0.6f)
        }
    }
}

// 透明的轮廓载体：系统把焦散画在这个 View 的 outline 外面，本体还是 Compose 画的
@Composable
internal fun CausticOutline(shape: Shape, color: Color, modifier: Modifier) {
    val density = LocalDensity.current
    val direction = LocalLayoutDirection.current
    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            View(ctx).apply {
                isEnabled = false
                outlineProvider = object : ViewOutlineProvider() {
                    override fun getOutline(view: View, outline: Outline) {
                        when (val o = shape.createOutline(Size(view.width.toFloat(), view.height.toFloat()), direction, density)) {
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
        },
        update = { v ->
            v.invalidateOutline()
            OplusCaustic.apply(v, color)
        },
    )
}

