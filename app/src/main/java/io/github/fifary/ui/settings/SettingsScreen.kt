package io.github.fifary.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.fifary.data.Palette
import io.github.fifary.data.PrivateVault
import io.github.fifary.data.Palettes
import io.github.fifary.data.SettingsStore
import io.github.fifary.data.ThemeMode
import io.github.fifary.ui.components.Capsule
import io.github.fifary.ui.components.CircleButton
import io.github.fifary.ui.components.InputDialog
import io.github.fifary.ui.components.rememberHaptics
import io.github.fifary.ui.theme.Elev
import io.github.fifary.ui.theme.Radius
import io.github.fifary.ui.theme.Space
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private val MODE_LABELS = listOf(
    ThemeMode.SYSTEM to "跟随系统",
    ThemeMode.LIGHT to "浅色",
    ThemeMode.DARK to "深色",
)

@Composable
fun SettingsScreen(onBack: () -> Unit) {
    val haptics = rememberHaptics()
    val scope = rememberCoroutineScope()
    var syncing by remember { mutableStateOf(false) }
    var doneCount by remember { mutableStateOf<Int?>(null) }

    // 私密密码：设置 / 修改（改密码要把全部私密笔记重新加密）
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
                CircleButton(onClick = onBack) {
                    ChevronLeft()
                }
                Spacer(Modifier.size(Space.s))
                Text("设置", style = MaterialTheme.typography.headlineLarge)
            }

            SectionLabel("外观")
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(Radius.card),
                color = MaterialTheme.colorScheme.surface,
            ) {
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
                                onClick = {
                                    haptics.toggle()
                                    SettingsStore.updateThemeMode(mode)
                                },
                            )
                        }
                    }

                    Spacer(Modifier.height(Space.xxs))
                    Text(
                        text = "页面颜色 · ${SettingsStore.palette.name}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(Space.s)) {
                        Palettes.forEach { palette ->
                            PaletteSwatch(
                                palette = palette,
                                selected = SettingsStore.palette.id == palette.id,
                                onClick = {
                                    haptics.toggle()
                                    SettingsStore.updatePalette(palette)
                                },
                            )
                        }
                    }
                }
            }

            SectionLabel("同步")
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(Radius.card),
                color = MaterialTheme.colorScheme.surface,
            ) {
                Column(Modifier.padding(Space.l), verticalArrangement = Arrangement.spacedBy(Space.s)) {
                    Text(
                        text = "尚未配置 WebDAV。没配置时 App 一样能用，只做本地笔记。",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (syncing) {
                        LinearProgressIndicator(Modifier.fillMaxWidth())
                    }
                    doneCount?.let { count ->
                        Surface(
                            color = MaterialTheme.colorScheme.primaryContainer,
                            shape = RoundedCornerShape(Radius.chip),
                        ) {
                            Text(
                                text = "已完成：上传 $count 项",
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.padding(horizontal = Space.m, vertical = Space.s),
                            )
                        }
                    }
                    Button(
                        onClick = {
                            scope.launch {
                                syncing = true
                                doneCount = null
                                delay(900)
                                syncing = false
                                doneCount = 12
                                haptics.syncDone()
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(if (syncing) "同步中…" else "立即同步（占位）")
                    }
                }
            }

            SectionLabel("私密")
            Surface(
                shape = RoundedCornerShape(Radius.card),
                color = MaterialTheme.colorScheme.surface,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(Modifier.padding(Space.m), verticalArrangement = Arrangement.spacedBy(Space.s)) {
                    Text(
                        text = if (PrivateVault.enabled) {
                            "私密笔记用密码派生的 AES-GCM 密钥加密保存，进入私密笔记只要输一次。" +
                                "改密码会把全部私密笔记用新密钥重新加密；云备份上传的仍是密文，密码不出本机。"
                        } else {
                            "还没有私密密码。设一个之后，私密笔记会加密保存。"
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (message != null) {
                        Text(
                            text = message!!,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(Space.xs)) {
                        Capsule(
                            selected = true,
                            onClick = {
                                message = null
                                if (PrivateVault.enabled) askOld = true else askSet = true
                            },
                        ) {
                            Text(
                                text = if (PrivateVault.enabled) "修改密码" else "设置密码",
                                style = MaterialTheme.typography.labelMedium,
                            )
                        }
                        if (PrivateVault.enabled) {
                            Capsule(
                                selected = false,
                                onClick = {
                                    PrivateVault.lock()
                                    message = "已锁定，下次看私密笔记要重新输密码"
                                },
                            ) {
                                Text("立即锁定", style = MaterialTheme.typography.labelMedium)
                            }
                        }
                    }
                }
            }
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
            note = "全部私密笔记会用新密码重新加密。",
            onDismiss = { askNew = false },
            onConfirm = { value ->
                askNew = false
                message = if (PrivateVault.changePassword(oldPassword, value)) {
                    "已用新密码重新加密全部私密笔记"
                } else {
                    "修改失败，密码没有变"
                }
            },
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
        modifier = Modifier.height(io.github.fifary.ui.theme.Size.chipHeight),
    ) {
        Row(Modifier.padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(text, style = MaterialTheme.typography.labelMedium)
        }
    }
}

@Composable
private fun PaletteSwatch(palette: Palette, selected: Boolean, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = palette.lightAccent,
        border = androidx.compose.foundation.BorderStroke(
            width = if (selected) 3.dp else 1.dp,
            color = if (selected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.outlineVariant,
        ),
        modifier = Modifier.size(44.dp),
    ) {
        Box(Modifier.background(palette.lightAccent), contentAlignment = Alignment.Center) {
            if (selected) {
                Box(
                    Modifier
                        .size(10.dp)
                        .background(MaterialTheme.colorScheme.surface, CircleShape)
                )
            }
        }
    }
}

@Composable
private fun ChevronLeft() {
    val tint = MaterialTheme.colorScheme.onSurface
    androidx.compose.foundation.Canvas(Modifier.size(18.dp)) {
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
