package io.github.fifary

import androidx.compose.runtime.Composable
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
import io.github.fifary.ui.editor.EditorScreen
import io.github.fifary.ui.list.NoteListScreen
import io.github.fifary.ui.settings.SettingsScreen
import io.github.fifary.ui.theme.FifaryTheme

@Composable
fun FifaryApp() {
    FifaryTheme {
        val navigator = rememberNavigator()

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
                        onCreate = { navigator.open(Screen.Editor(NotesStore.create(folder).path)) },
                        onMove = { paths, target -> NotesStore.move(paths, target) },
                        onDelete = { paths -> NotesStore.delete(paths) },
                        onRestore = { paths -> NotesStore.restore(paths) },
                        onPurge = { paths -> NotesStore.purge(paths) },
                        onPin = { paths -> NotesStore.togglePin(paths) },
                        onSetSecret = { paths, secret ->
                            val key = PrivateVault.key
                            if (key != null) {
                                if (secret) NotesStore.makeSecret(paths, key) else NotesStore.makePublic(paths, key)
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
                    if (note != null) {
                        EditorScreen(
                            note = if (note.secret) note.copy(body = PrivateVault.plain(note)) else note,
                            onBack = { navigator.back() },
                            onBodyChange = { body -> NotesStore.updatePlain(note.path, body) },
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
                    onClose = { navigator.back() },
                )

                Screen.Settings -> SettingsScreen(onBack = { navigator.back() })
            }
        }
    }
}
