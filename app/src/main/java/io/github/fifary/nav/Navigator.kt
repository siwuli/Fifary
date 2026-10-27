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
import androidx.compose.ui.graphics.graphicsLayer
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
    internal var scope: CoroutineScope? = null

    val canGoBack: Boolean get() = stack.size > 1

    fun open(screen: Screen) {
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
    val scope = rememberCoroutineScope()
    navigator.scope = scope

    var depth by remember { mutableIntStateOf(stack.size) }

    // 前进：新页面入栈后播一次入栈动画
    LaunchedEffect(stack.size) {
        if (stack.size > depth) {
            push.snapTo(0f)
            push.animateTo(1f, Motion.PageSpring)
        } else if (stack.size < depth) {
            push.snapTo(1f)
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
        if (count >= 2) {
            val base = stack[count - 2]
            val top = stack[count - 1]

            // 下层页面：随手指被"推"回来一点
            Box(
                Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        val b = back.value
                        val scale = 0.94f + 0.06f * b
                        scaleX = scale
                        scaleY = scale
                        translationX = -widthPx * 0.10f * (1f - b)
                        alpha = 0.55f + 0.45f * b
                    }
            ) { content(base) }

            // 上层页面：入栈动画 + 返回跟手，全部在 layer 阶段读状态 → 手势期间不重组
            Box(
                Modifier
                    .fillMaxSize()
                    .zIndex(1f)
                    .graphicsLayer {
                        val p = push.value
                        val b = back.value
                        val scale = (0.94f + 0.06f * p) * (1f - 0.06f * b)
                        scaleX = scale
                        scaleY = scale
                        // 跟手就是 1:1 跟着手指走，不加阻尼，否则"不跟手"
                        translationX = (1f - p) * widthPx * 0.18f + b * widthPx
                        alpha = ((0.5f + 0.5f * p) * (1f - 0.12f * b)).coerceIn(0f, 1f)
                        if (b > 0.001f) {
                            shape = RoundedCornerShape(24.dp * b)
                            clip = true
                        }
                    }
            ) { content(top) }
        } else {
            content(stack[0])
        }
    }
}

private fun popLast(stack: SnapshotStateList<Screen>) {
    if (stack.size > 1) stack.removeAt(stack.lastIndex)
}
