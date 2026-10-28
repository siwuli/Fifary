package io.github.fifary.nav

import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import io.github.fifary.ui.theme.Motion
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

sealed interface Screen {
    // 分类 = 目录。folder 是相对 vault 根的路径，"" 是根目录
    data class Notes(val folder: String) : Screen
    data class Editor(val path: String, val folder: String = "") : Screen
    data object Categories : Screen
    data object Settings : Screen
}

// 手写栈：为了拿到手势进度（NavHost 给不了 1:1 跟手）
class Navigator internal constructor(val stack: SnapshotStateList<Screen>) {

    internal val push = Animatable(1f)   // 1 = 上层页面已就位
    internal val back = Animatable(0f)   // 0..1 = 预测式返回手势进度
    internal val grow = Animatable(1f)   // 0..1 = 从「+」气泡长满全屏
    internal var fromBubble = false
    internal var scope: CoroutineScope? = null

    val canGoBack: Boolean get() = stack.size > 1

    fun open(screen: Screen, fromBubble: Boolean = false) {
        this.fromBubble = fromBubble
        stack.add(screen)
    }

    fun pop(): Boolean {
        if (stack.size <= 1) return false
        stack.removeAt(stack.lastIndex)
        return true
    }

    // 切分类：替换根页面并丢掉更深层，栈深度保持 1
    fun toRoot(screen: Screen) {
        while (stack.size > 1) stack.removeAt(stack.lastIndex)
        stack[0] = screen
    }

    // 从分组管理页选中某个分组：先播返回动画退出分组页，再换根内容
    fun selectCategory(folder: String) {
        val s = scope ?: return
        s.launch {
            back.animateTo(1f, Motion.BackSpring)
            toRoot(Screen.Notes(folder))
            back.snapTo(0f)
            push.snapTo(1f)
        }
    }

    // 顶栏返回：直接跑手势那套滑出动画（页面滑到屏幕外再出栈，没有跳变）
    fun back() {
        val s = scope ?: return
        if (!canGoBack) return
        s.launch {
            back.animateTo(1f, Motion.BackSpring)
            pop()
            back.snapTo(0f)
            push.snapTo(1f)
        }
    }
}

@Composable
fun rememberNavigator(): Navigator {
    val stack = remember { mutableStateListOf<Screen>(Screen.Notes("")) }
    return remember { Navigator(stack) }
}

@Composable
fun PageHost(navigator: Navigator, content: @Composable (Screen) -> Unit) {
    val stack = navigator.stack
    val push = navigator.push
    val back = navigator.back
    val grow = navigator.grow
    val scope = rememberCoroutineScope()
    navigator.scope = scope

    var depth by remember { mutableIntStateOf(stack.size) }

    // 前进：新页面入栈后播一次入栈动画
    LaunchedEffect(stack.size) {
        if (stack.size > depth) {
            if (navigator.fromBubble) {
                // 从「+」长出来：位置定死在那个气泡上，长满一整屏
                grow.snapTo(0f)
                push.snapTo(1f)
                grow.animateTo(1f, Motion.BubbleSpring)
            } else {
                push.snapTo(0f)
                push.animateTo(1f, Motion.PageSpring)
            }
        } else if (stack.size < depth) {
            push.snapTo(1f)
            grow.snapTo(1f)
        }
        depth = stack.size
    }

    // 预测式返回：progress 0→1 跟手；flow 正常结束 = 确认返回，取消 = 弹回
    PredictiveBackHandler(enabled = stack.size > 1) { progressFlow ->
        try {
            progressFlow.collect { event -> back.snapTo(event.progress) }
            popLast(stack)
            back.snapTo(0f)
            depth = stack.size
            push.snapTo(1f)
        } catch (c: CancellationException) {
            back.animateTo(0f, Motion.PageSpring)
            throw c
        }
    }

    val widthPx = with(LocalDensity.current) { LocalConfiguration.current.screenWidthDp.dp.toPx() }

    Box(Modifier.fillMaxSize()) {
        val count = stack.size
        val hasTop = count >= 2

        // 下层页面永远在同一个调用点：换个位置放它 = 整棵被销毁重建，
        // 列表的滚动位置和量出来的顶栏高度全丢，返回那一刻会往下跳一下
        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer {
                    if (!hasTop) return@graphicsLayer
                    val b = back.value
                    val scale = 0.94f + 0.06f * b
                    scaleX = scale
                    scaleY = scale
                    translationX = -widthPx * 0.10f * (1f - b)
                    alpha = 0.55f + 0.45f * b
                }
        ) { content(stack[(count - 2).coerceAtLeast(0)]) }

        if (hasTop) {
            // 上层页面：入栈动画 + 返回跟手，全部在 layer 阶段读状态 → 手势期间不重组
            Box(
                Modifier
                    .fillMaxSize()
                    .zIndex(1f)
                    .graphicsLayer {
                        val p = push.value
                        val b = back.value
                        val g = grow.value
                        val scale = (0.94f + 0.06f * p) * (1f - 0.06f * b)
                        scaleX = scale
                        scaleY = scale
                        // 跟手就是 1:1 跟着手指走，不加阻尼，否则"不跟手"
                        translationX = (1f - p) * widthPx * 0.18f + b * widthPx
                        alpha = ((0.5f + 0.5f * p) * (1f - 0.12f * b)).coerceIn(0f, 1f)
                        if (g < 1f) {
                            // 「+」气泡在屏幕右下角：以它为圆心的小圆长满整屏。内容不缩放，
                            // 只把一整页"揭"出来，看起来才是气泡变大，而不是小截图在放大
                            shape = BubbleReveal(g, size.width * 0.87f, size.height * 0.92f, 29.dp.toPx())
                            clip = true
                        } else if (b > 0.001f) {
                            shape = RoundedCornerShape(24.dp * b)
                            clip = true
                        }
                    }
            ) { content(stack[count - 1]) }
        }
    }
}

// 0 = 锚点上的一个小圆，1 = 整屏；圆角跟着一起收，贴到屏幕边时正好是方角
private class BubbleReveal(
    private val progress: Float,
    private val cx: Float,
    private val cy: Float,
    private val radius: Float,
) : Shape {
    override fun createOutline(
        size: Size,
        layoutDirection: LayoutDirection,
        density: Density,
    ): Outline {
        val t = progress.coerceIn(0f, 1f)
        fun mix(from: Float, to: Float) = from + (to - from) * t
        val rect = Rect(
            mix(cx - radius, 0f),
            mix(cy - radius, 0f),
            mix(cx + radius, size.width),
            mix(cy + radius, size.height),
        )
        return Outline.Rounded(RoundRect(rect, CornerRadius(mix(radius, 0f))))
    }
}

private fun popLast(stack: SnapshotStateList<Screen>) {
    if (stack.size > 1) stack.removeAt(stack.lastIndex)
}
