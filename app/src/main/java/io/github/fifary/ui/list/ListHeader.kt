package io.github.fifary.ui.list

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size as GeomSize
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.fifary.data.FolderEntry
import io.github.fifary.data.SortMode
import io.github.fifary.data.ViewMode
import io.github.fifary.ui.components.AccentDot
import io.github.fifary.ui.components.Capsule
import io.github.fifary.ui.components.CircleButton
import io.github.fifary.ui.components.CircleIconButton
import io.github.fifary.ui.components.rememberHaptics
import io.github.fifary.ui.theme.Elev
import io.github.fifary.ui.theme.Radius
import io.github.fifary.ui.theme.Size
import io.github.fifary.ui.theme.Space

@Composable
fun ListHeader(
    title: String,
    subtitle: String,
        searchOpen: Boolean,
    query: String,
    onQueryChange: (String) -> Unit,
    onToggleSearch: () -> Unit,
    categories: List<FolderEntry>,
    folder: String,
    onSelectCategory: (String) -> Unit,
    onOpenCategories: () -> Unit,
    viewMode: ViewMode,
    sortMode: SortMode,
    onToggleView: () -> Unit,
    onSelectSort: (SortMode) -> Unit,
    onOpenSettings: () -> Unit,
) {
    val haptics = rememberHaptics()
    var menuOpen by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth().padding(start = Space.l, end = Space.l, top = Space.xs),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CircleIconButton(Icons.Filled.Search, "搜索", onToggleSearch)
            Spacer(Modifier.width(Space.s))
            Box {
                CircleIconButton(Icons.Filled.MoreVert, "更多", onClick = { menuOpen = true })
                DropdownMenu(
                    expanded = menuOpen,
                    onDismissRequest = { menuOpen = false },
                    containerColor = MaterialTheme.colorScheme.surface,
                    shape = RoundedCornerShape(Radius.sheet),
                    shadowElevation = Elev.floating,
                ) {
                    MenuRow("设置") { menuOpen = false; onOpenSettings() }
                    MenuRow(if (viewMode == ViewMode.LIST) "宫格模式" else "列表模式") {
                        menuOpen = false
                        haptics.toggle()
                        onToggleView()
                    }
                    HorizontalDivider(
                        color = MaterialTheme.colorScheme.outlineVariant,
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

        if (searchOpen) {
            SearchField(query, onQueryChange)
        } else {
            Row(
                Modifier.fillMaxWidth().padding(start = Space.l, end = Space.l, top = Space.m),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.headlineLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Row(
                Modifier.padding(start = Space.l + 4.dp, top = Space.xs),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                AccentDot(size = 6)
                Spacer(Modifier.width(Space.xs))
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        Spacer(Modifier.height(Space.m))
        CategoryRow(
            categories = categories,
            folder = folder,
            onSelectCategory = onSelectCategory,
            onOpenCategories = onOpenCategories,
        )
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
private fun SearchField(query: String, onQueryChange: (String) -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth().padding(horizontal = Space.l, vertical = Space.s),
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = Elev.chip,
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = Space.l, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.weight(1f)) {
                if (query.isEmpty()) {
                    Text(
                        text = "搜索笔记标题",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.outline,
                    )
                }
                BasicTextField(
                    value = query,
                    onValueChange = onQueryChange,
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodyMedium.copy(
                        color = MaterialTheme.colorScheme.onSurface,
                    ),
                )
            }
            Icon(
                imageVector = Icons.Filled.Close,
                contentDescription = "关闭搜索",
                tint = MaterialTheme.colorScheme.outline,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

@Composable
private fun CategoryRow(
    categories: List<FolderEntry>,
    folder: String,
    onSelectCategory: (String) -> Unit,
    onOpenCategories: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = Space.l),
        horizontalArrangement = Arrangement.spacedBy(Space.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // 左边这个方形按钮 = 进入分组管理页（不是切视图）
        Surface(
            onClick = onOpenCategories,
            shape = CircleShape,
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
            Capsule(selected = folder == entry.path, onClick = { onSelectCategory(entry.path) }) {
                AccentDot(size = 12)
                Spacer(Modifier.width(Space.xs))
                Text(entry.name, style = MaterialTheme.typography.labelMedium)
            }
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


// 宫格画成 2x2 方块，列表画成三条横杠
@Composable
private fun ViewModeIcon(viewMode: ViewMode) {
    val tint = MaterialTheme.colorScheme.onSurface
    Canvas(Modifier.size(18.dp)) {
        val side = size.width
        val radius = CornerRadius(side * 0.12f)
        if (viewMode == ViewMode.LIST) {
            val cell = side * 0.4f
            val gap = side * 0.2f
            listOf(0f to 0f, (cell + gap) to 0f, 0f to (cell + gap), (cell + gap) to (cell + gap)).forEach { (x, y) ->
                drawRoundRect(tint, Offset(x, y), GeomSize(cell, cell), radius)
            }
        } else {
            val bar = side * 0.16f
            listOf(0f, side * 0.42f, side * 0.84f).forEach { y ->
                drawRoundRect(tint, Offset(0f, y), GeomSize(side, bar), radius)
            }
        }
    }
}
