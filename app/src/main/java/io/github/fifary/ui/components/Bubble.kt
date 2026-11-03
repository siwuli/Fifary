package io.github.fifary.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

// 气泡质感：软投影 + 顶部高光（玻璃感），深色下再补一圈细描边把边撑出来。
// 参考 App 的悬浮胶囊不是硬贴纸：阴影很散、表面从上到下有一点点亮到暗的落差。
@Composable
fun Modifier.bubble(
    shape: Shape,
    color: Color = MaterialTheme.colorScheme.surface,
    elevation: Dp = 12.dp,
): Modifier {
    val dark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    // 彩色气泡（琥珀色 FAB、完成按钮）上白色高光减半，不然顶上一片发白像褪色
    val chroma = maxOf(color.red, color.green, color.blue) - minOf(color.red, color.green, color.blue)
    val tinted = chroma > 0.08f
    val shine = if (tinted) 0.45f else 1f
    // 上沿亮、下沿一点点暗，表面才有从光到影的落差；原来是整片均亮的平涂
    val gloss = remember(dark, shine) {
        Brush.verticalGradient(
            0f to Color.White.copy(alpha = (if (dark) 0.09f else 0.34f) * shine),
            0.55f to Color.Transparent,
            1f to Color.Black.copy(alpha = (if (dark) 0.10f else 0.04f) * shine),
        )
    }
    // 上沿一道极细的高光压边，光是从上面来的那种感觉
    val rim = remember(dark, shine) {
        Brush.verticalGradient(
            0f to Color.White.copy(alpha = (if (dark) 0.10f else 0.55f) * shine),
            0.35f to Color.Transparent,
        )
    }
    return this
        .shadow(
            elevation = elevation,
            shape = shape,
            clip = false,
            // 投影再散一点、深一点：10dp 那档在灰底上几乎看不出来，气泡就贴住了
            ambientColor = Color.Black.copy(alpha = if (dark) 0.55f else 0.14f),
            spotColor = Color.Black.copy(alpha = if (dark) 0.70f else 0.24f),
        )
        .clip(shape)
        .background(color)
        .drawBehind {
            drawRect(gloss)
            drawRect(rim)
        }
        .then(if (dark) Modifier.border(1.dp, Color.White.copy(alpha = 0.10f), shape) else Modifier)
}

@Composable
fun Bubble(
    shape: Shape,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.surface,
    elevation: Dp = 12.dp,
    contentAlignment: Alignment = Alignment.Center,
    enabled: Boolean = true,
    onClick: (() -> Unit)? = null,
    content: @Composable BoxScope.() -> Unit,
) {
    Box(
        modifier
            .bubble(shape, color, elevation)
            .graphicsLayer { alpha = if (enabled) 1f else 0.38f }
            .then(
                if (onClick != null) {
                    Modifier.clickable(enabled = enabled, onClick = onClick)
                } else {
                    Modifier
                }
            ),
        contentAlignment = contentAlignment,
        content = content,
    )
}
