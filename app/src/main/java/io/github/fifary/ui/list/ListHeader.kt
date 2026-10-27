package io.github.fifary.ui.list

import androidx.compose.foundation.Canvas
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
import androidx.compose.material.icons.filled.KeyboardArrowDown
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
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size as GeomSize
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.fifary.data.FolderEntry
import io.github.fifary.data.NotesStore
import io.github.fifary.data.SortMode
import io.github.fifary.data.ViewMode
import io.github.fifary.ui.components.Capsule
import io.github.fifary.ui.components.CircleIconButton
import io.github.fifary.ui.components.NotebookGlyph
import io.github.fifary.ui.components.TextPill
import io.github.fifary.ui.theme.Elev
import io.github.fifary.ui.theme.Radius
import io.github.fifary.ui.theme.Size
import io.github.fifary.ui.theme.Space

// 悬浮顶栏，尺寸照参考 App 的 COUI AppBar：
//  · coui_appbar_title_toolbar_height   52dip，常驻工具条
//  · coui_appbar_title_expanded_margin_top 54dip，大标题的起始位置
//  · 大标题 coui_appbar_title_expanded_text_size 32dip → 收起后 coui_appbar_title_collapsed_text_size 24dip
//  · 副标题 coui_appbar_subtitle_text_size 14dip，收起后仍然留在标题下面
// 所以大标题是"缩小 + 上移落进工具条"，不是淡出一个再淡入另一个；
// 分类胶囊整排滑进工具条底下（裁在胶囊那一格里）。
// 雾化带只在内容滑到工具条下面时才出现，静止时完全透明，不遮内容。
private val ToolbarHeight = 56.dp
// 收起后要同时装下小标题和篇数，所以常驻条比单个标题行高一点
private val ToolbarTop = 16.dp
private val TitleTop = 54.dp
private val TitleShift = 53.dp
private val SubtitleShift = 71.dp

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
    onOpenCategoryPicker: () -> Unit,
    onCompactChange: (Int) -> Unit,
    onHeightChange: (Int) -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }
    val colors = MaterialTheme.colorScheme
    var compactPx by remember { mutableFloatStateOf(0f) }
    var chipPx by remember { mutableFloatStateOf(0f) }

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
            // 雾化只铺在常驻工具条那一条上
            .drawBehind {
                val c = collapse()
                if (c > 0.01f) {
                    drawRect(
                        brush = band,
                        size = GeomSize(size.width, compactPx + 16.dp.toPx()),
                        alpha = c,
                    )
                }
            }
            .onSizeChanged { onHeightChange(it.height) }
    ) {
        Column(Modifier.fillMaxWidth().statusBarsPadding()) {
            Spacer(Modifier.height(TitleTop))
            if (!selectionMode && !searchOpen) {
                Row(
                    Modifier
                        .padding(start = Space.l, end = Space.l)
                        .graphicsLayer {
                            val c = collapse()
                            transformOrigin = TransformOrigin(0f, 0f)
                            val scale = 1f - 0.42f * c
                            scaleX = scale
                            scaleY = scale
                            translationY = -TitleShift.toPx() * c
                        },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.headlineLarge,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    // 标题旁边的小箭头：收起后点它就换分类，不用再滚回去
                    Box(
                        Modifier
                            .size(28.dp)
                            .graphicsLayer {
                                alpha = ((collapse() - 0.3f) * 2.4f).coerceIn(0f, 1f)
                            }
                            .clickable(onClick = onOpenCategoryPicker),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            Icons.Filled.KeyboardArrowDown,
                            contentDescription = "切换分类",
                            tint = colors.onSurfaceVariant,
                            modifier = Modifier.size(22.dp),
                        )
                    }
                }
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.labelMedium,
                    color = colors.onSurfaceVariant,
                    modifier = Modifier
                        .padding(start = Space.l + 2.dp, top = Space.xxs)
                        .graphicsLayer {
                            translationY = -SubtitleShift.toPx() * collapse()
                        },
                )
                Spacer(Modifier.height(Space.xs))
                // 胶囊整排跟着手指往上走，走到副标题那儿就已经化没了
                Box(
                    Modifier
                        .fillMaxWidth()
                        .onSizeChanged { chipPx = it.height.toFloat() }
                ) {
                    CategoryRow(
                        categories = categories,
                        folder = folder,
                        secretEnabled = secretEnabled,
                        onSelectCategory = onSelectCategory,
                        onOpenCategories = onOpenCategories,
                        modifier = Modifier.graphicsLayer {
                            val c = collapse()
                            translationY = -chipPx * c
                            alpha = (1f - c * 1.3f).coerceIn(0f, 1f)
                        },
                    )
                }
                Spacer(Modifier.height(Space.xs))
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
            Spacer(Modifier.height(ToolbarTop))
            Row(
                Modifier
                    .fillMaxWidth()
                    .height(ToolbarHeight)
                    .padding(horizontal = Space.l),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (selectionMode) {
                    TextPill("取消", onCancelSelection)
                    Text(
                        text = selectionText,
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.weight(1f),
                        textAlign = TextAlign.Center,
                    )
                    TextPill(if (allSelected) "取消全选" else "全选", onSelectAll)
                } else {
                    Spacer(Modifier.weight(1f))
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
                            MenuRow("编辑") { menuOpen = false; onEnterEdit() }
                            MenuRow("设置") { menuOpen = false; onOpenSettings() }
                            MenuRow(if (viewMode == ViewMode.LIST) "宫格模式" else "列表模式") {
                                menuOpen = false
                                onToggleView()
                            }
                            HorizontalDivider(
                                color = colors.outlineVariant,
                                modifier = Modifier.padding(horizontal = Space.s, vertical = Space.xxs),
                            )
                            MenuRow("按创建时间排序", checked = sortMode == SortMode.CREATED) {
                                menuOpen = false
                                onSelectSort(SortMode.CREATED)
                            }
                            MenuRow("按修改时间排序", checked = sortMode == SortMode.UPDATED) {
                                menuOpen = false
                                onSelectSort(SortMode.UPDATED)
                            }
                        }
                    }
                }
            }
            if (searchOpen && !selectionMode) {
                SearchField(query, onQueryChange, onClose = onToggleSearch)
            }
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
    modifier: Modifier = Modifier,
) {
    Row(
        modifier
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
        categories.forEach { entry ->
            Capsule(
                selected = folder == entry.path,
                onClick = { onSelectCategory(entry.path) },
            ) {
                NotebookGlyph(size = 15)
                Spacer(Modifier.width(Space.xxs))
                Text(entry.name, style = MaterialTheme.typography.labelMedium)
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
