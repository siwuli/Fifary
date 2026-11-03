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
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.fifary.ui.theme.Motion

// 透光气泡：本体是透的（白色只压 28%），底下的字看得见；
// 关键是本体*外面*的光影：贴着下沿有一道被聚焦透过去的高光，外圈再压一层散阴影，
// 这样才像一颗悬在页面之上的玻璃泡，而不是画在页面上的色块。
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
    val glass = if (tinted) color else color.copy(alpha = color.alpha * if (dark) 0.35f else 0.28f)
    // 两肋的阴影：光从正面来，两侧的边厚、透过的光少
    val sides = remember(dark) {
        Brush.horizontalGradient(
            0f to Color.Black.copy(alpha = if (dark) 0.30f else 0.12f),
            0.24f to Color.Transparent,
            0.76f to Color.Transparent,
            1f to Color.Black.copy(alpha = if (dark) 0.30f else 0.12f),
        )
    }
    // 上沿一点亮，别抢下面那团聚焦光
    val sheen = remember(dark) {
        Brush.verticalGradient(
            0f to Color.White.copy(alpha = if (dark) 0.16f else 0.30f),
            0.45f to Color.Transparent,
        )
    }
    val edge = remember(dark) {
        Brush.verticalGradient(
            0f to Color.White.copy(alpha = if (dark) 0.16f else 0.55f),
            0.5f to Color.Transparent,
            1f to Color.White.copy(alpha = if (dark) 0.10f else 0.45f),
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
        // 画在本体外面（必须先于 clip）：光影都落在按钮之外
        .drawBehind {
            val w = size.width
            val h = size.height
            val unit = if (w > h) w else h
            // 外圈散阴影：比本体大一圈，重心在下沿
            drawRect(
                Brush.radialGradient(
                    0f to Color.Black.copy(alpha = if (dark) 0.55f else 0.16f),
                    0.55f to Color.Black.copy(alpha = if (dark) 0.22f else 0.06f),
                    1f to Color.Transparent,
                    center = Offset(w * 0.5f, h * 0.80f),
                    radius = unit * 0.66f,
                ),
                topLeft = Offset(-w * 0.3f, -h * 0.25f),
                size = Size(w * 1.6f, h * 1.75f),
            )
            // 贴下沿落一团透过来的聚焦光：亮斑在按钮外面，底边最亮
            drawRect(
                Brush.radialGradient(
                    0f to Color.White.copy(alpha = if (dark) 0.30f else 0.95f),
                    0.45f to Color.White.copy(alpha = if (dark) 0.12f else 0.32f),
                    1f to Color.Transparent,
                    center = Offset(w * 0.5f, h * 1.06f),
                    radius = unit * 0.42f,
                ),
                topLeft = Offset(-w * 0.15f, h * 0.2f),
                size = Size(w * 1.3f, h * 1.4f),
            )
        }
        .clip(shape)
        .background(glass)
        .drawBehind {
            drawRect(sheen)
            drawRect(sides)
            // 泡体内部只留很淡的一点聚焦光，亮的是外面那圈
            drawRect(
                Brush.radialGradient(
                    0f to Color.White.copy(alpha = if (tinted) 0.22f else if (dark) 0.10f else 0.28f),
                    1f to Color.Transparent,
                    center = Offset(size.width * 0.5f, size.height * 0.95f),
                    radius = (if (size.width > size.height) size.width else size.height) * 0.40f,
                )
            )

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
