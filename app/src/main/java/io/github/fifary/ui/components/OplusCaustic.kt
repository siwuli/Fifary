package io.github.fifary.ui.components

import android.graphics.Outline
import android.view.View
import android.view.ViewOutlineProvider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.viewinterop.AndroidView

// 参考 App 那圈「按钮外面下方透过去的高光 + 两侧阴影」是 ColorOS 系统的焦散阴影：
// 解包 com.coui.appcompat.causticshadow.COUICausticShadowEffect 里，它反射
// com.oplus.view.material.OplusMaterialUtil.setOutlineCausticShadow*（要求 ColorOS >= 40.12）。
// 上一版直接调系统方法会让真机闪退，所以现在只探测不调用：把类在不在、
// 有哪些 Caustic 方法写进诊断（设置 → 调试），确认后再接。
internal object OplusCaustic {
    private val util: Class<*>? by lazy {
        runCatching { Class.forName("com.oplus.view.material.OplusMaterialUtil") }.getOrNull()
    }

    var report by mutableStateOf("未探测")
        private set

    fun probe() {
        if (report != "未探测") return
        val cls = util ?: run {
            report = "无系统类"
            return
        }
        val names = runCatching {
            cls.methods.map { it.name }.filter { it.contains("Caustic", ignoreCase = true) }.distinct()
        }.getOrNull().orEmpty()
        report = "类在,方法=" + if (names.isEmpty()) "无" else names.joinToString("/")
    }
}

// 只把气泡轮廓算给系统用，不调用任何系统方法；出异常也不影响绘制
@Composable
internal fun CausticOutline(shape: Shape, modifier: Modifier) {
    val density = LocalDensity.current
    val direction = LocalLayoutDirection.current
    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            View(ctx).apply {
                isEnabled = false
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
        update = { OplusCaustic.probe() },
    )
}
