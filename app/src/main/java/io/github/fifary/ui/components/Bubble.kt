package io.github.fifary.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.fifary.ui.theme.Motion

// 玻璃质感：半透明的片 + 软投影 + 一道斜向反光 + 一圈薄薄的玻璃边。
// 表面是平的（不做球面高光），透才是关键：底色透过来，壳才像壳而不是贴纸。
@Composable
fun Modifier.bubble(
    shape: Shape,
    color: Color = MaterialTheme.colorScheme.surface,
    elevation: Dp = 10.dp,
): Modifier {
    val dark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    // 玻璃：中性表面明显是透的，后面的底色/内容透得出来；彩色（强调色的 FAB、完成按钮）
    // 不掺水，一透就成粉彩。Color.Transparent 乘完还是 0。
    val chroma = maxOf(color.red, color.green, color.blue) - minOf(color.red, color.green, color.blue)
    val tinted = chroma > 0.08f
    val glass = if (tinted) color else color.copy(alpha = color.alpha * if (dark) 0.80f else 0.78f)
    // 一道斜向的反光（不是球面的圆斑），加上玻璃边：上沿一道亮边、下沿一道暗边。
    // 只有这一斜一薄边，表面仍是平的，不会变成弹珠
    val sheen = remember(dark) {
        Brush.linearGradient(
            0f to Color.White.copy(alpha = if (dark) 0.14f else 0.32f),
            0.55f to Color.Transparent,
            1f to Color.Black.copy(alpha = if (dark) 0.12f else 0.05f),
            start = Offset.Zero,
            end = Offset.Infinite,
        )
    }
    // 下沿内侧一道很淡的反光：光穿过玻璃底边的样子，玻璃的"厚度"靠它
    val innerGlow = remember(dark) {
        Brush.verticalGradient(
            0.82f to Color.Transparent,
            1f to Color.White.copy(alpha = if (dark) 0.10f else 0.34f),
        )
    }
    val edge = remember(dark) {
        Brush.verticalGradient(
            0f to Color.White.copy(alpha = if (dark) 0.22f else 1f),
            0.5f to Color.White.copy(alpha = if (dark) 0.04f else 0.10f),
            1f to Color.Black.copy(alpha = if (dark) 0.22f else 0.10f),
        )
    }
    return this
        .shadow(
            elevation = elevation,
            shape = shape,
            clip = false,
            // 悬浮感靠投影：参考 App 的悬浮条/气泡用的是 #52000000~#8a000000 这一档深阴影，
            // 浅投影只会像贴纸边上一圈脏
            ambientColor = Color.Black.copy(alpha = if (dark) 0.55f else 0.18f),
            spotColor = Color.Black.copy(alpha = if (dark) 0.75f else 0.36f),
        )
        .clip(shape)
        .background(glass)
        .drawBehind {
            drawRect(sheen)
            // 底下这道反光只给玻璃用：彩色的 FAB 加上去会泛白发粉
            if (!tinted) drawRect(innerGlow)
        }
        .border(1.dp, edge, shape)
}

@Composable
fun Bubble(
    shape: Shape,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.surface,
    elevation: Dp = 10.dp,
    contentAlignment: Alignment = Alignment.Center,
    enabled: Boolean = true,
    onClick: (() -> Unit)? = null,
    content: @Composable BoxScope.() -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    // 按下去是被压扁：横着鼓、竖着扁，松手由弹簧弹回（果冻的形变是各向异性的，等比缩放只会像缩小）
    val sx by animateFloatAsState(if (pressed) 1.06f else 1f, Motion.JellySpring, label = "jellyX")
    val sy by animateFloatAsState(if (pressed) 0.92f else 1f, Motion.JellySpring, label = "jellyY")
    Box(
        modifier
            .graphicsLayer { alpha = if (enabled) 1f else 0.38f }
            // 形变层必须排在 bubble 前面：链上越靠前的修饰符才是外层，
            // 放后面只能缩到里面的图标，壳本身不动
            .graphicsLayer {
                scaleX = sx
                scaleY = sy
            }
            .bubble(shape, color, elevation)
            .then(
                if (onClick != null) {
                    Modifier.clickable(
                        interactionSource = interaction,
                        indication = LocalIndication.current,
                        enabled = enabled,
                        onClick = onClick,
                    )
                } else {
                    Modifier
                }
            ),
        contentAlignment = contentAlignment,
        content = content,
    )
}
