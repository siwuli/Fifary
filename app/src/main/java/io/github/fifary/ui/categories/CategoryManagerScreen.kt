package io.github.fifary.ui.categories

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.fifary.data.FolderEntry
import io.github.fifary.ui.components.AccentDot
import io.github.fifary.ui.components.BarAction
import io.github.fifary.ui.components.CircleButton
import io.github.fifary.ui.components.ConfirmDialog
import io.github.fifary.ui.components.FloatingActionBar
import io.github.fifary.ui.components.InputDialog
import io.github.fifary.ui.components.SelectCheck
import io.github.fifary.ui.components.TextPill
import io.github.fifary.ui.components.rememberHaptics
import io.github.fifary.ui.components.rememberSecretGate
import io.github.fifary.ui.components.springPressable
import io.github.fifary.ui.theme.Elev
import io.github.fifary.ui.theme.Radius
import io.github.fifary.ui.theme.Space

@Composable
fun CategoryManagerScreen(
    categories: List<FolderEntry>,
    totalNotes: Int,
    trashCount: Int,
    secretCount: Int,
    secretEnabled: Boolean,
    onOpenAll: () -> Unit,
    onOpenCategory: (String) -> Unit,
    onOpenTrash: () -> Unit,
    onOpenSecret: () -> Unit,
    onCreate: (String) -> Unit,
    onRename: (String, String) -> Unit,
    onDelete: (String) -> Unit,
    onSetSecret: (String) -> Unit,
    onClose: () -> Unit,
) {
    val haptics = rememberHaptics()
    val gate = rememberSecretGate()
    var editing by remember { mutableStateOf(false) }
    var selected by remember { mutableStateOf(emptySet<String>()) }
    var creating by remember { mutableStateOf(false) }
    var renaming by remember { mutableStateOf<FolderEntry?>(null) }
    var deleting by remember { mutableStateOf(emptyList<String>()) }
    var askSecret by remember { mutableStateOf(false) }
    var headerHeight by remember { mutableStateOf(0.dp) }
    val density = LocalDensity.current
    val listState = rememberLazyListState()
    val tail = with(LocalConfiguration.current) { (screenHeightDp * 0.45f).dp }

    fun exitSelection() {
        editing = false
        selected = emptySet()
    }

    fun toggle(path: String) {
        haptics.toggle()
        editing = true
        selected = if (path in selected) selected - path else selected + path
    }

    BackHandler(enabled = editing) { exitSelection() }

    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = Space.l,
                end = Space.l,
                top = headerHeight + Space.xxs,
                bottom = tail,
            ),
        ) {
            item(key = "all") {
                RowGroup {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .springPressable(onClick = { if (!editing) onOpenAll() })
                            .padding(horizontal = Space.l, vertical = 18.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        LayersGlyph()
                        Spacer(Modifier.width(Space.s))
                        Text("全部笔记", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                        Text(
                            text = "$totalNotes",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.outline,
                        )
                        Icon(
                            Icons.Filled.KeyboardArrowRight,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.outline,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }
            }

            item(key = "label") {
                Text(
                    text = "我的分组",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = Space.xxs, top = Space.m, bottom = Space.xs),
                )
            }

            item(key = "groups") {
                RowGroup {
                    if (categories.isEmpty()) {
                        Text(
                            text = "还没有分组，点右上角 + 新建",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = Space.l, vertical = Space.l),
                        )
                    }
                    categories.forEachIndexed { index, entry ->
                        if (index > 0) RowDivider()
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .springPressable(
                                    onLongPress = {
                                        haptics.longPress()
                                        editing = true
                                        selected = setOf(entry.path)
                                    },
                                    onClick = { if (editing) toggle(entry.path) else onOpenCategory(entry.path) },
                                )
                                .padding(horizontal = Space.l, vertical = 18.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            AccentDot()
                            Spacer(Modifier.width(Space.s))
                            Text(
                                text = entry.name,
                                style = MaterialTheme.typography.titleMedium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f),
                            )
                            Text(
                                text = "${entry.noteCount}",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.outline,
                            )
                            if (editing) {
                                Spacer(Modifier.width(Space.s))
                                SelectCheck(entry.path in selected)
                            } else {
                                Icon(
                                    Icons.Filled.KeyboardArrowRight,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.outline,
                                    modifier = Modifier.size(18.dp),
                                )
                            }
                        }
                    }
                }
            }

            item(key = "misc") {
                Spacer(Modifier.height(Space.m))
                RowGroup {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .springPressable(onClick = { if (!editing) onOpenSecret() })
                            .padding(horizontal = Space.l, vertical = 18.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            Icons.Filled.Lock,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp),
                        )
                        Spacer(Modifier.width(Space.s))
                        Text("私密笔记", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                        if (secretEnabled) {
                            Text(
                                text = "$secretCount",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.outline,
                            )
                        }
                        Icon(
                            Icons.Filled.Lock,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.outline,
                            modifier = Modifier.size(16.dp),
                        )
                    }
                    RowDivider()
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .springPressable(onClick = { if (!editing) onOpenTrash() })
                            .padding(horizontal = Space.l, vertical = 18.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            Icons.Filled.Delete,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp),
                        )
                        Spacer(Modifier.width(Space.s))
                        Text("最近删除", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                        Text(
                            text = "$trashCount",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.outline,
                        )
                        Icon(
                            Icons.Filled.KeyboardArrowRight,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.outline,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }
            }
        }

        // 悬浮顶栏：内容从下面滑过去，被渐变雾化带盖住
        val band = androidx.compose.ui.graphics.Brush.verticalGradient(
            0f to MaterialTheme.colorScheme.background.copy(alpha = 0.88f),
            0.55f to MaterialTheme.colorScheme.background.copy(alpha = 0.94f),
            0.78f to MaterialTheme.colorScheme.background.copy(alpha = 0.86f),
            1f to MaterialTheme.colorScheme.background.copy(alpha = 0.35f),
        )
        Column(
            Modifier
                .fillMaxWidth()
                .background(band)
                .statusBarsPadding()
                .onSizeChanged { headerHeight = with(density) { it.height.toDp() } }
        ) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = Space.l, vertical = Space.xs),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (editing) {
                    TextPill("完成", ::exitSelection)
                    Text(
                        text = "已选择 ${selected.size} 项",
                        style = MaterialTheme.typography.titleMedium,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        modifier = Modifier.weight(1f),
                    )
                    Spacer(Modifier.width(52.dp))
                } else {
                    CircleButton(onClick = onClose) {
                        Icon(Icons.Filled.Close, contentDescription = "关闭", modifier = Modifier.size(20.dp))
                    }
                    Spacer(Modifier.width(Space.s))
                    Text("分组", style = MaterialTheme.typography.headlineLarge)
                    Spacer(Modifier.weight(1f))
                    CircleButton(onClick = { haptics.press(); creating = true }) {
                        Icon(Icons.Filled.Add, contentDescription = "新建分组", modifier = Modifier.size(22.dp))
                    }
                    Spacer(Modifier.width(Space.s))
                    CircleButton(onClick = {
                        haptics.press()
                        selected = emptySet()
                        editing = true
                    }) {
                        Icon(Icons.Filled.Edit, contentDescription = "编辑分组", modifier = Modifier.size(20.dp))
                    }
                }
            }
            Spacer(Modifier.height(Space.xs))
        }

        if (editing) {
            FloatingActionBar(
                actions = listOf(
                    BarAction(
                        label = "编辑",
                        icon = Icons.Filled.Edit,
                        enabled = selected.size == 1,
                        onClick = { renaming = categories.firstOrNull { it.path in selected } },
                    ),
                    BarAction(
                        label = "设为私密",
                        icon = Icons.Filled.Lock,
                        enabled = selected.isNotEmpty(),
                        onClick = { askSecret = true },
                    ),
                    BarAction(
                        label = "删除",
                        icon = Icons.Filled.Delete,
                        enabled = selected.isNotEmpty(),
                        onClick = { deleting = selected.toList() },
                    ),
                ),
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(bottom = Space.m)
                    .fillMaxWidth(0.92f),
            )
        }
    }

    if (creating) {
        InputDialog(
            title = "新建分组",
            placeholder = "分组名称",
            confirmText = "创建",
            onDismiss = { creating = false },
            onConfirm = { name ->
                creating = false
                onCreate(name)
            },
        )
    }

    renaming?.let { entry ->
        InputDialog(
            title = "重命名分组",
            placeholder = "分组名称",
            confirmText = "保存",
            initial = entry.name,
            onDismiss = { renaming = null },
            onConfirm = { name ->
                renaming = null
                onRename(entry.path, name)
                exitSelection()
            },
        )
    }

    if (deleting.isNotEmpty()) {
        ConfirmDialog(
            title = "删除 ${deleting.size} 个分组？",
            text = "分组里的笔记会移到最近删除，30 天内可以恢复。",
            confirmText = "删除",
            onDismiss = { deleting = emptyList() },
            onConfirm = {
                haptics.toggle()
                deleting.forEach(onDelete)
                deleting = emptyList()
                exitSelection()
            },
        )
    }

    if (askSecret) {
        ConfirmDialog(
            title = "把分组设为私密？",
            text = "分组里的笔记会加密保存，之后查看要先输密码。",
            confirmText = "设为私密",
            onDismiss = { askSecret = false },
            onConfirm = {
                askSecret = false
                val paths = selected.toList()
                gate.run {
                    haptics.toggle()
                    paths.forEach(onSetSecret)
                    exitSelection()
                }
            },
        )
    }
}

@Composable
private fun RowGroup(content: @Composable ColumnScope.() -> Unit) {
    Surface(
        shape = RoundedCornerShape(Radius.card),
        color = MaterialTheme.colorScheme.surface,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(content = content)
    }
}

@Composable
private fun RowDivider() {
    HorizontalDivider(
        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f),
        modifier = Modifier.padding(start = 56.dp),
    )
}

// "全部笔记"用的层叠图标，参考图里那个三层效果
@Composable
private fun LayersGlyph() {
    val tint = MaterialTheme.colorScheme.onSurfaceVariant
    androidx.compose.foundation.Canvas(Modifier.size(20.dp)) {
        val w = size.width
        val h = size.height
        val bar = h * 0.18f
        val r = androidx.compose.ui.geometry.CornerRadius(bar / 2)
        drawRoundRect(tint.copy(alpha = 0.45f), androidx.compose.ui.geometry.Offset(w * 0.20f, h * 0.12f), androidx.compose.ui.geometry.Size(w * 0.60f, bar), r)
        drawRoundRect(tint.copy(alpha = 0.7f), androidx.compose.ui.geometry.Offset(w * 0.12f, h * 0.38f), androidx.compose.ui.geometry.Size(w * 0.76f, bar), r)
        drawRoundRect(tint, androidx.compose.ui.geometry.Offset(w * 0.04f, h * 0.64f), androidx.compose.ui.geometry.Size(w * 0.92f, bar), r)
    }
}
