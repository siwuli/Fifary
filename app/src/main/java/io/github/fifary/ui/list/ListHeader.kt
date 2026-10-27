package io.github.fifary.ui.list

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size as GeomSize
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.fifary.data.FolderEntry
import io.github.fifary.data.NotesStore
import io.github.fifary.data.SortMode
import io.github.fifary.data.ViewMode
import io.github.fifary.ui.components.AccentDot
import io.github.fifary.ui.components.Capsule
import io.github.fifary.ui.components.CircleIconButton
import io.github.fifary.ui.components.TextPill
import io.github.fifary.ui.components.rememberHaptics
import io.github.fifary.ui.theme.Elev
import io.github.fifary.ui.theme.Radius
import io.github.fifary.ui.theme.Size
import io.github.fifary.ui.theme.Space

// 悬浮顶栏：整块浮在内容上面，内容从它下面滑过去（被渐变雾化带盖住）。
// collapse 只在 graphicsLayer 里读，滚动过程中不发生重组。
@Composable
fun ListHeader(
    title: String,
    subtitle: String,
    collapse: () -> Float,
    selectionMode: Boolean,
    selectionText: String,
    searchOpen: Boolean,
    query: String,
    onQueryChange: (String) -> Unit,
    onToggleSearch: () -> Unit,
    onCancelSelection: () -> Unit,
    onSelectAll: () -> Unit,
    allSelected: Boolean,
    categories: List<FolderEntry>,
    folder: String,
    secretEnabled: Boolean,
    onSelectCategory: (String) -> Unit,
    onOpenCategories: () -> Unit,
    viewMode: ViewMode,
    sortMode: SortMode,
    onToggleView: () -> Unit,
    onSelectSort: (SortMode) -> Unit,
    onOpenSettings: () -> Unit,
    onEnterEdit: () -> Unit,
    onHeightChange: (Int) -> Unit,
) {
    val haptics = rememberHaptics()
    var menuOpen by remember { mutableStateOf(false) }
    val colors = MaterialTheme.colorScheme

    // 雾化带：顶部接近实色，越往下越透，被盖住的内容隐约透出来但不抢戏
    val band = Brush.verticalGradient(
        0f to colors.background.copy(alpha = 0.88f),
        0.55f to colors.background.copy(alpha = 0.94f),
        0.78f to colors.background.copy(alpha = 0.86f),
        1f to colors.background.copy(alpha = 0.35f),
    )

    Column(
        Modifier
            .fillMaxWidth()
            .background(band)
            .statusBarsPadding()
            .onSizeChanged { onHeightChange(it.height) }
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(start = Space.l, end = Space.l, top = Space.xs)
                .height(if (selectionMode) 0.dp else Size.touch),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (!selectionMode) {
                CircleIconButton(Icons.Filled.Search, "搜索", onToggleSearch)
                Spacer(Modifier.width(Space.s))
                Box {
                    CircleIconButton(Icons.Filled.MoreVert, "更多", onClick = { menuOpen = true })
                    DropdownMenu(
                        expanded = menuOpen,
                        onDismissRequest = { menuOpen = false },
                        containerColor = colors.surface,
                        shape = RoundedCornerShape(Radius.sheet),
                        shadowElevation = Elev.floating,
                    ) {
                        MenuRow("编辑") { menuOpen = false; haptics.press(); onEnterEdit() }
                        MenuRow("设置") { menuOpen = false; onOpenSettings() }
                        MenuRow(if (viewMode == ViewMode.LIST) "宫格模式" else "列表模式") {
                            menuOpen = false
                            haptics.toggle()
                            onToggleView()
                        }
                        HorizontalDivider(
                            color = colors.outlineVariant,
                            modifier = Modifier.padding(horizontal = Space.s, vertical = Space.xxs),
                        )
                        MenuRow("按创建时间排序", checked = sortMode == SortMode.CREATED) {
                            menuOpen = false
                            haptics.toggle()
                            onSelectSort(SortMode.CREATED)
                        }
                        MenuRow("按修改时间排序", checked = sortMode == SortMode.UPDATED) {
                            menuOpen = false
                            haptics.toggle()
                            onSelectSort(SortMode.UPDATED)
                        }
                    }
                }
            }
        }

        Box(Modifier.fillMaxWidth().padding(top = Space.xxs)) {
            if (selectionMode) {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = Space.l, vertical = Space.s),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TextPill("取消", onCancelSelection)
                    Text(
                        text = selectionText,
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.weight(1f),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    )
                    TextPill(if (allSelected) "取消全选" else "全选", onSelectAll)
                }
            } else {
                Column(Modifier.fillMaxWidth()) {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(start = Space.l, end = Space.l, top = Space.s),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = title,
                            style = MaterialTheme.typography.headlineLarge,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.graphicsLayer {
                                // 上滑时标题缩小钻到左上角按钮行里，不消失
                                val c = collapse()
                                val scale = 1f - 0.42f * c
                                scaleX = scale
                                scaleY = scale
                                transformOrigin = TransformOrigin(0f, 0f)
                                translationY = -c * 38.dp.toPx()
                            },
                        )
                    }
                    Row(
                        Modifier
                            .padding(start = Space.l + 4.dp, top = Space.xxs)
                            .graphicsLayer { alpha = (1f - collapse() * 2.2f).coerceIn(0f, 1f) },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        AccentDot(size = 6)
                        Spacer(Modifier.width(Space.xs))
                        Text(
                            text = subtitle,
                            style = MaterialTheme.typography.labelMedium,
                            color = colors.onSurfaceVariant,
                        )
                    }
                }
            }
        }

        if (searchOpen) {
            SearchField(query, onQueryChange, onClose = onToggleSearch)
        }

        if (!selectionMode) {
            Spacer(Modifier.height(if (searchOpen) Space.xs else Space.s))
            Row(Modifier.fillMaxWidth()) {
                CategoryRow(
                    categories = categories,
                    folder = folder,
                    secretEnabled = secretEnabled,
                    onSelectCategory = onSelectCategory,
                    onOpenCategories = onOpenCategories,
                )
            }
            Spacer(Modifier.height(Space.xs))
        }
    }
}

@Composable
private fun MenuRow(text: String, checked: Boolean = false, onClick: () -> Unit) {
    DropdownMenuItem(
        text = { Text(text, style = MaterialTheme.typography.bodyMedium) },
        trailingIcon = {
            if (checked) {
                Icon(
                    Icons.Filled.Check,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp),
                )
            }
        },
        onClick = onClick,
    )
}

@Composable
private fun SearchField(query: String, onQueryChange: (String) -> Unit, onClose: () -> Unit) {
    val focus = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    LaunchedEffect(Unit) {
        focus.requestFocus()
        keyboard?.show()
    }
    Surface(
        modifier = Modifier.fillMaxWidth().padding(horizontal = Space.l, vertical = Space.xs),
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = Elev.chip,
    ) {
        Row(
            Modifier.fillMaxWidth().padding(start = Space.l, end = Space.s, top = 14.dp, bottom = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.weight(1f)) {
                if (query.isEmpty()) {
                    Text(
                        text = "搜索标题和正文",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.outline,
                    )
                }
                BasicTextField(
                    value = query,
                    onValueChange = onQueryChange,
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().focusRequester(focus),
                    textStyle = MaterialTheme.typography.bodyMedium.copy(
                        color = MaterialTheme.colorScheme.onSurface,
                    ),
                )
            }
            Box(
                Modifier
                    .size(28.dp)
                    .clickable(onClick = onClose),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Filled.Close,
                    contentDescription = "关闭搜索",
                    tint = MaterialTheme.colorScheme.outline,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
    }
}

@Composable
private fun CategoryRow(
    categories: List<FolderEntry>,
    folder: String,
    secretEnabled: Boolean,
    onSelectCategory: (String) -> Unit,
    onOpenCategories: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = Space.l),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // 左边这个方块按钮 = 进入分组管理页（不是切视图）
        Surface(
            onClick = { onOpenCategories() },
            shape = RoundedCornerShape(10.dp),
            color = MaterialTheme.colorScheme.surface,
            shadowElevation = Elev.chip,
            modifier = Modifier.size(Size.categoryButton),
        ) {
            Box(contentAlignment = Alignment.Center) { LayersGlyph() }
        }
        Capsule(selected = folder.isEmpty(), onClick = { onSelectCategory("") }) {
            Text("全部", style = MaterialTheme.typography.labelMedium)
        }
        categories.forEach { entry ->
            Capsule(
                selected = folder == entry.path,
                onClick = { onSelectCategory(entry.path) },
            ) {
                AccentDot(size = 10)
                Spacer(Modifier.width(Space.xxs))
                Text(entry.name, style = MaterialTheme.typography.labelMedium)
            }
        }
        if (secretEnabled) {
            Capsule(
                selected = folder == NotesStore.PRIVATE,
                onClick = { onSelectCategory(NotesStore.PRIVATE) },
            ) {
                Icon(
                    Icons.Filled.Lock,
                    contentDescription = null,
                    modifier = Modifier.size(13.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.width(Space.xxs))
                Text("私密", style = MaterialTheme.typography.labelMedium)
            }
        }
        Capsule(
            selected = folder == NotesStore.TRASH,
            onClick = { onSelectCategory(NotesStore.TRASH) },
        ) {
            Icon(
                Icons.Filled.Delete,
                contentDescription = null,
                modifier = Modifier.size(13.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.width(Space.xxs))
            Text("最近删除", style = MaterialTheme.typography.labelMedium)
        }
    }
}

// 分组（层叠）图标：三层横条，比默认图标更像"分类"
@Composable
private fun LayersGlyph() {
    val tint = MaterialTheme.colorScheme.onSurface
    Canvas(Modifier.size(18.dp)) {
        val w = size.width
        val h = size.height
        val bar = h * 0.18f
        val r = CornerRadius(bar / 2)
        drawRoundRect(tint.copy(alpha = 0.45f), Offset(w * 0.20f, h * 0.12f), GeomSize(w * 0.60f, bar), r)
        drawRoundRect(tint.copy(alpha = 0.7f), Offset(w * 0.12f, h * 0.38f), GeomSize(w * 0.76f, bar), r)
        drawRoundRect(tint, Offset(w * 0.04f, h * 0.64f), GeomSize(w * 0.92f, bar), r)
    }
}
