package io.github.fifary.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
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

// 平台自带的边缘效果只拉伸内容，顶栏不跟着动，滑到头会跟顶栏脱开。
// 这里换成一整块橡皮筋：内容和顶栏一起按阻尼下移，松手弹回。
// shortcut: 只回弹顶部，到底硬停；真要底部回弹再加。
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun BouncingBox(modifier: Modifier = Modifier, content: @Composable BoxScope.() -> Unit) {
    val pull = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    val limit = with(LocalDensity.current) { 120.dp.toPx() }
    val connection = remember {
        object : NestedScrollConnection {
            override fun onPostScroll(
                consumed: Offset,
                available: Offset,
                source: NestedScrollSource,
            ): Offset {
                if (source != NestedScrollSource.UserInput) return Offset.Zero
                // 只有到顶还继续往下拉时 available.y 才是负的，剩下的方向一律不动
                val next = (pull.value - available.y * 0.45f).coerceIn(0f, limit)
                if (next != pull.value) scope.launch { pull.snapTo(next) }
                return Offset.Zero
            }

            override suspend fun onPreFling(available: Velocity): Velocity {
                if (pull.value == 0f) return Velocity.Zero
                pull.animateTo(
                    0f,
                    spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessMediumLow),
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
