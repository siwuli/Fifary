package io.github.fifary.data

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.snapshots.SnapshotStateList

// 一条笔记 = 一个 Markdown 文件。分类就是它所在的目录（Obsidian 逻辑），
// path 是相对 vault 根的路径，例如 "工作/周会记录.md"。
@Immutable
data class Note(
    val path: String,
    val body: String,
    val createdAt: Long,
    val updatedAt: Long,
    val pinned: Boolean = false,
) {
    val name: String get() = path.substringAfterLast('/').removeSuffix(".md")
    val folder: String get() = path.substringBeforeLast('/', "")
}

@Immutable
data class FolderEntry(
    val path: String,
    val name: String,
    val noteCount: Int,
)

// 占位数据：只为了验证交互。P0 接 VaultStore 后整块替换（目录改由文件系统枚举）。
object NotesStore {

    private val now = System.currentTimeMillis()

    val all: SnapshotStateList<Note> = mutableStateListOf(
        Note(
            path = "欢迎使用 Fifary.md",
            body = "这是一个纯本地 Markdown 笔记客户端。\n\n" +
                "- 不配置任何服务器也能完整使用\n" +
                "- 同步走标准 WebDAV，随时可换\n" +
                "- 分类就是目录，和 Obsidian 一样：文件怎么放，App 里就怎么显示",
            createdAt = now - 60_000,
            updatedAt = now - 60_000,
            pinned = true,
        ),
        Note(
            path = "购物清单.md",
            body = "- 咖啡豆\n- 牛奶\n- 全麦面包\n- 洗衣液\n\n周末去超市前再看一眼。",
            createdAt = now - 3_600_000,
            updatedAt = now - 3_600_000,
        ),
        Note(
            path = "灵感收集.md",
            body = "- 通知栏直接新建笔记\n- 长按快捷记录，落盘后自动收起\n- 搜索按修改时间排序，而不是相关度",
            createdAt = now - 400_000_000,
            updatedAt = now - 400_000_000,
        ),
        Note(
            path = "日记/2026-05-12.md",
            body = "今天把列表的跟手返回调通了。\n\n手指往回拉的时候页面跟着走，松手够远才真的退出，这个感觉比纯动画自然很多。",
            createdAt = now - 7_200_000,
            updatedAt = now - 7_200_000,
        ),
        Note(
            path = "日记/2026-05-10.md",
            body = "整理了一遍目录结构。\n\n工作、日记、读书分开，找东西快很多。",
            createdAt = now - 90_000_000,
            updatedAt = now - 90_000_000,
        ),
        Note(
            path = "工作/周会记录.md",
            body = "结论\n\n1. 本周先把手感跑通，再接文件读写\n2. 同步功能排在文件之后\n3. 冲突策略默认保留两份，不覆盖\n\n待确认\n\n- 是否需要平板布局\n- 图片附件是否单独目录",
            createdAt = now - 26_000_000,
            updatedAt = now - 26_000_000,
        ),
        Note(
            path = "工作/需求草案.md",
            body = "目标\n\n支持任意标准 WebDAV，不绑定服务商。\n\n约束\n\n- 只发用户配置的请求\n- 无账号体系",
            createdAt = now - 500_000_000,
            updatedAt = now - 500_000_000,
        ),
        Note(
            path = "读书/深度工作.md",
            body = "注意力是稀缺资源。把大块时间留给不需要即时反馈的工作。\n\n忙碌不等于高效。",
            createdAt = now - 172_000_000,
            updatedAt = now - 172_000_000,
        ),
        Note(
            path = "读书/代码整洁之道.md",
            body = "注释只在代码无法表达「为什么」的时候写。\n\n能删的代码就是最好的代码。",
            createdAt = now - 800_000_000,
            updatedAt = now - 800_000_000,
        ),
    )

    fun byPath(path: String): Note? = all.firstOrNull { it.path == path }

    fun notesIn(folder: String): List<Note> = all.filter { it.folder == folder }

    // 空目录：真实文件系统里目录可以没有笔记，占位阶段单独记一份
    private val emptyFolders = mutableStateListOf<String>()

    // 目录由笔记路径 + 空目录推导：占位阶段没有真实文件系统，
    // 接 VaultStore 后改成枚举目录。
    fun foldersIn(folder: String): List<FolderEntry> {
        val prefix = if (folder.isEmpty()) "" else "$folder/"
        val names = LinkedHashSet<String>()
        for (note in all) {
            if (!note.path.startsWith(prefix)) continue
            val rest = note.path.removePrefix(prefix)
            if (rest.contains('/')) names.add(rest.substringBefore('/'))
        }
        for (empty in emptyFolders) {
            if (!empty.startsWith(prefix)) continue
            val rest = empty.removePrefix(prefix)
            if (rest.isNotEmpty() && !rest.contains('/')) names.add(rest)
        }
        return names.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it }).map { name ->
            val path = if (folder.isEmpty()) name else "$folder/$name"
            FolderEntry(path = path, name = name, noteCount = countUnder(path))
        }
    }

    fun createFolder(name: String): String? {
        val clean = name.trim()
        if (clean.isEmpty() || clean.contains('/')) return null
        val path = clean
        if (emptyFolders.contains(path) || foldersIn("").any { it.path == path }) return null
        emptyFolders.add(path)
        return path
    }

    fun deleteFolder(path: String) {
        all.removeAll { it.path.startsWith("$path/") }
        emptyFolders.removeAll { it == path || it.startsWith("$path/") }
    }

    private fun countUnder(path: String): Int =
        all.count { it.path == path || it.path.startsWith("$path/") }    fun create(folder: String): Note {
        val base = "未命名笔记"
        var path = if (folder.isEmpty()) "$base.md" else "$folder/$base.md"
        var index = 2
        while (all.any { it.path == path }) {
            path = if (folder.isEmpty()) "$base $index.md" else "$folder/$base $index.md"
            index++
        }
        val time = System.currentTimeMillis()
        val note = Note(path = path, body = "", createdAt = time, updatedAt = time)
        all.add(0, note)
        return note
    }

    fun updateBody(path: String, body: String) {
        val index = all.indexOfFirst { it.path == path }
        if (index < 0) return
        all[index] = all[index].copy(body = body, updatedAt = System.currentTimeMillis())
    }

    fun togglePin(paths: Set<String>) {
        all.replaceAll { note ->
            if (note.path in paths) note.copy(pinned = !note.pinned) else note
        }
    }

    fun delete(paths: Set<String>) {
        all.removeAll { note ->
            paths.any { it == note.path || note.path.startsWith("$it/") }
        }
    }
}
