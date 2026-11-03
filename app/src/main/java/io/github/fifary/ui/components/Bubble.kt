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
    elevation: Dp = 10.dp,
): Modifier {
    val dark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val gloss = remember(dark) {
        Brush.verticalGradient(
            0f to Color.White.copy(alpha = if (dark) 0.07f else 0.30f),
            0.65f to Color.Transparent,
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
        .background(color)
        .drawBehind { drawRect(gloss) }
        .then(if (dark) Modifier.border(1.dp, Color.White.copy(alpha = 0.08f), shape) else Modifier)
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
