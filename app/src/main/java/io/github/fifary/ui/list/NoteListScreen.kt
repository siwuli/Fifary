package io.github.fifary.ui.list

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed as gridItemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import io.github.fifary.data.FolderEntry
import io.github.fifary.data.Note
import io.github.fifary.data.NotesStore
import io.github.fifary.data.PrivateVault
import io.github.fifary.data.SortMode
import io.github.fifary.data.ViewMode
import io.github.fifary.ui.components.AccentDot
import io.github.fifary.ui.components.BarAction
import io.github.fifary.ui.components.ConfirmDialog
import io.github.fifary.ui.components.FloatingActionBar
import io.github.fifary.ui.components.TextPill
import io.github.fifary.ui.components.rememberHaptics
import io.github.fifary.ui.components.rememberSecretGate
import io.github.fifary.ui.components.springPressable
import io.github.fifary.ui.theme.Elev
import io.github.fifary.ui.theme.Radius
import io.github.fifary.ui.theme.Space

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
    onMove: (Set<String>, String) -> Unit,
    onDelete: (Set<String>) -> Unit,
    onRestore: (Set<String>) -> Unit,
    onPurge: (Set<String>) -> Unit,
    onPin: (Set<String>) -> Unit,
    onSetSecret: (Set<String>, Boolean) -> Unit,
    onToggleView: () -> Unit,
    onSelectSort: (SortMode) -> Unit,
    onOpenCategories: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    val haptics = rememberHaptics()
    val gate = rememberSecretGate()
    val inTrash = folder == NotesStore.TRASH
    val privateView = folder == NotesStore.PRIVATE
    val locked = privateView && !PrivateVault.unlocked

    var editing by remember(folder) { mutableStateOf(false) }
    var selected by remember(folder) { mutableStateOf(emptySet<String>()) }
    var searchOpen by remember(folder) { mutableStateOf(false) }
    var query by remember(folder) { mutableStateOf("") }
    var pinnedCollapsed by remember(folder) { mutableStateOf(false) }
    var headerHeight by remember { mutableStateOf(0.dp) }
    var askDelete by remember { mutableStateOf(false) }
    var askPurge by remember { mutableStateOf(false) }
    var askSecret by remember { mutableStateOf(false) }
    var moveOpen by remember { mutableStateOf(false) }

    val selectionMode = editing

    fun toggle(path: String) {
        haptics.toggle()
        editing = true
        selected = if (path in selected) selected - path else selected + path
    }

    fun exitSelection() {
        editing = false
        selected = emptySet()
    }

    LaunchedEffect(Unit) { NotesStore.purgeExpiredTrash() }

    // 离开私密空间就自动上锁，跟市面上的私密空间一致
    DisposableEffect(folder) {
        onDispose { if (privateView) PrivateVault.lock() }
    }
    BackHandler(enabled = selectionMode) { exitSelection() }
    BackHandler(enabled = !selectionMode && searchOpen) {
        searchOpen = false
        query = ""
    }

    // 搜索标题 + 正文
    val shown = if (query.isBlank()) notes else notes.filter { NotesStore.matches(it, query) }
    val pinned = if (pinnedCollapsed) emptyList() else shown.filter { it.pinned }
    val others = shown.filter { !it.pinned }
    val hasPinned = shown.any { it.pinned }
    val allSelected = shown.isNotEmpty() && shown.all { it.path in selected }

    val listState = rememberLazyListState()
    val gridState = rememberLazyGridState()
    val density = LocalDensity.current
    val collapseThreshold = with(density) { 72.dp.toPx() }
    val collapse = remember(viewMode) {
        derivedStateOf {
            val grid = viewMode == ViewMode.GRID
            val index = if (grid) gridState.firstVisibleItemIndex else listState.firstVisibleItemIndex
            val offset = if (grid) {
                gridState.firstVisibleItemScrollOffset
            } else {
                listState.firstVisibleItemScrollOffset
            }
            // 滑过第一项之后 offset 会归零，所以用 index 兜住，避免标题又"长"回去
            if (index > 0) 1f else (offset / collapseThreshold).coerceIn(0f, 1f)
        }
    }

    val title = when (folder) {
        "" -> "笔记"
        NotesStore.TRASH -> "最近删除"
        NotesStore.PRIVATE -> "私密笔记"
        else -> folder
    }
    val subtitle = when {
        query.isNotBlank() -> "找到 ${shown.size} 篇"
        inTrash -> "${shown.size} 篇 · 删除后保留 30 天"
        privateView -> "${shown.size} 篇 · 已加密"
        else -> "${shown.size} 篇笔记"
    }

    // 底部多留一屏：内容很少也要能滑，滑动才能把标题带上去
    val tail = with(LocalConfiguration.current) { (screenHeightDp * 0.45f).dp }

    Box(Modifier.fillMaxSize()) {
        if (locked) {
            LockedState(Modifier.fillMaxSize(), onUnlock = { gate.run { } })
        } else if (shown.isEmpty()) {
            EmptyState(
                modifier = Modifier.fillMaxSize(),
                lockedHint = privateView,
                text = when {
                    query.isNotBlank() -> "没有匹配的笔记"
                    inTrash -> "最近删除是空的"
                    else -> "这个分组还没有笔记"
                },
            )
        } else if (viewMode == ViewMode.GRID) {
            LazyVerticalGrid(
                state = gridState,
                columns = GridCells.Fixed(2),
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    start = Space.l,
                    end = Space.l,
                    top = headerHeight + Space.s,
                    bottom = tail,
                ),
                horizontalArrangement = Arrangement.spacedBy(Space.xs),
                verticalArrangement = Arrangement.spacedBy(Space.xs),
            ) {
                if (hasPinned) {
                    item(key = "h-pin", span = { GridItemSpan(maxLineSpan) }) {
                        SectionHeader("置顶", collapsible = true, collapsed = pinnedCollapsed) {
                            haptics.toggle()
                            pinnedCollapsed = !pinnedCollapsed
                        }
                    }
                }
                gridItemsIndexed(items = pinned, key = { _, n -> n.path }) { index, note ->
                    NoteCard(
                        modifier = Modifier.animateItem(),
                        note = note,
                        grid = true,
                        selectionMode = selectionMode,
                        selected = note.path in selected,
                        index = index,
                        animateIn = false,
                        onOpen = { if (selectionMode) toggle(note.path) else onOpenNote(note) },
                        onLongPress = {
                            if (selectionMode) {
                                toggle(note.path)
                            } else {
                                haptics.longPress()
                                editing = true
                                selected = setOf(note.path)
                            }
                        },
                    )
                }
                if (hasPinned) {
                    item(key = "h-other", span = { GridItemSpan(maxLineSpan) }) {
                        SectionHeader("其他", collapsible = false, collapsed = false) {}
                    }
                }
                gridItemsIndexed(items = others, key = { _, n -> n.path }) { index, note ->
                    NoteCard(
                        modifier = Modifier.animateItem(),
                        note = note,
                        grid = true,
                        selectionMode = selectionMode,
                        selected = note.path in selected,
                        index = index,
                        animateIn = false,
                        onOpen = { if (selectionMode) toggle(note.path) else onOpenNote(note) },
                        onLongPress = {
                            if (selectionMode) {
                                toggle(note.path)
                            } else {
                                haptics.longPress()
                                editing = true
                                selected = setOf(note.path)
                            }
                        },
                    )
                }
            }
        } else {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    start = Space.l,
                    end = Space.l,
                    top = headerHeight + Space.s,
                    bottom = tail,
                ),
                verticalArrangement = Arrangement.spacedBy(Space.xs),
            ) {
                if (hasPinned) {
                    item(key = "h-pin") {
                        SectionHeader("置顶", collapsible = true, collapsed = pinnedCollapsed) {
                            haptics.toggle()
                            pinnedCollapsed = !pinnedCollapsed
                        }
                    }
                }
                itemsIndexed(items = pinned, key = { _, n -> n.path }) { index, note ->
                    NoteCard(
                        modifier = Modifier.animateItem(),
                        note = note,
                        grid = false,
                        selectionMode = selectionMode,
                        selected = note.path in selected,
                        index = index,
                        animateIn = false,
                        onOpen = { if (selectionMode) toggle(note.path) else onOpenNote(note) },
                        onLongPress = {
                            if (selectionMode) {
                                toggle(note.path)
                            } else {
                                haptics.longPress()
                                editing = true
                                selected = setOf(note.path)
                            }
                        },
                    )
                }
                if (hasPinned) {
                    item(key = "h-other") {
                        SectionHeader("其他", collapsible = false, collapsed = false) {}
                    }
                }
                itemsIndexed(items = others, key = { _, n -> n.path }) { index, note ->
                    NoteCard(
                        modifier = Modifier.animateItem(),
                        note = note,
                        grid = false,
                        selectionMode = selectionMode,
                        selected = note.path in selected,
                        index = index,
                        animateIn = false,
                        onOpen = { if (selectionMode) toggle(note.path) else onOpenNote(note) },
                        onLongPress = {
                            if (selectionMode) {
                                toggle(note.path)
                            } else {
                                haptics.longPress()
                                editing = true
                                selected = setOf(note.path)
                            }
                        },
                    )
                }
            }
        }

        // 悬浮顶栏：永远在最上层，内容从它下面滑过去
        ListHeader(
            title = title,
            subtitle = subtitle,
            collapse = { collapse.value },
            selectionMode = selectionMode,
            selectionText = if (allSelected) "已选择全部项" else "已选择 ${selected.size} 项",
            searchOpen = searchOpen,
            query = query,
            onQueryChange = { query = it },
            onToggleSearch = {
                searchOpen = !searchOpen
                if (!searchOpen) query = ""
            },
            onCancelSelection = { exitSelection() },
            onSelectAll = {
                haptics.toggle()
                selected = if (allSelected) emptySet() else shown.map { it.path }.toSet()
            },
            allSelected = allSelected,
            categories = categories,
            folder = folder,
            secretEnabled = PrivateVault.enabled,
            onSelectCategory = onSelectCategory,
            onOpenCategories = onOpenCategories,
            viewMode = viewMode,
            sortMode = sortMode,
            onToggleView = onToggleView,
            onSelectSort = onSelectSort,
            onOpenSettings = onOpenSettings,
            onEnterEdit = {
                exitSelection()
                editing = true
            },
            onHeightChange = { headerHeight = with(density) { it.toDp() } },
        )

        if (selectionMode) {
            FloatingActionBar(
                actions = when {
                    inTrash -> listOf(
                        BarAction("恢复", enabled = selected.isNotEmpty(), icon = Icons.Filled.Refresh, onClick = {
                            haptics.toggle()
                            onRestore(selected)
                            exitSelection()
                        }),
                        BarAction("彻底删除", enabled = selected.isNotEmpty(), icon = Icons.Filled.Delete, onClick = { askPurge = true }),
                    )
                    privateView -> listOf(
                        BarAction("取消私密", enabled = selected.isNotEmpty(), icon = Icons.Filled.Lock, onClick = {
                            val key = PrivateVault.key
                            if (key != null) {
                                haptics.toggle()
                                onSetSecret(selected, false)
                                exitSelection()
                            }
                        }),
                        BarAction("删除", enabled = selected.isNotEmpty(), icon = Icons.Filled.Delete, onClick = { askDelete = true }),
                    )
                    else -> listOf(
                        BarAction("移动", enabled = selected.isNotEmpty(), icon = Icons.AutoMirrored.Filled.ExitToApp, onClick = { moveOpen = true }),
                        BarAction("设为私密", enabled = selected.isNotEmpty(), icon = Icons.Filled.Lock, onClick = { askSecret = true }),
                        BarAction("置顶", enabled = selected.isNotEmpty(), icon = Icons.Filled.Star, onClick = {
                            haptics.toggle()
                            onPin(selected)
                            exitSelection()
                        }),
                        BarAction("删除", enabled = selected.isNotEmpty(), icon = Icons.Filled.Delete, onClick = { askDelete = true }),
                    )
                },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(bottom = Space.m)
                    .fillMaxWidth(0.92f),
            )
        } else if (!inTrash && !locked) {
            Box(
                Modifier
                    .align(Alignment.BottomEnd)
                    .navigationBarsPadding()
                    .padding(end = Space.l, bottom = Space.xl)
            ) {
                FloatingNewButton(onCreate)
            }
        }
    }

    if (askDelete) {
        ConfirmDialog(
            title = "删除 ${selected.size} 篇笔记？",
            text = "会移到「最近删除」，保留 30 天，期间都能恢复。",
            confirmText = "删除",
            onDismiss = { askDelete = false },
            onConfirm = {
                haptics.toggle()
                askDelete = false
                onDelete(selected)
                exitSelection()
            },
        )
    }

    if (askPurge) {
        ConfirmDialog(
            title = "彻底删除 ${selected.size} 篇？",
            text = "不会进最近删除，删了就找不回来了。",
            confirmText = "彻底删除",
            onDismiss = { askPurge = false },
            onConfirm = {
                haptics.toggle()
                askPurge = false
                onPurge(selected)
                exitSelection()
            },
        )
    }

    if (askSecret) {
        ConfirmDialog(
            title = "设为私密？",
            text = "${selected.size} 篇笔记会移进「私密笔记」，之后进入私密笔记要输密码。",
            confirmText = "设为私密",
            onDismiss = { askSecret = false },
            onConfirm = {
                askSecret = false
                val paths = selected
                gate.run {
                    haptics.toggle()
                    onSetSecret(paths, true)
                    exitSelection()
                }
            },
        )
    }

    if (moveOpen) {
        MoveDialog(
            categories = categories,
            onDismiss = { moveOpen = false },
            onPick = { target ->
                haptics.toggle()
                moveOpen = false
                onMove(selected, target)
                exitSelection()
            },
        )
    }

}

@Composable
private fun SectionHeader(
    title: String,
    collapsible: Boolean,
    collapsed: Boolean,
    onToggle: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(top = Space.s, bottom = Space.xxs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text = title, style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.weight(1f))
        if (collapsible) {
            Box(
                Modifier
                    .size(28.dp)
                    .clickable(onClick = onToggle),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = if (collapsed) Icons.Filled.KeyboardArrowDown else Icons.Filled.KeyboardArrowUp,
                    contentDescription = if (collapsed) "展开置顶" else "折叠置顶",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(22.dp),
                )
            }
        }
    }
}

@Composable
private fun MoveDialog(
    categories: List<FolderEntry>,
    onDismiss: () -> Unit,
    onPick: (String) -> Unit,
) {
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(Radius.sheet),
        containerColor = MaterialTheme.colorScheme.surface,
        titleContentColor = MaterialTheme.colorScheme.onSurface,
        title = { Text("移动到", style = MaterialTheme.typography.titleMedium) },
        text = {
            Column(Modifier.fillMaxWidth()) {
                categories.forEach { entry ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .springPressable(onClick = { onPick(entry.path) })
                            .padding(vertical = Space.s),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        AccentDot(size = 10)
                        Spacer(Modifier.width(Space.s))
                        Text(entry.name, style = MaterialTheme.typography.bodyMedium)
                        Spacer(Modifier.weight(1f))
                        Text(
                            text = "${entry.noteCount}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.outline,
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextPill("取消", onDismiss)
        },
    )
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
private fun LockedState(modifier: Modifier, onUnlock: () -> Unit) {
    Column(
        modifier.padding(top = 160.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier
                .size(72.dp)
                .background(MaterialTheme.colorScheme.surfaceVariant, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Filled.Lock,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(26.dp),
            )
        }
        Spacer(Modifier.height(Space.m))
        Surface(
            onClick = onUnlock,
            shape = RoundedCornerShape(Radius.card),
            color = MaterialTheme.colorScheme.surface,
        ) {
            Text(
                text = "输入密码进入私密笔记",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(horizontal = Space.l, vertical = Space.s),
            )
        }
    }
}

@Composable
private fun EmptyState(modifier: Modifier, text: String, lockedHint: Boolean = false) {
    Column(
        modifier.padding(top = 160.dp),
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
        if (lockedHint) {
            Text(
                text = "离开这一页或 App 进后台会自动上锁",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.outline,
                modifier = Modifier.padding(top = Space.xs),
            )
        }
    }
}
