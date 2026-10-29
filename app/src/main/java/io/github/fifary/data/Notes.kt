package io.github.fifary.data

import android.content.Context
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

object NotesStore {

    // 「默认」是虚拟分组：没有同名目录，就是 vault 根上的那些笔记
    const val DEFAULT = "默认"

    // 两个虚拟分组：不是真目录，所以不能建同名分组
    const val TRASH = "最近删除"
    const val PRIVATE = "私密"

    const val TRASH_KEEP_MS = 30L * 24 * 60 * 60 * 1000

    // 参考 app 里空标题存成「无标题」
    const val UNTITLED = "无标题"

    private val now = System.currentTimeMillis()

    // 第一次装、vault 还不存在时灌一篇介绍；之后盘上有什么就显示什么
    private val seed: List<Note> = listOf(
        Note(
            path = "欢迎使用 Fifary.md",
            body = "Fifary 是一个纯本地 Markdown 笔记 App。\n\n" +
                "- 笔记就是 .md 文件，目录就是分组，不存数据库\n" +
                "- 不配置任何服务器也能完整使用，没有账号体系\n" +
                "- 同步走标准 WebDAV，随时可换，不锁定服务商\n" +
                "- 还有私密笔记、导入导出、深浅色主题\n\n" +
                "「设置」里可以改存储位置、语言、配置 WebDAV。这篇笔记随时可以删掉。",
            createdAt = now - 60_000,
            updatedAt = now - 60_000,
        ),
    )

    val all: SnapshotStateList<Note> = mutableStateListOf()

    private var loaded = false

    fun load(context: Context) {
        if (loaded) return
        loaded = true
        val disk = Vault.open(context)
        // null = 第一次装；空目录说明用户真把笔记删光了，不能再灌示例
        if (disk == null) seed.forEach(Vault::save)
        all.addAll(disk ?: seed)
        emptyFolders.addAll(Vault.emptyFolderNames())
        purgeExpiredTrash()
    }

    // 换过存储位置、导入过备份、同步完都得重读一遍盘
    fun reload(context: Context) {
        loaded = false
        all.clear()
        emptyFolders.clear()
        load(context)
    }

    fun byPath(path: String): Note? = all.firstOrNull { it.path == path }

    // 编辑页只在点「完成」时走这里。标题就是文件名，改标题等于给文件改名；
    // path 传空的表示新建。返回最终路径（改名后可能变了）。
    // 没填标题（无标题 / 无标题 N）的笔记：编辑器标题栏留空，列表拿正文第一行顶上
    fun isUntitled(name: String): Boolean =
        name.isBlank() || name == UNTITLED || name.startsWith("$UNTITLED ")

    // 第一行可能是一整句话：落盘的名字截到 40 字，别让文件名超长写不进去。
    // 重名不管——两条笔记可以同名，后面用「 2」错开
    private fun fileName(title: String): String {
        var s = title.trim().replace("/", " ").take(40)
        if (s.lastOrNull()?.isHighSurrogate() == true) s = s.dropLast(1)
        return s.trim()
    }

    fun save(path: String, folder: String, title: String, body: String): String {
        val time = System.currentTimeMillis()
        val old = path.substringAfterLast('/').removeSuffix(".md")
        // 标题空着：本来就是无标题的保持不变（否则每存一次都改名），有标题的清成无标题
        // 名字只用来落盘：第一行可能是一整句，截断到 40 字，不然文件名超长会写失败
        val name = fileName(title).ifEmpty { if (isUntitled(old)) old else UNTITLED }
        val key = PrivateVault.key
        val existing = byPath(path)

        if (existing == null) {
            val secret = folder == PRIVATE && key != null
            val target = if (folder.isEmpty() || folder == PRIVATE || folder == TRASH) "" else folder
            val fresh = uniquePath(target, name, null)
            val note = Note(
                path = fresh,
                body = if (secret && key != null) Secret.encrypt(key, body) else body,
                createdAt = time,
                updatedAt = time,
                secret = secret,
            )
            all.add(0, note)
            Vault.save(note)
            return fresh
        }

        // 私密笔记这会儿可能是锁着的（切过后台、密钥已经清了）：宁可这次改动不写进文件，
        // 也不能把明文塞进一个标了 secret 的文件——那篇笔记就再也解不开了
        val stored = when {
            !existing.secret -> body
            key == null -> existing.body
            else -> Secret.encrypt(key, body)
        }
        val fresh = uniquePath(existing.folder, name, existing.path)
        // 只是打开看了一眼、什么都没改：不写盘也不动最后修改时间，
        // 不然列表上的时间一进出笔记就变成「刚刚」。
        // 结尾的换行不算改（读回来带不带它取决于文件怎么写的）；
        // 私密笔记每次都重新加密、密文必然不同，只能拿明文比
        val untouched = when {
            !existing.secret -> stored.trimEnd('\n') == existing.body.trimEnd('\n')
            key == null -> true
            else -> Secret.decrypt(key, existing.body)?.trimEnd('\n') == body.trimEnd('\n')
        }
        if (fresh == existing.path && untouched) return fresh
        if (fresh != existing.path) Vault.move(existing.path, fresh)
        val at = all.indexOfFirst { it.path == existing.path }
        if (at >= 0) {
            all[at] = existing.copy(path = fresh, body = stored, updatedAt = time)
            Vault.save(all[at])
        }
        PrivateVault.forget(fresh)
        return fresh
    }

    private fun uniquePath(folder: String, name: String, self: String?): String {
        val prefix = if (folder.isEmpty()) "" else "$folder/"
        var path = "$prefix$name.md"
        var index = 2
        while (all.any { it.path == path && it.path != self }) {
            path = "$prefix$name $index.md"
            index++
        }
        return path
    }

    // 列表用哪一屏：私密只出私密，最近删除只出删除的
    // 直接在 vault 根上的笔记也算「默认」分组：App 里没有比它更合适的地方，不然只有「全部」看得到
    fun notesFor(folder: String): List<Note> = when (folder) {
        TRASH -> all.filter { it.deletedAt > 0L }
        PRIVATE -> all.filter { it.secret && it.deletedAt == 0L }
        "" -> all.filter { it.deletedAt == 0L && !it.secret }
        else -> all.filter {
            (it.folder == folder || (folder == DEFAULT && it.folder.isEmpty())) &&
                it.deletedAt == 0L && !it.secret
        }
    }

    fun countIn(folder: String): Int = notesFor(folder).size

    // 空分组：真实文件系统里目录可以没有文件，占位阶段单独记一份
    private val emptyFolders = mutableStateListOf<String>()

    // 分组永远是平铺的一级目录。顺序按用户在分组页拖出来的，没排过的按名字放在后面
    fun folders(): List<FolderEntry> {
        val names = LinkedHashSet<String>()
        names.add(DEFAULT)
        for (note in all) {
            if (note.deletedAt > 0L || note.secret || note.folder.isEmpty()) continue
            names.add(note.folder)
        }
        names.addAll(emptyFolders)
        val order = SettingsStore.folderOrder
        fun rank(name: String): Int = order.indexOf(name).let { if (it < 0) Int.MAX_VALUE else it }
        return names.sortedWith(compareBy({ rank(it) }, { it.lowercase() }))
            .map { FolderEntry(path = it, name = it, noteCount = countIn(it)) }
    }

    fun createFolder(name: String): Boolean {
        val clean = name.trim()
        if (clean.isEmpty() || clean.contains('/')) return false
        if (clean == TRASH || clean == PRIVATE || clean == DEFAULT) return false
        if (folders().any { it.path == clean }) return false
        emptyFolders.add(clean)
        Vault.makeFolder(clean)
        return true
    }

    fun renameFolder(from: String, to: String): Boolean {
        val clean = to.trim()
        if (clean.isEmpty() || clean.contains('/') || clean == from) return false
        if (clean == TRASH || clean == PRIVATE || clean == DEFAULT) return false
        if (folders().any { it.path == clean }) return false
        all.replaceAll { note ->
            if (note.folder == from) note.copy(path = "$clean/${note.name}.md") else note
        }
        if (emptyFolders.remove(from)) emptyFolders.add(clean)
        Vault.renameFolder(from, clean)
        return true
    }

    fun deleteFolder(path: String) {
        val hit = all.filter { it.folder == path && it.deletedAt == 0L }.map { it.path }.toSet()
        delete(hit)
    }

    private fun expand(paths: Set<String>): List<Note> =
        all.filter { note -> paths.any { it == note.path || note.path.startsWith("$it/") } }

    // 删除 = 进最近删除，30 天后清掉；置顶照旧。文件留在原目录，靠文件头的 deleted 认出来
    fun delete(paths: Set<String>) {
        val time = System.currentTimeMillis()
        val hit = expand(paths).map { it.path }.toSet()
        all.replaceAll { note ->
            if (note.path in hit) note.copy(deletedAt = time, pinned = false) else note
        }
        saveAll(hit)
    }

    fun restore(paths: Set<String>) {
        val hit = expand(paths).map { it.path }.toSet()
        all.replaceAll { note -> if (note.path in hit) note.copy(deletedAt = 0L) else note }
        saveAll(hit)
    }

    fun purge(paths: Set<String>) {
        val hit = expand(paths).map { it.path }.toSet()
        all.removeAll { it.path in hit }
        hit.forEach(Vault::remove)
    }

    fun purgeExpiredTrash() {
        val deadline = System.currentTimeMillis() - TRASH_KEEP_MS
        val dead = all.filter { it.deletedAt in 1L..deadline }.map { it.path }
        all.removeAll { it.path in dead }
        dead.forEach(Vault::remove)
    }

    // 移动 = 改路径（分类即目录），盘上就是重命名文件；移到「默认」= 挪回 vault 根
    fun move(paths: Set<String>, folder: String) {
        val target = if (folder == DEFAULT) "" else folder
        val hit = expand(paths).filter { it.deletedAt == 0L }
        for (note in hit) {
            val path = uniquePath(target, note.name, note.path)
            val at = all.indexOfFirst { it.path == note.path }
            if (at >= 0) {
                Vault.move(note.path, path)
                all[at] = all[at].copy(path = path)
            }
        }
    }

    fun togglePin(paths: Set<String>) {
        val hit = expand(paths).map { it.path }.toSet()
        all.replaceAll { note -> if (note.path in hit) note.copy(pinned = !note.pinned) else note }
        saveAll(hit)
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
        saveAll(hit)
    }

    fun makePublic(paths: Set<String>, key: ByteArray) {
        val hit = expand(paths).map { it.path }.toSet()
        all.replaceAll { note ->
            if (note.path in hit && note.secret) {
                // 解不开就让它保持私密：把密文当正文留下来等于毁掉这篇笔记
                val plain = Secret.decrypt(key, note.body)
                if (plain == null) note else note.copy(body = plain, secret = false)
            } else {
                note
            }
        }
        saveAll(hit)
    }

    private fun saveAll(paths: Set<String>) {
        paths.forEach { path -> byPath(path)?.let(Vault::save) }
    }

    // 搜索标题和正文。私密笔记传进来的是已解密的明文。
    fun matches(note: Note, query: String): Boolean {
        if (query.isBlank()) return true
        return note.name.contains(query, true) || note.body.contains(query, true)
    }
}
