package io.github.fifary.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.fifary.ui.theme.Motion

// 磨砂质感：半透明的片 + 软投影，表面近乎平，只有一点点上亮下暗。
// 透是关键：底色透过来，壳才像壳而不是贴纸。
@Composable
fun Modifier.bubble(
    shape: Shape,
    color: Color = MaterialTheme.colorScheme.surface,
    elevation: Dp = 10.dp,
): Modifier {
    val dark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    // 磨砂：中性表面透一点底子出来；彩色（强调色的 FAB、完成按钮）不掺水，
    // 一透就成粉彩，参考图里那几颗是实心的。Color.Transparent 乘完还是 0。
    val chroma = maxOf(color.red, color.green, color.blue) - minOf(color.red, color.green, color.blue)
    val glass = if (chroma > 0.08f) {
        color
    } else {
        color.copy(alpha = color.alpha * if (dark) 0.74f else 0.62f)
    }
    // 只在竖直方向留一点极淡的落差，不画高光球、不描亮边：
    // 参考 App 的按钮是平的磨砂片，一加球面高光就变成玻璃弹珠
    val gloss = remember(dark) {
        Brush.verticalGradient(
            0f to Color.White.copy(alpha = if (dark) 0.05f else 0.12f),
            0.6f to Color.Transparent,
            1f to Color.Black.copy(alpha = if (dark) 0.10f else 0.02f),
        )
    }
    return this
        .shadow(
            elevation = elevation,
            shape = shape,
            clip = false,
            ambientColor = Color.Black.copy(alpha = if (dark) 0.50f else 0.10f),
            spotColor = Color.Black.copy(alpha = if (dark) 0.60f else 0.16f),
        )
        .clip(shape)
        .background(glass)
        .drawBehind { drawRect(gloss) }
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
