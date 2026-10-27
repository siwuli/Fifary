package io.github.fifary.ui.list

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed as gridItemsIndexed
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import io.github.fifary.data.FolderEntry
import io.github.fifary.data.Note
import io.github.fifary.data.SortMode
import io.github.fifary.data.ViewMode
import io.github.fifary.ui.components.AccentDot
import io.github.fifary.ui.components.CircleButton
import io.github.fifary.ui.components.TextPill
import io.github.fifary.ui.components.rememberHaptics
import io.github.fifary.ui.theme.Elev
import io.github.fifary.ui.theme.Space
import kotlinx.coroutines.delay

@Composable
fun NoteListScreen(
    folder: String,
    categories: List<FolderEntry>,
    notes: List<Note>,
    viewMode: ViewMode,
    sortMode: SortMode,
        onSelectCategory: (String) -> Unit,
    onOpenNote: (Note) -> Unit,
    onCreate: () -> Unit,
    onDelete: (Set<String>) -> Unit,
    onPin: (Set<String>) -> Unit,
    onToggleView: () -> Unit,
    onSelectSort: (SortMode) -> Unit,
    onOpenCategories: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    val haptics = rememberHaptics()
    var selected by remember(folder) { mutableStateOf(emptySet<String>()) }
    var searchOpen by remember(folder) { mutableStateOf(false) }
    var query by remember(folder) { mutableStateOf("") }
    val selectionMode = selected.isNotEmpty()

    // 入场错峰只在 App 首次进入时播一次，切分类不再逐项延迟（否则"不跟手"）
    var introDone by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(400)
        introDone = true
    }

    // 切分类时整块内容快速淡入，替代逐项动画
    val contentAlpha = remember { Animatable(1f) }
    LaunchedEffect(folder) {
        contentAlpha.snapTo(0.55f)
        contentAlpha.animateTo(1f, tween(150))
    }

    BackHandler(enabled = selectionMode) { selected = emptySet() }

    val shownNotes = if (query.isBlank()) notes else notes.filter { it.name.contains(query, true) }
    val total = shownNotes.size
    val allSelected = shownNotes.isNotEmpty() && shownNotes.all { it.path in selected }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            // 必须给底色：自定义顶栏不透明，否则列表滚动时会从标题后面透出来
            Column(
                Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.background)
                    .statusBarsPadding()
            ) {
                if (selectionMode) {
                    SelectionHeader(
                        count = selected.size,
                        allSelected = allSelected,
                        onCancel = { selected = emptySet() },
                        onSelectAll = {
                            haptics.toggle()
                            selected = if (allSelected) emptySet() else shownNotes.map { it.path }.toSet()
                        },
                    )
                } else {
                    ListHeader(
                        title = if (folder.isEmpty()) "笔记" else folder,
                        subtitle = "共 $total 项 · 仅本地",
                                                searchOpen = searchOpen,
                        query = query,
                        onQueryChange = { query = it },
                        onToggleSearch = {
                            searchOpen = !searchOpen
                            if (!searchOpen) query = ""
                        },
                        categories = categories,
                        folder = folder,
                        onSelectCategory = onSelectCategory,
                        onOpenCategories = onOpenCategories,
                        viewMode = viewMode,
                        sortMode = sortMode,
                        onToggleView = onToggleView,
                        onSelectSort = onSelectSort,
                        onOpenSettings = onOpenSettings,
                    )
                }
                Spacer(Modifier.height(Space.s))
            }
        },
        bottomBar = {
            if (selectionMode) {
                SelectionActionBar(
                    onPin = {
                        haptics.toggle()
                        onPin(selected)
                        selected = emptySet()
                    },
                    onDelete = {
                        haptics.toggle()
                        onDelete(selected)
                        selected = emptySet()
                    },
                )
            }
        },
        floatingActionButton = {
            if (!selectionMode) {
                FloatingNewButton(onCreate)
            }
        },
    ) { inner ->
        val contentPadding = PaddingValues(
            start = Space.l,
            end = Space.l,
            top = inner.calculateTopPadding() + Space.xxs,
            bottom = inner.calculateBottomPadding() + 96.dp,
        )

        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer { alpha = contentAlpha.value }
        ) {
            if (total == 0) {
                EmptyState(
                    modifier = Modifier.fillMaxSize().padding(inner),
                    text = if (query.isBlank()) "这个分组还没有笔记" else "没有匹配的笔记",
                )
            } else if (viewMode == ViewMode.GRID) {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = contentPadding,
                    horizontalArrangement = Arrangement.spacedBy(Space.xs),
                    verticalArrangement = Arrangement.spacedBy(Space.xs),
                ) {
                    gridItemsIndexed(
                        items = shownNotes,
                        key = { _, note -> note.path },
                        contentType = { _, _ -> "note" },
                    ) { index, note ->
                        NoteCard(
                            modifier = Modifier.animateItem(),
                            note = note,
                            grid = true,
                            selectionMode = selectionMode,
                            selected = note.path in selected,
                            index = index,
                            animateIn = !introDone,
                            onOpen = { onOpenNote(note) },
                            onLongPress = {
                                selected = if (note.path in selected) selected - note.path else selected + note.path
                            },
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = contentPadding,
                    verticalArrangement = Arrangement.spacedBy(Space.xs),
                ) {
                    itemsIndexed(
                        items = shownNotes,
                        key = { _, note -> note.path },
                        contentType = { _, _ -> "note" },
                    ) { index, note ->
                        NoteCard(
                            modifier = Modifier.animateItem(),
                            note = note,
                            grid = false,
                            selectionMode = selectionMode,
                            selected = note.path in selected,
                            index = index,
                            animateIn = !introDone,
                            onOpen = { onOpenNote(note) },
                            onLongPress = {
                                selected = if (note.path in selected) selected - note.path else selected + note.path
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun FloatingNewButton(onCreate: () -> Unit) {
    val haptics = rememberHaptics()
    Surface(
        onClick = { haptics.press(); onCreate() },
        shape = CircleShape,
        color = MaterialTheme.colorScheme.primary,
        contentColor = MaterialTheme.colorScheme.onPrimary,
        shadowElevation = Elev.floating,
        modifier = Modifier.size(58.dp),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(Icons.Filled.Add, contentDescription = "新建笔记", modifier = Modifier.size(26.dp))
        }
    }
}

@Composable
private fun SelectionHeader(
    count: Int,
    allSelected: Boolean,
    onCancel: () -> Unit,
    onSelectAll: () -> Unit,
) {
    Column(Modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = Space.l, vertical = Space.xs),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextPill("取消", onCancel)
            Spacer(Modifier.weight(1f))
            TextPill(if (allSelected) "取消全选" else "全选", onSelectAll)
        }
        Text(
            text = "选择项目",
            style = MaterialTheme.typography.headlineLarge,
            modifier = Modifier.padding(start = Space.l, top = Space.s),
        )
        Row(
            Modifier.padding(start = Space.l + 4.dp, top = Space.xs),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AccentDot(size = 6)
            Spacer(Modifier.width(Space.xs))
            Text(
                text = "已选 $count 项",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.height(Space.m))
    }
}

@Composable
private fun SelectionActionBar(onPin: () -> Unit, onDelete: () -> Unit) {
    Box(
        Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = Space.xl, vertical = Space.s),
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surface,
            shadowElevation = Elev.floating,
        ) {
            Row(
                Modifier.padding(horizontal = Space.xl, vertical = Space.s),
                horizontalArrangement = Arrangement.spacedBy(40.dp),
            ) {
                ActionItem(Icons.Filled.Star, "置顶", onPin)
                ActionItem(Icons.Filled.Delete, "删除", onDelete)
            }
        }
    }
}

@Composable
private fun ActionItem(icon: ImageVector, label: String, onClick: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        CircleButton(onClick = onClick, subtle = true) {
            Icon(icon, contentDescription = label, modifier = Modifier.size(22.dp))
        }
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = Space.xxs),
        )
    }
}

@Composable
private fun EmptyState(modifier: Modifier, text: String) {
    Column(
        modifier,
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier
                .size(72.dp)
                .background(MaterialTheme.colorScheme.surfaceVariant, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            AccentDot(size = 18)
        }
        Spacer(Modifier.height(Space.m))
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = "右下角新建一篇",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.outline,
            modifier = Modifier.padding(top = Space.xxs),
        )
    }
}
