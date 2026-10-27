package io.github.fifary.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size as GeomSize
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import io.github.fifary.ui.theme.Elev
import io.github.fifary.ui.theme.Radius
import io.github.fifary.ui.theme.Size

// 页面里反复用到的小控件集中在这里，避免每个页面各写一套 Material 默认样式

@Composable
fun CircleButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    subtle: Boolean = false,
    content: @Composable () -> Unit,
) {
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = if (subtle) Color.Transparent else MaterialTheme.colorScheme.surfaceVariant,
        contentColor = MaterialTheme.colorScheme.onSurface,
        modifier = modifier.size(Size.touch),
    ) {
        Box(contentAlignment = Alignment.Center) { content() }
    }
}

@Composable
fun CircleIconButton(icon: ImageVector, label: String, onClick: () -> Unit, subtle: Boolean = false) {
    CircleButton(onClick = onClick, subtle = subtle) {
        Icon(icon, contentDescription = label, modifier = Modifier.size(20.dp))
    }
}

// 胶囊：选中白底+轻阴影，未选中浅灰底
@Composable
fun Capsule(
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable RowScope.() -> Unit,
) {
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = if (selected) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.surfaceVariant,
        contentColor = if (selected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
        shadowElevation = if (selected) Elev.chip else 0.dp,
        modifier = modifier.height(Size.chipHeight),
    ) {
        Row(
            Modifier.padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            content = content,
        )
    }
}

@Composable
fun TextPill(text: String, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surface,
        modifier = Modifier.height(Size.chipHeight),
    ) {
        Row(Modifier.padding(horizontal = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(text, style = MaterialTheme.typography.labelMedium)
        }
    }
}

// 分组图标：参考 App 里那个小笔记本（正片 + 书脊），比纯色块更像"分组"
@Composable
fun NotebookGlyph(size: Int = 14) {
    val body = MaterialTheme.colorScheme.primary
    val spine = lerp(body, Color.Black, 0.28f)
    Canvas(Modifier.size(size.dp)) {
        val w = this.size.width
        val h = this.size.height
        val r = CornerRadius(w * 0.24f)
        drawRoundRect(body, Offset(w * 0.04f, 0f), GeomSize(w * 0.92f, h), r)
        drawRoundRect(
            spine,
            Offset(w * 0.20f, h * 0.14f),
            GeomSize(w * 0.08f, h * 0.72f),
            CornerRadius(w * 0.04f),
        )
    }
}

// 分类前的小色块：代替图标，颜色取强调色
@Composable
fun AccentDot(modifier: Modifier = Modifier, size: Int = 14) {
    Box(
        modifier
            .size(size.dp)
            .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(5.dp))
    )
}

@Composable
fun SelectCheck(selected: Boolean) {
    if (selected) {
        Box(
            Modifier
                .size(Size.check)
                .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(Radius.check)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Filled.Check,
                contentDescription = "已选",
                tint = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.size(14.dp),
            )
        }
    } else {
        Box(
            Modifier
                .size(Size.check)
                .background(Color.Transparent, RoundedCornerShape(Radius.check))
                .then(
                    Modifier.background(Color.Transparent)
                )
        ) {
            Surface(
                shape = RoundedCornerShape(Radius.check),
                color = Color.Transparent,
                border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.outline),
                modifier = Modifier.size(Size.check),
            ) {}
        }
    }
}
