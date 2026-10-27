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
import io.github.fifary.data.Palettes
import io.github.fifary.data.SettingsStore
import io.github.fifary.data.ThemeMode
import io.github.fifary.ui.components.Capsule
import io.github.fifary.ui.components.CircleButton
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
        }
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
