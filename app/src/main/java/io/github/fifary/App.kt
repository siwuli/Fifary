package io.github.fifary

import io.github.fifary.ui.S
import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import io.github.fifary.data.Dav
import io.github.fifary.data.Note
import io.github.fifary.data.NotesStore
import io.github.fifary.data.PrivateVault
import io.github.fifary.data.SettingsStore
import io.github.fifary.data.SortMode
import io.github.fifary.data.ViewMode
import io.github.fifary.nav.PageHost
import io.github.fifary.nav.Screen
import io.github.fifary.nav.rememberNavigator
import io.github.fifary.ui.categories.CategoryManagerScreen
import io.github.fifary.ui.components.rememberSecretGate
import io.github.fifary.ui.editor.EditorScreen
import io.github.fifary.ui.list.NoteListScreen
import io.github.fifary.ui.settings.AboutScreen
import io.github.fifary.ui.settings.DebugScreen
import io.github.fifary.ui.settings.SettingsScreen
import io.github.fifary.ui.settings.SyncScreen
import io.github.fifary.ui.theme.FifaryTheme

@Composable
fun FifaryApp() {
    FifaryTheme {
        val navigator = rememberNavigator()
        val gate = rememberSecretGate()
        val lifecycle = LocalLifecycleOwner.current
        val context = LocalContext.current

        // 私密区域 = 私密列表或一篇私密笔记。只有真的离开这块区域才上锁：
        // 在私密列表里打开一篇笔记、或者在里面新建，都不该再要一次密码
        val inPrivate = privateArea(navigator.stack.last())
        DisposableEffect(inPrivate) {
            onDispose { if (inPrivate) PrivateVault.lock() }
        }

        DisposableEffect(lifecycle) {
            val observer = LifecycleEventObserver { _, event ->
                when (event) {
                    // 切后台就上锁：私密内容不能在最近任务里露脸
                    Lifecycle.Event.ON_STOP -> PrivateVault.lock()
                    // 回来时私密区域已经锁着了：先把密码要回来，不然这次的改动存不进去
                    Lifecycle.Event.ON_START -> {
                        // 回到前台先对一遍远端，再去开门（要密码的页面这时候可能已经锁了）
                        Dav.auto(context)
                        if (!PrivateVault.unlocked && privateArea(navigator.stack.last())) gate.run { }
                    }
                    else -> Unit
                }
            }
            lifecycle.lifecycle.addObserver(observer)
            onDispose { lifecycle.lifecycle.removeObserver(observer) }
        }

        PageHost(navigator) { screen ->
            when (screen) {
                is Screen.Notes -> {
                    val folder = screen.folder
                    val sortMode = SettingsStore.sortMode
                    val notes = NotesStore.notesFor(folder)
                        // 私密笔记存的是密文，列表和编辑器只看明文
                        .map { note -> if (note.secret) note.copy(body = PrivateVault.plain(note)) else note }
                        .sortedWith(
                            compareByDescending<Note> { it.pinned }
                                .thenByDescending {
                                    if (sortMode == SortMode.UPDATED) it.updatedAt else it.createdAt
                                }
                        )
                    NoteListScreen(
                        folder = folder,
                        categories = NotesStore.folders(),
                        notes = notes,
                        viewMode = SettingsStore.viewMode,
                        sortMode = sortMode,
                        // 切分组替换根页面，不堆栈：返回键始终是"退出"
                        onSelectCategory = { path -> navigator.toRoot(Screen.Notes(path)) },
                        onOpenNote = { note -> navigator.open(Screen.Editor(note.path)) },
                        onCreate = { navigator.open(Screen.Editor("", folder), fromBubble = true) },
                        onMove = { paths, target -> NotesStore.move(paths, target) },
                        onDelete = { paths -> NotesStore.delete(paths) },
                        onRestore = { paths -> NotesStore.restore(paths) },
                        onPurge = { paths -> NotesStore.purge(paths) },
                        onPin = { paths -> NotesStore.togglePin(paths) },
                        onSetSecret = { paths, secret ->
                            val key = PrivateVault.key
                            if (key != null) {
                                if (secret) {
                                    NotesStore.makeSecret(paths, key)
                                    // 设为私密 = 把它们挪进私密笔记那一页
                                    navigator.toRoot(Screen.Notes(NotesStore.PRIVATE))
                                } else {
                                    NotesStore.makePublic(paths, key)
                                }
                            }
                        },
                        onToggleView = {
                            SettingsStore.updateViewMode(
                                if (SettingsStore.viewMode == ViewMode.LIST) ViewMode.GRID else ViewMode.LIST
                            )
                        },
                        onSelectSort = { mode -> SettingsStore.updateSortMode(mode) },
                        onOpenCategories = { navigator.open(Screen.Categories) },
                        onOpenSettings = { navigator.open(Screen.Settings) },
                    )
                }

                is Screen.Editor -> {
                    val note = NotesStore.byPath(screen.path)
                        ?.let { n -> if (n.secret) n.copy(body = PrivateVault.plain(n)) else n }
                    // 新笔记第一次落盘后就有了自己的路径，之后（包括切后台自动存的）都得用新路径，
                    // 不然会再存出一条「xxx 2」的重复笔记
                    var path by remember(screen.path) { mutableStateOf(screen.path) }
                    val context = LocalContext.current
                    val stash: (String, String) -> Unit = { title, body ->
                        // 密码已经清掉（切过后台）时不能存私密内容：存了就是把明文写进密文文件，
                        // 或者在私密里新建出一篇不加密、列表里还看不见的笔记
                        val secret = note?.secret == true || (note == null && screen.folder == NotesStore.PRIVATE)
                        if (secret && !PrivateVault.unlocked) {
                            Toast.makeText(context, S("私密笔记已锁定，这次改动没保存"), Toast.LENGTH_SHORT).show()
                        } else {
                            path = NotesStore.save(path, screen.folder, title, body)
                        }
                    }
                    // 换笔记时必须重开编辑页：页面槽位会被复用，WebView 里还是上一条的正文，
                    // 点完成就把上一条的内容写进这一条（真把用户笔记覆盖过）
                    key(screen.path) {
                        EditorScreen(
                            note = note,
                            folder = note?.folder ?: screen.folder,
                            onDiscard = { navigator.back() },
                            onStash = stash,
                            onSave = { title, body ->
                                stash(title, body)
                                navigator.back()
                            },
                        )
                    }
                }

                Screen.Categories -> CategoryManagerScreen(
                    categories = NotesStore.folders(),
                    totalNotes = NotesStore.notesFor("").size,
                    trashCount = NotesStore.notesFor(NotesStore.TRASH).size,
                    secretCount = NotesStore.notesFor(NotesStore.PRIVATE).size,
                    secretEnabled = PrivateVault.enabled,
                    onOpenAll = { navigator.selectCategory("") },
                    onOpenCategory = { path -> navigator.selectCategory(path) },
                    onOpenTrash = { navigator.selectCategory(NotesStore.TRASH) },
                    onOpenSecret = { navigator.selectCategory(NotesStore.PRIVATE) },
                    onCreate = { name -> NotesStore.createFolder(name) },
                    onRename = { from, to -> NotesStore.renameFolder(from, to) },
                    onDelete = { path -> NotesStore.deleteFolder(path) },
                    onSetSecret = { path ->
                        PrivateVault.key?.let { key -> NotesStore.makeSecret(setOf(path), key) }
                    },
                    onReorder = { order -> SettingsStore.updateFolderOrder(order) },
                    onClose = { navigator.back() },
                )

                Screen.Settings -> SettingsScreen(
                    onBack = { navigator.back() },
                    onOpenSync = { navigator.open(Screen.Sync) },
                    onOpenAbout = { navigator.open(Screen.About) },
                    onOpenDebug = { navigator.open(Screen.Debug) },
                )

                Screen.Sync -> SyncScreen(onBack = { navigator.back() })
                Screen.About -> AboutScreen(onBack = { navigator.back() })
                Screen.Debug -> DebugScreen(onBack = { navigator.back() })
            }
        }
    }
}

// 这一屏算不算在私密区域里：私密列表，或者正在编辑一篇私密笔记
private fun privateArea(screen: Screen): Boolean = when (screen) {
    is Screen.Notes -> screen.folder == NotesStore.PRIVATE
    is Screen.Editor -> screen.folder == NotesStore.PRIVATE || NotesStore.byPath(screen.path)?.secret == true
    else -> false
}
