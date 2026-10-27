package io.github.fifary.ui.settings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.fifary.data.Palette
import io.github.fifary.data.Palettes
import io.github.fifary.data.PrivateVault
import io.github.fifary.data.SettingsStore
import io.github.fifary.data.ThemeMode
import io.github.fifary.ui.components.CircleButton
import io.github.fifary.ui.components.InputDialog
import io.github.fifary.ui.components.springPressable
import io.github.fifary.ui.theme.Radius
import io.github.fifary.ui.theme.Size
import io.github.fifary.ui.theme.Space

private val MODE_LABELS = listOf(
    ThemeMode.SYSTEM to "跟随系统",
    ThemeMode.LIGHT to "浅色",
    ThemeMode.DARK to "深色",
)

@Composable
fun SettingsScreen(onBack: () -> Unit) {
    // 私密密码：设置 / 修改（改密码只重新包一次数据密钥，不动笔记）
    var askSet by remember { mutableStateOf(false) }
    var askOld by remember { mutableStateOf(false) }
    var askNew by remember { mutableStateOf(false) }
    var oldPassword by remember { mutableStateOf("") }
    var message by remember { mutableStateOf<String?>(null) }

    Scaffold(containerColor = MaterialTheme.colorScheme.background) { inner ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(inner)
                .statusBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Space.l)
                .padding(bottom = 48.dp),
            verticalArrangement = Arrangement.spacedBy(Space.m),
        ) {
            Row(
                Modifier.fillMaxWidth().padding(top = Space.xs, bottom = Space.s),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CircleButton(onClick = onBack) { ChevronLeft() }
                Spacer(Modifier.size(Space.s))
                Text("设置", style = MaterialTheme.typography.headlineLarge)
            }

            SectionLabel("外观")
            Card {
                Column(Modifier.padding(Space.l), verticalArrangement = Arrangement.spacedBy(Space.s)) {
                    Text(
                        text = "主题",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(Space.xs)) {
                        MODE_LABELS.forEach { (mode, label) ->
                            ThemePill(
                                text = label,
                                selected = SettingsStore.themeMode == mode,
                                onClick = { SettingsStore.updateThemeMode(mode) },
                            )
                        }
                    }

                    Spacer(Modifier.height(Space.xxs))
                    Text(
                        text = "页面颜色 · ${SettingsStore.palette.name}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    // 六个色块平分一行，窄屏也不会有够不到的第七个
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(Space.xs),
                    ) {
                        Palettes.forEach { palette ->
                            PaletteSwatch(
                                palette = palette,
                                selected = SettingsStore.palette.id == palette.id,
                                onClick = { SettingsStore.updatePalette(palette) },
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                }
            }

            SectionLabel("私密笔记")
            Card {
                SettingRow(
                    title = "私密密码",
                    value = if (PrivateVault.enabled) "已设置" else "未设置",
                    onClick = {
                        message = null
                        if (PrivateVault.enabled) askOld = true else askSet = true
                    },
                )
                if (PrivateVault.unlocked) {
                    HorizontalDivider(
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f),
                        modifier = Modifier.padding(start = Space.l),
                    )
                    SettingRow(
                        title = "立即锁定",
                        value = null,
                        onClick = {
                            PrivateVault.lock()
                            message = "已锁定，看私密笔记要重新输密码"
                        },
                    )
                }
            }
            Text(
                text = "密码只在本机验证；私密笔记用独立数据密钥加密，改密码只换外面那层包裹，不会重写笔记。",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.outline,
                modifier = Modifier.padding(horizontal = Space.xxs),
            )
            message?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(horizontal = Space.xxs),
                )
            }

            // 装的是哪一版，看这一行就够了
            Text(
                text = version(),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.outline,
                modifier = Modifier.padding(top = Space.s, start = Space.xxs),
            )
        }
    }

    if (askSet) {
        InputDialog(
            title = "设置私密密码",
            placeholder = "至少 4 位",
            confirmText = "保存",
            masked = true,
            note = "密码只存在本机，忘了就解不开。",
            onDismiss = { askSet = false },
            onConfirm = { value ->
                if (PrivateVault.setPassword(value)) {
                    askSet = false
                    message = "私密密码已设置"
                }
            },
        )
    }

    if (askOld) {
        InputDialog(
            title = "输入当前密码",
            placeholder = "密码",
            confirmText = "下一步",
            masked = true,
            onDismiss = { askOld = false },
            onConfirm = { value ->
                if (PrivateVault.unlock(value)) {
                    oldPassword = value
                    askOld = false
                    askNew = true
                } else {
                    message = "密码不对"
                }
            },
        )
    }

    if (askNew) {
        InputDialog(
            title = "设置新密码",
            placeholder = "至少 4 位",
            confirmText = "保存",
            masked = true,
            onDismiss = { askNew = false },
            onConfirm = { value ->
                askNew = false
                message = if (PrivateVault.changePassword(oldPassword, value)) {
                    "密码已修改，笔记不用重新加密"
                } else {
                    "修改失败，密码没有变"
                }
                oldPassword = ""
            },
        )
    }
}

@Composable
private fun Card(content: @Composable ColumnScope.() -> Unit) {
    Surface(
        shape = RoundedCornerShape(Radius.card),
        color = MaterialTheme.colorScheme.surface,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(content = content)
    }
}

@Composable
private fun SettingRow(title: String, value: String?, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .springPressable(onClick = onClick)
            .padding(horizontal = Space.l, vertical = 18.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
        if (value != null) {
            Text(
                text = value,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Icon(
            Icons.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.outline,
            modifier = Modifier.size(18.dp),
        )
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = Space.xxs, top = Space.xs),
    )
}

@Composable
private fun ThemePill(text: String, selected: Boolean, onClick: () -> Unit) {
    // 卡片是白底，所以选中的胶囊用强调色填充，而不是"白底+阴影"
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
        contentColor = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.height(Size.chipHeight),
    ) {
        Row(Modifier.padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(text, style = MaterialTheme.typography.labelMedium)
        }
    }
}

@Composable
private fun PaletteSwatch(
    palette: Palette,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = palette.lightAccent,
        border = BorderStroke(
            width = if (selected) 3.dp else 1.dp,
            color = if (selected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.outlineVariant,
        ),
        modifier = modifier.aspectRatio(1f),
    ) {
        Box(Modifier.background(palette.lightAccent), contentAlignment = Alignment.Center) {
            if (selected) {
                Box(
                    Modifier
                        .size(8.dp)
                        .background(MaterialTheme.colorScheme.surface, CircleShape)
                )
            }
        }
    }
}

@Composable
private fun ChevronLeft() {
    val tint = MaterialTheme.colorScheme.onSurface
    Canvas(Modifier.size(18.dp)) {
        val w = size.width
        val h = size.height
        val stroke = w * 0.13f
        drawLine(
            tint,
            androidx.compose.ui.geometry.Offset(w * 0.62f, h * 0.18f),
            androidx.compose.ui.geometry.Offset(w * 0.32f, h * 0.5f),
            stroke,
            cap = androidx.compose.ui.graphics.StrokeCap.Round,
        )
        drawLine(
            tint,
            androidx.compose.ui.geometry.Offset(w * 0.32f, h * 0.5f),
            androidx.compose.ui.geometry.Offset(w * 0.62f, h * 0.82f),
            stroke,
            cap = androidx.compose.ui.graphics.StrokeCap.Round,
        )
    }
}

@Composable
private fun version(): String {
    val context = LocalContext.current
    return remember {
        runCatching {
            val info = context.packageManager.getPackageInfo(context.packageName, 0)
            "Fifary ${info.versionName} (${info.longVersionCode})"
        }.getOrDefault("Fifary")
    }
}
