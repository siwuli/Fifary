package io.github.fifary.ui.categories

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.fifary.data.FolderEntry
import io.github.fifary.ui.components.AccentDot
import io.github.fifary.ui.components.CircleButton
import io.github.fifary.ui.components.rememberHaptics
import io.github.fifary.ui.components.springPressable
import io.github.fifary.ui.theme.Radius
import io.github.fifary.ui.theme.Space

@Composable
fun CategoryManagerScreen(
    categories: List<FolderEntry>,
    totalNotes: Int,
    onOpenAll: () -> Unit,
    onOpenCategory: (String) -> Unit,
    onCreate: (String) -> Unit,
    onDelete: (String) -> Unit,
    onClose: () -> Unit,
) {
    val haptics = rememberHaptics()
    var creating by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf<FolderEntry?>(null) }

    Scaffold(containerColor = MaterialTheme.colorScheme.background) { inner ->
        Column(Modifier.fillMaxSize().padding(inner).statusBarsPadding()) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = Space.l, vertical = Space.xs),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CircleButton(onClick = onClose) {
                    Icon(Icons.Filled.Close, contentDescription = "关闭", modifier = Modifier.size(20.dp))
                }
                Spacer(Modifier.size(Space.s))
                Text("分组", style = MaterialTheme.typography.headlineLarge)
                Spacer(Modifier.weight(1f))
                CircleButton(onClick = { haptics.press(); creating = true }) {
                    Icon(Icons.Filled.Add, contentDescription = "新建分组", modifier = Modifier.size(22.dp))
                }
            }

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(
                    start = Space.l,
                    end = Space.l,
                    top = Space.m,
                    bottom = 48.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(Space.xs),
            ) {
                item(key = "all") {
                    CategoryRow(
                        name = "全部笔记",
                        count = totalNotes,
                        leading = { LayersGlyph() },
                        onClick = onOpenAll,
                    )
                }
                item(key = "label") {
                    Text(
                        text = "我的分组",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = Space.xxs, top = Space.m, bottom = Space.xxs),
                    )
                }
                items(items = categories, key = { it.path }) { entry ->
                    CategoryRow(
                        name = entry.name,
                        count = entry.noteCount,
                        leading = { AccentDot() },
                        onClick = { onOpenCategory(entry.path) },
                        onLongPress = {
                            haptics.longPress()
                            deleting = entry
                        },
                        modifier = Modifier.animateItem(),
                    )
                }
                if (categories.isEmpty()) {
                    item(key = "empty") {
                        Text(
                            text = "还没有分组，点右上角 + 新建",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = Space.m, start = Space.xxs),
                        )
                    }
                }
            }
        }
    }

    if (creating) {
        InputDialog(
            title = "新建分组",
            placeholder = "分组名称",
            confirmText = "创建",
            onDismiss = { creating = false },
            onConfirm = { name ->
                onCreate(name)
                creating = false
            },
        )
    }

    deleting?.let { entry ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text("删除分组「${entry.name}」？") },
            text = { Text("分组里的 ${entry.noteCount} 篇笔记会一起删除。") },
            confirmButton = {
                TextButton(onClick = {
                    haptics.toggle()
                    onDelete(entry.path)
                    deleting = null
                }) { Text("删除") }
            },
            dismissButton = {
                TextButton(onClick = { deleting = null }) { Text("取消") }
            },
        )
    }
}

@Composable
private fun CategoryRow(
    name: String,
    count: Int,
    leading: @Composable () -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onLongPress: (() -> Unit)? = null,
) {
    Surface(
        modifier = modifier.fillMaxWidth().springPressable(onLongPress = onLongPress, onClick = onClick),
        shape = RoundedCornerShape(Radius.card),
        color = MaterialTheme.colorScheme.surface,
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = Space.l, vertical = 18.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            leading()
            Spacer(Modifier.size(Space.s))
            Text(
                text = name,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = "$count",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.outline,
            )
            Icon(
                imageVector = Icons.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.outline,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

// "全部笔记"用的层叠图标，参考图里那个三层效果
@Composable
private fun LayersGlyph() {
    val tint = MaterialTheme.colorScheme.onSurfaceVariant
    Canvas(Modifier.size(18.dp)) {
        val w = size.width
        val h = size.height
        val bar = h * 0.18f
        val r = CornerRadius(bar / 2)
        drawRoundRect(tint.copy(alpha = 0.45f), Offset(w * 0.20f, h * 0.12f), Size(w * 0.60f, bar), r)
        drawRoundRect(tint.copy(alpha = 0.7f), Offset(w * 0.12f, h * 0.38f), Size(w * 0.76f, bar), r)
        drawRoundRect(tint, Offset(w * 0.04f, h * 0.64f), Size(w * 0.92f, bar), r)
    }
}

@Composable
private fun InputDialog(
    title: String,
    placeholder: String,
    confirmText: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var value by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Box(Modifier.padding(horizontal = Space.m, vertical = Space.s)) {
                    if (value.isEmpty()) {
                        Text(
                            text = placeholder,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.outline,
                        )
                    }
                    BasicTextField(
                        value = value,
                        onValueChange = { value = it },
                        singleLine = true,
                        textStyle = MaterialTheme.typography.bodyMedium.copy(
                            color = MaterialTheme.colorScheme.onSurface,
                        ),
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(value) },
                enabled = value.isNotBlank(),
            ) { Text(confirmText) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        },
    )
}
