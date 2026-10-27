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
    // 私密笔记：body 存的是密文，只有一个密码能解开
    val secret: Boolean = false,
    // > 0 = 已进最近删除，值是被删的时间
    val deletedAt: Long = 0L,
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

    const val DEFAULT = "默认"

    // 两个虚拟分组：不是真目录，所以不能建同名分组
    const val TRASH = "最近删除"
    const val PRIVATE = "私密"

    const val TRASH_KEEP_MS = 30L * 24 * 60 * 60 * 1000

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
            path = "$DEFAULT/新想法.md",
            body = "没选分组的笔记默认放这里，和 ColorOS 便签的「默认」一样。",
            createdAt = now - 5_000_000,
            updatedAt = now - 5_000_000,
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
            body = "结论\n\n1. 本周先把手感跑通，再接文件读写\n2. 同步功能排在文件之后\n3. 冲突策略默认保留两份，不覆盖",
            createdAt = now - 26_000_000,
            updatedAt = now - 26_000_000,
            pinned = true,
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
        // 最近删除留一条，方便看 30 天倒计时
        Note(
            path = "旧的草稿.md",
            body = "写完发现方向不对，先扔着。",
            createdAt = now - 900_000_000,
            updatedAt = now - 900_000_000,
            deletedAt = now - 3 * 24 * 60 * 60 * 1000L,
        ),
    )

    fun byPath(path: String): Note? = all.firstOrNull { it.path == path }

    fun rewrite(transform: (Note) -> Note) {
        all.replaceAll(transform)
    }

    fun updateBody(path: String, body: String) {
        val index = all.indexOfFirst { it.path == path }
        if (index < 0) return
        all[index] = all[index].copy(body = body, updatedAt = System.currentTimeMillis())
    }

    // 编辑器只管明文：私密笔记在这里转成密文再存，缓存同步失效
    fun updatePlain(path: String, plain: String) {
        val note = byPath(path) ?: return
        val key = PrivateVault.key
        val stored = if (note.secret && key != null) Secret.encrypt(key, plain) else plain
        updateBody(path, stored)
        PrivateVault.forget(path)
    }

    // 列表用哪一屏：私密只出私密，最近删除只出删除的
    fun notesFor(folder: String): List<Note> = when (folder) {
        TRASH -> all.filter { it.deletedAt > 0L }
        PRIVATE -> all.filter { it.secret && it.deletedAt == 0L }
        "" -> all.filter { it.deletedAt == 0L && !it.secret }
        else -> all.filter { it.folder == folder && it.deletedAt == 0L && !it.secret }
    }

    fun countIn(folder: String): Int = notesFor(folder).size

    // 空分组：真实文件系统里目录可以没有文件，占位阶段单独记一份
    private val emptyFolders = mutableStateListOf<String>()

    // 分组永远是平铺的一级目录
    fun folders(): List<FolderEntry> {
        val names = LinkedHashSet<String>()
        names.add(DEFAULT)
        for (note in all) {
            if (note.deletedAt > 0L || note.secret || note.folder.isEmpty()) continue
            names.add(note.folder)
        }
        names.addAll(emptyFolders)
        return names.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it })
            .map { FolderEntry(path = it, name = it, noteCount = countIn(it)) }
    }

    fun createFolder(name: String): Boolean {
        val clean = name.trim()
        if (clean.isEmpty() || clean.contains('/')) return false
        if (clean == TRASH || clean == PRIVATE) return false
        if (folders().any { it.path == clean }) return false
        emptyFolders.add(clean)
        return true
    }

    fun renameFolder(from: String, to: String): Boolean {
        val clean = to.trim()
        if (clean.isEmpty() || clean.contains('/') || clean == from) return false
        if (clean == TRASH || clean == PRIVATE) return false
        if (folders().any { it.path == clean }) return false
        all.replaceAll { note ->
            if (note.folder == from) note.copy(path = "$clean/${note.name}.md") else note
        }
        if (emptyFolders.remove(from)) emptyFolders.add(clean)
        return true
    }

    fun deleteFolder(path: String) {
        val hit = all.filter { it.folder == path && it.deletedAt == 0L }.map { it.path }.toSet()
        delete(hit)
    }

    fun create(folder: String): Note {
        val target = if (folder.isEmpty() || folder == TRASH || folder == PRIVATE) DEFAULT else folder
        val base = "未命名笔记"
        var path = "$target/$base.md"
        var index = 2
        while (all.any { it.path == path }) {
            path = "$target/$base $index.md"
            index++
        }
        val time = System.currentTimeMillis()
        val note = Note(path = path, body = "", createdAt = time, updatedAt = time)
        all.add(0, note)
        return note
    }

    private fun expand(paths: Set<String>): List<Note> =
        all.filter { note -> paths.any { it == note.path || note.path.startsWith("$it/") } }

    // 删除 = 进最近删除，30 天后清掉；置顶照旧
    fun delete(paths: Set<String>) {
        val time = System.currentTimeMillis()
        val hit = expand(paths).map { it.path }.toSet()
        all.replaceAll { note ->
            if (note.path in hit) note.copy(deletedAt = time, pinned = false) else note
        }
    }

    fun restore(paths: Set<String>) {
        val hit = expand(paths).map { it.path }.toSet()
        all.replaceAll { note -> if (note.path in hit) note.copy(deletedAt = 0L) else note }
    }

    fun purge(paths: Set<String>) {
        val hit = expand(paths).map { it.path }.toSet()
        all.removeAll { it.path in hit }
    }

    fun purgeExpiredTrash() {
        val deadline = System.currentTimeMillis() - TRASH_KEEP_MS
        all.removeAll { it.deletedAt in 1L..deadline }
    }

    // 移动 = 改路径（分类即目录）
    fun move(paths: Set<String>, folder: String) {
        val target = if (folder.isEmpty()) DEFAULT else folder
        val hit = expand(paths).filter { it.deletedAt == 0L }
        for (note in hit) {
            var path = "$target/${note.name}.md"
            var index = 2
            while (all.any { it.path == path && it.path != note.path }) {
                path = "$target/${note.name} $index.md"
                index++
            }
            val at = all.indexOfFirst { it.path == note.path }
            if (at >= 0) all[at] = all[at].copy(path = path)
        }
    }

    fun togglePin(paths: Set<String>) {
        val hit = expand(paths).map { it.path }.toSet()
        all.replaceAll { note -> if (note.path in hit) note.copy(pinned = !note.pinned) else note }
    }

    // 设为私密 / 取消私密。加密和解密都在这里做，别的层只看到明文。
    fun makeSecret(paths: Set<String>, key: ByteArray) {
        val hit = expand(paths).map { it.path }.toSet()
        all.replaceAll { note ->
            if (note.path in hit && !note.secret) {
                note.copy(body = Secret.encrypt(key, note.body), secret = true)
            } else {
                note
            }
        }
    }

    fun makePublic(paths: Set<String>, key: ByteArray) {
        val hit = expand(paths).map { it.path }.toSet()
        all.replaceAll { note ->
            if (note.path in hit && note.secret) {
                note.copy(body = Secret.decrypt(key, note.body) ?: note.body, secret = false)
            } else {
                note
            }
        }
    }

    // 搜索标题和正文。私密笔记传进来的是已解密的明文。
    fun matches(note: Note, query: String): Boolean {
        if (query.isBlank()) return true
        return note.name.contains(query, true) || note.body.contains(query, true)
    }
}
