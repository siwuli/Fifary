package io.github.fifary.data

import android.content.Context
import java.io.File

// vault 目录里的 .md 就是全部数据：目录 = 分组，文件头的 front matter 记元数据。
// 整个目录拷走就是一次完整备份，放回来就能用，不需要数据库。
object Vault {

    private const val DIR = "vault"
    private const val FENCE = "---"
    private const val CREATED = "created"
    private const val UPDATED = "updated"
    private const val PINNED = "pinned"
    private const val DELETED = "deleted"
    private const val SECRET = "secret"
    private val KEYS = setOf(CREATED, UPDATED, PINNED, DELETED, SECRET)

    private var root: File? = null

    // null = vault 目录本来不存在，也就是第一次装
    fun open(context: Context): List<Note>? {
        val dir = File(context.filesDir, DIR)
        root = dir
        if (!dir.exists()) {
            dir.mkdirs()
            return null
        }
        return dir.walkTopDown()
            .filter { it.isFile && it.extension == "md" }
            .map { parse(it.relativeTo(dir).invariantSeparatorsPath, it.readText(), it.lastModified()) }
            .toList()
    }

    fun save(note: Note) {
        val file = fileFor(note.path) ?: return
        file.parentFile?.mkdirs()
        file.writeText(encode(note))
    }

    fun remove(path: String) {
        fileFor(path)?.delete()
    }

    fun move(from: String, to: String) {
        val dst = fileFor(to) ?: return
        dst.parentFile?.mkdirs()
        fileFor(from)?.renameTo(dst)
    }

    fun makeFolder(name: String) {
        root?.let { File(it, name).mkdirs() }
    }

    fun renameFolder(from: String, to: String) {
        val dir = root ?: return
        File(dir, from).renameTo(File(dir, to))
    }

    // 盘上真空着的目录：用户建了分组但还没写笔记。附件目录不是分组
    fun emptyFolderNames(): List<String> = root?.listFiles()
        ?.filter {
            it.isDirectory && !it.name.startsWith(".") && it.name != Attach.DIR &&
                it.listFiles()?.isEmpty() == true
        }
        ?.map { it.name }
        .orEmpty()

    private fun fileFor(path: String): File? {
        val dir = root ?: return null
        if (path.isEmpty() || path.contains("..")) return null
        return File(dir, path)
    }

    private fun encode(note: Note): String = buildString {
        append(FENCE).append('\n')
        append(CREATED).append(": ").append(note.createdAt).append('\n')
        append(UPDATED).append(": ").append(note.updatedAt).append('\n')
        if (note.pinned) append(PINNED).append(": true\n")
        if (note.secret) append(SECRET).append(": true\n")
        if (note.deletedAt > 0L) append(DELETED).append(": ").append(note.deletedAt).append('\n')
        append(FENCE).append('\n')
        append(note.body)
    }

    private fun parse(path: String, text: String, modified: Long): Note {
        val head = frontMatter(text)
        val values = head?.first.orEmpty()
            .associate { it.substringBefore(':').trim() to it.substringAfter(':').trim() }
        return Note(
            path = path,
            body = if (head == null) text else text.substring(head.second),
            createdAt = values[CREATED]?.toLongOrNull() ?: modified,
            updatedAt = values[UPDATED]?.toLongOrNull() ?: modified,
            pinned = values[PINNED] == "true",
            secret = values[SECRET] == "true",
            deletedAt = values[DELETED]?.toLongOrNull() ?: 0L,
        )
    }

    // 只认我们写的那几个键：出现别的行就整篇当正文，
    // 免得把笔记开头自己写的 --- 分割线当成文件头吃掉
    private fun frontMatter(text: String): Pair<List<String>, Int>? {
        if (!text.startsWith("$FENCE\n")) return null
        val close = text.indexOf("\n$FENCE\n", FENCE.length)
        if (close < 0) return null
        val lines = text.substring(FENCE.length + 1, close).lines()
        if (lines.any { it.isNotEmpty() && it.substringBefore(':').trim() !in KEYS }) return null
        return lines to close + FENCE.length + 2
    }

    // 自检：写进去的必须原样读回来。正文自带 --- 和别的 App 的 md 是最容易踩的两处
    fun selfCheck(): Boolean {
        val note = Note(
            path = "检查/回环.md",
            body = "---\n不是文件头\n---\n正文",
            createdAt = 1L,
            updatedAt = 2L,
            pinned = true,
            secret = true,
            deletedAt = 3L,
        )
        if (parse(note.path, encode(note), 0L) != note) return false
        val foreign = "---\ntitle: hi\n---\n正文"
        return parse("别人的.md", foreign, 0L).body == foreign
    }
}
