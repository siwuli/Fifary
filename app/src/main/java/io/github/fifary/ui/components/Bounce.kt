package io.github.fifary.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.LocalOverscrollFactory
import androidx.compose.foundation.OverscrollEffect
import androidx.compose.foundation.OverscrollFactory
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

// 参考 App 的 com.nearme.note.view.refresh.BounceLayout：
//  · 阻尼系数 2.5（位移 = 手指位移 / 2.5）
//  · 最大拖动距离 pull_refresh_down_fragment_max_drag_distance 150dip + 24dip 头部留白
//  · 松手用 Scroller + PathInterpolator(0.3, 0, 0.1, 1) 回弹，不是弹簧
//  · 上下两头都能拉：到顶还往下拉 = 内容下移；到底还往上拉 = 内容上移
// shortcut: 只做整块平移，不做毛边拉伸/下拉刷新指示器。
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun BouncingBox(modifier: Modifier = Modifier, content: @Composable BoxScope.() -> Unit) {
    val pull = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    val limit = with(LocalDensity.current) { 174.dp.toPx() }
    val connection = remember {
        object : NestedScrollConnection {
            override fun onPostScroll(
                consumed: Offset,
                available: Offset,
                source: NestedScrollSource,
            ): Offset {
                if (source != NestedScrollSource.UserInput) return Offset.Zero
                // available.y > 0 是到顶后继续往下拉，< 0 是到底后继续往上拉
                val next = (pull.value + available.y / 2.5f).coerceIn(-limit, limit)
                if (next != pull.value) scope.launch { pull.snapTo(next) }
                return Offset.Zero
            }

            override suspend fun onPreFling(available: Velocity): Velocity {
                if (pull.value == 0f) return Velocity.Zero
                pull.animateTo(
                    0f,
                    tween(durationMillis = 250, easing = CubicBezierEasing(0.3f, 0f, 0.1f, 1f)),
                )
                return available
            }
        }
    }
    CompositionLocalProvider(LocalOverscrollFactory provides NoStretchFactory) {
        Box(
            modifier
                .clipToBounds()
                .nestedScroll(connection)
                .graphicsLayer { translationY = pull.value },
            content = content,
        )
    }
}

// 关掉平台拉伸，否则我们平移、平台缩放，两边各动各的
@OptIn(ExperimentalFoundationApi::class)
private val NoStretchFactory: OverscrollFactory = object : OverscrollFactory {
    override fun hashCode(): Int = System.identityHashCode(this)

    override fun equals(other: Any?): Boolean = this === other

    private val effect = object : OverscrollEffect {
        override fun applyToScroll(
            delta: Offset,
            source: NestedScrollSource,
            performScroll: (Offset) -> Offset,
        ): Offset = performScroll(delta)

        override suspend fun applyToFling(
            velocity: Velocity,
            performFling: suspend (Velocity) -> Velocity,
        ) {
            performFling(velocity)
        }

        override val isInProgress: Boolean get() = false
    }

    override fun createOverscrollEffect(): OverscrollEffect = effect
}
