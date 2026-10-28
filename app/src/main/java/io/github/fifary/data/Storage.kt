package io.github.fifary.data

import android.content.Context
import android.os.Build
import android.os.Environment
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

// vault 就是一个普通目录：换位置 = 把整棵树拷过去再改一个路径，不是数据库迁移
object Storage {

    const val INTERNAL = ""

    fun internalRoot(context: Context): File = File(context.filesDir, "vault")

    fun externalRoot(context: Context): File =
        File(context.getExternalFilesDir(null) ?: context.filesDir, "vault")

    fun root(context: Context): File =
        SettingsStore.vaultPath.takeIf { it.isNotEmpty() }?.let(::File) ?: internalRoot(context)

    // 自定义到 /sdcard 之类的地方要「所有文件访问权限」；应用自己的目录不用
    fun hasAllFiles(): Boolean =
        Build.VERSION.SDK_INT < 30 || Environment.isExternalStorageManager()

    fun label(context: Context): String = when (root(context).absolutePath) {
        internalRoot(context).absolutePath -> "内部存储"
        externalRoot(context).absolutePath -> "应用外部目录"
        else -> "自定义目录"
    }

    // 拷完数一遍文件数，对得上才切路径；旧目录留着（删数据的事不替用户做）
    fun relocate(context: Context, target: File): Boolean {
        val from = root(context)
        if (from.absolutePath == target.absolutePath) return true
        return runCatching {
            target.mkdirs()
            val files = from.walkTopDown().filter { it.isFile }.toList()
            for (file in files) {
                val dst = File(target, file.relativeTo(from).invariantSeparatorsPath)
                dst.parentFile?.mkdirs()
                file.copyTo(dst, overwrite = true)
            }
            if (target.walkTopDown().count { it.isFile } < files.size) return false
            SettingsStore.updateVaultPath(
                if (target.absolutePath == internalRoot(context).absolutePath) INTERNAL else target.absolutePath
            )
            true
        }.getOrDefault(false)
    }

    fun notes(context: Context): Int = root(context).walkTopDown().count { it.isFile && it.extension == "md" }

    fun attachments(context: Context): Int =
        File(root(context), Attach.DIR).listFiles()?.count { it.isFile } ?: 0

    fun bytes(context: Context): Long =
        root(context).walkTopDown().filter { it.isFile }.sumOf { it.length() }

    // 导出：整棵树打成 zip，附件和空分组都在里面
    fun export(context: Context, out: OutputStream): Int {
        val root = root(context)
        var count = 0
        ZipOutputStream(out.buffered()).use { zip ->
            root.walkTopDown().forEach { file ->
                val rel = file.relativeTo(root).invariantSeparatorsPath
                if (rel.isEmpty()) return@forEach
                if (file.isDirectory) {
                    zip.putNextEntry(ZipEntry("$rel/"))
                    zip.closeEntry()
                } else if (file.isFile) {
                    zip.putNextEntry(ZipEntry(rel))
                    file.inputStream().use { it.copyTo(zip) }
                    zip.closeEntry()
                    count++
                }
            }
        }
        return count
    }

    // 导入：同名覆盖，没删的旧文件留着。空目录补回来
    fun import(context: Context, input: InputStream): Int {
        val root = root(context)
        var count = 0
        ZipInputStream(input.buffered()).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                val name = entry.name
                // 只挡跳出去的路径
                if (name.startsWith("/") || name.split('/').any { it == ".." }) {
                    zip.closeEntry()
                    continue
                }
                val target = File(root, name)
                if (entry.isDirectory) {
                    target.mkdirs()
                } else {
                    target.parentFile?.mkdirs()
                    target.outputStream().use { zip.copyTo(it) }
                    count++
                }
                zip.closeEntry()
            }
        }
        return count
    }
}
