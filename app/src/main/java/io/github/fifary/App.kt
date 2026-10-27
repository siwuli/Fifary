package io.github.fifary

import androidx.compose.runtime.Composable
import io.github.fifary.data.Note
import io.github.fifary.data.NotesStore
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
                    val allView = screen.folder.isEmpty()
                    val sortMode = SettingsStore.sortMode
                    val notes = (if (allView) NotesStore.all else NotesStore.notesIn(screen.folder))
                        .sortedWith(
                            compareByDescending<Note> { it.pinned }
                                .thenByDescending {
                                    if (sortMode == SortMode.UPDATED) it.updatedAt else it.createdAt
                                }
                        )
                    NoteListScreen(
                        folder = screen.folder,
                        categories = NotesStore.foldersIn(""),
                        notes = notes,
                        viewMode = SettingsStore.viewMode,
                        sortMode = sortMode,
                                                // 切分组替换根页面，不堆栈：返回键始终是"退出"
                        onSelectCategory = { path -> navigator.toRoot(Screen.Notes(path)) },
                        onOpenNote = { note -> navigator.open(Screen.Editor(note.path)) },
                        onCreate = { navigator.open(Screen.Editor(NotesStore.create(screen.folder).path)) },
                        onDelete = { paths -> NotesStore.delete(paths) },
                        onPin = { paths -> NotesStore.togglePin(paths) },
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
                            note = note,
                            onBack = { navigator.back() },
                            onBodyChange = { body -> NotesStore.updateBody(note.path, body) },
                        )
                    }
                }

                Screen.Categories -> CategoryManagerScreen(
                    categories = NotesStore.foldersIn(""),
                    totalNotes = NotesStore.all.size,
                    onOpenAll = { navigator.selectCategory("") },
                    onOpenCategory = { path -> navigator.selectCategory(path) },
                    onCreate = { name -> NotesStore.createFolder(name) },
                    onDelete = { path -> NotesStore.deleteFolder(path) },
                    onClose = { navigator.back() },
                )

                Screen.Settings -> SettingsScreen(onBack = { navigator.back() })
            }
        }
    }
}
