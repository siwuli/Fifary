package io.github.fifary.ui.settings

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.dp
import io.github.fifary.ui.components.CircleButton
import io.github.fifary.ui.components.springPressable
import io.github.fifary.ui.theme.Radius
import io.github.fifary.ui.theme.Space

// 设置各页共用的骨架：顶部返回气泡 + 大标题 + 一组组卡片
@Composable
fun SettingsPage(
    title: String,
    onBack: () -> Unit,
    bottom: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Scaffold(containerColor = MaterialTheme.colorScheme.background) { inner ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(inner)
                .statusBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Space.l)
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(Space.m),
        ) {
            Row(
                Modifier.fillMaxWidth().padding(top = Space.xs, bottom = Space.s),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CircleButton(onClick = onBack) { ChevronLeft() }
                Spacer(Modifier.size(Space.s))
                Text(title, style = MaterialTheme.typography.headlineLarge)
            }
            content()
            bottom?.invoke()
        }
    }
}

@Composable
fun SectionLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = Space.xxs, top = Space.xs),
    )
}

@Composable
fun Group(content: @Composable ColumnScope.() -> Unit) {
    Surface(
        shape = RoundedCornerShape(Radius.card),
        color = MaterialTheme.colorScheme.surface,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(content = content)
    }
}

// onClick 为空 = 这一行只显示值，不带箭头也不可点
@Composable
fun RowItem(
    title: String,
    value: String? = null,
    arrow: Boolean = true,
    onClick: (() -> Unit)? = null,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.springPressable(onClick = onClick) else Modifier)
            .padding(horizontal = Space.l, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.weight(1f))
        if (value != null) {
            Text(
                text = value,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (onClick != null && arrow) {
            Icon(
                Icons.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.outline,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

@Composable
fun SwitchItem(title: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .springPressable(onClick = { onChange(!checked) })
            .padding(horizontal = Space.l, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
fun Rule() {
    HorizontalDivider(
        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f),
        modifier = Modifier.padding(start = Space.l),
    )
}

// "0.9.44 (54)"：设置里那一行
@Composable
fun appVersion(): String {
    val context = LocalContext.current
    return remember {
        runCatching {
            val info = context.packageManager.getPackageInfo(context.packageName, 0)
            "${info.versionName} (${info.longVersionCode})"
        }.getOrDefault("")
    }
}

// "0.9.44"：拿去和 Releases 的 tag 比
fun versionName(context: android.content.Context): String = runCatching {
    context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: ""
}.getOrDefault("")

@Composable
fun ChevronLeft() {
    val tint = MaterialTheme.colorScheme.onSurface
    Canvas(Modifier.size(18.dp)) {
        val w = size.width
        val h = size.height
        val stroke = w * 0.13f
        drawLine(tint, Offset(w * 0.62f, h * 0.18f), Offset(w * 0.32f, h * 0.5f), stroke, cap = StrokeCap.Round)
        drawLine(tint, Offset(w * 0.32f, h * 0.5f), Offset(w * 0.62f, h * 0.82f), stroke, cap = StrokeCap.Round)
    }
}
