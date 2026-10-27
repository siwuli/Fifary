package io.github.fifary.ui.categories

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
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
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size as GeomSize
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.fifary.data.FolderEntry
import io.github.fifary.ui.components.BarAction
import io.github.fifary.ui.components.BouncingBox
import io.github.fifary.ui.components.CircleButton
import io.github.fifary.ui.components.ConfirmDialog
import io.github.fifary.ui.components.FloatingActionBar
import io.github.fifary.ui.components.InputDialog
import io.github.fifary.ui.components.NotebookGlyph
import io.github.fifary.ui.components.SelectCheck
import io.github.fifary.ui.components.TextPill
import io.github.fifary.ui.components.rememberHaptics
import io.github.fifary.ui.components.rememberSecretGate
import io.github.fifary.ui.components.springPressable
import io.github.fifary.ui.theme.Radius
import io.github.fifary.ui.theme.Space

private val ToolbarHeight = 56.dp
private val TitleTop = 54.dp
private val TitleShift = 53.dp

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
    var compactHeight by remember { mutableStateOf(0.dp) }
    var headerHeight by remember { mutableStateOf(0.dp) }
    val density = LocalDensity.current
    val listState = rememberLazyListState()

    val spacer = (headerHeight - compactHeight).coerceAtLeast(0.dp)
    val spacerPx = with(density) { spacer.toPx() }
    val collapse = remember(spacerPx) {
        derivedStateOf {
            val index = listState.firstVisibleItemIndex
            if (index > 0) 1f else (listState.firstVisibleItemScrollOffset / spacerPx.coerceAtLeast(1f))
                .coerceIn(0f, 1f)
        }
    }

    fun exitSelection() {
        editing = false
        selected = emptySet()
    }

    fun toggle(path: String) {
        editing = true
        selected = if (path in selected) selected - path else selected + path
    }

    BackHandler(enabled = editing) { exitSelection() }

    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        BouncingBox(Modifier.fillMaxSize()) {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    start = Space.l,
                    end = Space.l,
                    top = compactHeight,
                    bottom = Space.xl + 88.dp,
                ),
            ) {
                item(key = "space") { Spacer(Modifier.height(spacer)) }

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
                                NotebookGlyph(size = 20)
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
                                Icons.Filled.KeyboardArrowRight,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.outline,
                                modifier = Modifier.size(18.dp),
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

            CategoryHeader(
                editing = editing,
                selectedCount = selected.size,
                collapse = { collapse.value },
                onClose = onClose,
                onCreate = { creating = true },
                onEnterEdit = {
                    selected = emptySet()
                    editing = true
                },
                onExitEdit = { exitSelection() },
                onCompactChange = { compactHeight = with(density) { it.toDp() } },
                onHeightChange = { headerHeight = with(density) { it.toDp() } },
            )
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
                deleting.forEach(onDelete)
                deleting = emptyList()
                exitSelection()
            },
        )
    }

    if (askSecret) {
        ConfirmDialog(
            title = "把分组设为私密笔记本？",
            text = "分组里的笔记会加密保存，查看时要先输密码。",
            confirmText = "设为私密",
            onDismiss = { askSecret = false },
            onConfirm = {
                askSecret = false
                val paths = selected.toList()
                gate.run {
                    paths.forEach(onSetSecret)
                    exitSelection()
                }
            },
        )
    }
}

// 和笔记页同一套悬浮顶栏：52dp 工具条常驻，大标题随滚动收起
@Composable
private fun CategoryHeader(
    editing: Boolean,
    selectedCount: Int,
    collapse: () -> Float,
    onClose: () -> Unit,
    onCreate: () -> Unit,
    onEnterEdit: () -> Unit,
    onExitEdit: () -> Unit,
    onCompactChange: (Int) -> Unit,
    onHeightChange: (Int) -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    var compactPx by remember { mutableStateOf(0f) }
    val band = remember(colors.background) {
        Brush.verticalGradient(
            0f to colors.background,
            0.7f to colors.background.copy(alpha = 0.92f),
            1f to colors.background.copy(alpha = 0f),
        )
    }

    Box(
        Modifier
            .fillMaxWidth()
            .drawBehind {
                val c = collapse()
                if (c > 0.01f) {
                    drawRect(band, size = GeomSize(size.width, compactPx + 16.dp.toPx()), alpha = c)
                }
            }
            .onSizeChanged { onHeightChange(it.height) }
    ) {
        Column(Modifier.fillMaxWidth().statusBarsPadding()) {
            Spacer(Modifier.height(TitleTop))
            if (!editing) {
                Text(
                    text = "分组",
                    style = MaterialTheme.typography.headlineLarge,
                    modifier = Modifier
                        .padding(start = Space.l)
                        .graphicsLayer {
                            // 和笔记页一样：缩小 + 上移，落进工具条左侧
                            val c = collapse()
                            transformOrigin = TransformOrigin(0f, 0f)
                            val scale = 1f - 0.42f * c
                            scaleX = scale
                            scaleY = scale
                            translationY = -TitleShift.toPx() * c
                        },
                )
            }
        }

        Column(
            Modifier
                .statusBarsPadding()
                .onSizeChanged {
                    compactPx = it.height.toFloat()
                    onCompactChange(it.height)
                }
        ) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .height(ToolbarHeight)
                    .padding(horizontal = Space.l),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (editing) {
                    TextPill("完成", onExitEdit)
                    Text(
                        text = "已选择 $selectedCount 项",
                        style = MaterialTheme.typography.titleMedium,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.weight(1f),
                    )
                    Spacer(Modifier.width(52.dp))
                } else {
                    CircleButton(onClick = onClose) {
                        Icon(Icons.Filled.Close, contentDescription = "关闭", modifier = Modifier.size(20.dp))
                    }
                    Spacer(Modifier.weight(1f))
                    CircleButton(onClick = onCreate) {
                        Icon(Icons.Filled.Add, contentDescription = "新建分组", modifier = Modifier.size(22.dp))
                    }
                    Spacer(Modifier.width(Space.xs))
                    CircleButton(onClick = onEnterEdit) {
                        Icon(Icons.Filled.Edit, contentDescription = "编辑分组", modifier = Modifier.size(20.dp))
                    }
                }
            }
        }
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
    Canvas(Modifier.size(20.dp)) {
        val w = size.width
        val h = size.height
        val bar = h * 0.18f
        val r = CornerRadius(bar / 2)
        drawRoundRect(tint.copy(alpha = 0.45f), Offset(w * 0.20f, h * 0.12f), GeomSize(w * 0.60f, bar), r)
        drawRoundRect(tint.copy(alpha = 0.7f), Offset(w * 0.12f, h * 0.38f), GeomSize(w * 0.76f, bar), r)
        drawRoundRect(tint, Offset(w * 0.04f, h * 0.64f), GeomSize(w * 0.92f, bar), r)
    }
}
