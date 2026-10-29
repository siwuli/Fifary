package io.github.fifary.ui.categories

import io.github.fifary.ui.S
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
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
import androidx.compose.material.icons.filled.DragHandle
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
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size as GeomSize
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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

private val ToolbarHeight = 52.dp
private val TitleTop = 54.dp
private val TitleShift = 45.dp

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
    onReorder: (List<String>) -> Unit,
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
                    top = compactHeight + Space.s,
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
                            Text(S("全部笔记"), style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
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
                        text = S("我的分组"),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = Space.xxs, top = Space.m, bottom = Space.xs),
                    )
                }

                item(key = "groups") {
                    // 本地先排，松手才写回设置：拖动过程中不惊动外层数据
                    val order = remember(categories) { categories.toMutableStateList() }
                    var dragging by remember { mutableStateOf<String?>(null) }
                    var dragOffset by remember { mutableStateOf(0f) }
                    var rowPx by remember { mutableStateOf(0f) }

                    RowGroup {
                        if (order.isEmpty()) {
                            Text(
                                text = S("还没有分组，点右上角 + 新建"),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = Space.l, vertical = Space.l),
                            )
                        }
                        order.forEachIndexed { index, entry ->
                            if (index > 0) RowDivider()
                            // key 跟着分组走：换位后拖动的那一行还是同一份 composition，
                            // 不然手势会被取消，一次只能挪一格
                            key(entry.path) {
                                DragRow(
                                    entry = entry,
                                    editing = editing,
                                    selected = entry.path in selected,
                                    lifted = dragging == entry.path,
                                    dragOffset = dragOffset,
                                    onMeasured = { rowPx = it },
                                    onToggle = { if (editing) toggle(entry.path) else onOpenCategory(entry.path) },
                                    onLongPress = {
                                        haptics.longPress()
                                        editing = true
                                        selected = setOf(entry.path)
                                    },
                                    onHaptics = { haptics.longPress() },
                                    onDragStart = {
                                        dragging = entry.path
                                        dragOffset = 0f
                                    },
                                    onDrag = { dy ->
                                        dragOffset += dy
                                        val from = order.indexOfFirst { it.path == entry.path }
                                        if (rowPx > 0f && from >= 0) {
                                            if (dragOffset > rowPx / 2 && from < order.lastIndex) {
                                                order.add(from + 1, order.removeAt(from))
                                                dragOffset -= rowPx
                                            } else if (dragOffset < -rowPx / 2 && from > 0) {
                                                order.add(from - 1, order.removeAt(from))
                                                dragOffset += rowPx
                                            }
                                        }
                                    },
                                    onDragEnd = {
                                        dragging = null
                                        dragOffset = 0f
                                        onReorder(order.map { it.path })
                                    },
                                )
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
                            Text(S("私密笔记"), style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
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
                            Text(S("最近删除"), style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
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
                        label = S("编辑"),
                        icon = Icons.Filled.Edit,
                        enabled = selected.size == 1,
                        onClick = { renaming = categories.firstOrNull { it.path in selected } },
                    ),
                    BarAction(
                        label = S("设为私密"),
                        icon = Icons.Filled.Lock,
                        enabled = selected.isNotEmpty(),
                        onClick = { askSecret = true },
                    ),
                    BarAction(
                        label = S("删除"),
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
            title = S("新建分组"),
            placeholder = S("分组名称"),
            confirmText = S("创建"),
            onDismiss = { creating = false },
            onConfirm = { name ->
                creating = false
                onCreate(name)
            },
        )
    }

    renaming?.let { entry ->
        InputDialog(
            title = S("重命名分组"),
            placeholder = S("分组名称"),
            confirmText = S("保存"),
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
            text = S("分组里的笔记会移到最近删除，30 天内可以恢复。"),
            confirmText = S("删除"),
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
            title = S("把分组设为私密笔记本？"),
            text = S("分组里的笔记会加密保存，查看时要先输密码。"),
            confirmText = S("设为私密"),
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
                    drawRect(colors.background, size = GeomSize(size.width, compactPx))
                    drawRect(band, topLeft = Offset(0f, compactPx), size = GeomSize(size.width, 8.dp.toPx()))
                }
            }
            .onSizeChanged { onHeightChange(it.height) }
    ) {
        Column(Modifier.fillMaxWidth().statusBarsPadding()) {
            Spacer(Modifier.height(TitleTop))
            if (!editing) {
                Text(
                    text = S("分组"),
                    style = MaterialTheme.typography.headlineLarge.copy(
                        fontSize = 32.sp,
                        lineHeight = 38.sp,
                    ),
                    modifier = Modifier
                        .padding(start = Space.l)
                        .graphicsLayer {
                            // 和笔记页一样：缩小 + 上移，落进工具条左侧
                            val c = collapse()
                            transformOrigin = TransformOrigin(0f, 0f)
                            val scale = 1f - 0.25f * c
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
                    TextPill(S("完成"), onExitEdit)
                    Text(
                        text = "已选择 $selectedCount 项",
                        style = MaterialTheme.typography.titleMedium,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.weight(1f),
                    )
                    Spacer(Modifier.width(52.dp))
                } else {
                    CircleButton(onClick = onClose) {
                        Icon(Icons.Filled.Close, contentDescription = S("关闭"), modifier = Modifier.size(20.dp))
                    }
                    Spacer(Modifier.weight(1f))
                    CircleButton(onClick = onCreate) {
                        Icon(Icons.Filled.Add, contentDescription = S("新建分组"), modifier = Modifier.size(22.dp))
                    }
                    Spacer(Modifier.width(Space.xs))
                    CircleButton(onClick = onEnterEdit) {
                        Icon(Icons.Filled.Edit, contentDescription = S("编辑分组"), modifier = Modifier.size(20.dp))
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

// 一行分组：左边六个点的拖动把手，拖动时整行跟着手指走
@Composable
private fun DragRow(
    entry: FolderEntry,
    editing: Boolean,
    selected: Boolean,
    lifted: Boolean,
    dragOffset: Float,
    onMeasured: (Float) -> Unit,
    onToggle: () -> Unit,
    onLongPress: () -> Unit,
    onHaptics: () -> Unit,
    onDragStart: () -> Unit,
    onDrag: (Float) -> Unit,
    onDragEnd: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .graphicsLayer { translationY = if (lifted) dragOffset else 0f }
            .onSizeChanged { onMeasured(it.height.toFloat()) }
            .springPressable(onLongPress = onLongPress, onClick = onToggle)
            .padding(horizontal = Space.l, vertical = 18.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (!editing) {
            Icon(
                Icons.Filled.DragHandle,
                contentDescription = S("拖动排序"),
                tint = MaterialTheme.colorScheme.outlineVariant,
                modifier = Modifier
                    .size(20.dp)
                    .pointerInput(Unit) {
                        detectDragGestures(
                            onDragStart = {
                                onHaptics()
                                onDragStart()
                            },
                            onDragEnd = onDragEnd,
                            onDragCancel = onDragEnd,
                        ) { change, drag ->
                            change.consume()
                            onDrag(drag.y)
                        }
                    },
            )
            Spacer(Modifier.width(Space.xs))
        }
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
            SelectCheck(selected)
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
