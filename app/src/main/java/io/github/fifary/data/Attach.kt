package io.github.fifary.data

import android.content.Context
import android.net.Uri
import java.io.File

// 图片和录音跟笔记放在同一个 vault 里，目录拷走时附件跟着走
object Attach {

    const val DIR = "attachments"

    // 从 Obsidian 之类搬过来的库本来就用 assets/ 存图片，跟着它走，不然同一个库里图片分两处
    fun dirName(context: Context): String =
        if (File(Storage.root(context), ASSETS).isDirectory) ASSETS else DIR

    private fun dir(context: Context): File =
        File(Storage.root(context), dirName(context)).apply { mkdirs() }

    fun file(context: Context, relative: String): File = File(Storage.root(context), relative)

    fun copy(context: Context, uri: Uri): String? = runCatching {
        val type = context.contentResolver.getType(uri)?.substringAfterLast('/') ?: "jpg"
        val ext = if (type == "jpeg") "jpg" else type
        val target = File(dir(context), "img_${System.currentTimeMillis()}.$ext")
        context.contentResolver.openInputStream(uri)?.use { input ->
            target.outputStream().use { output -> input.copyTo(output) }
        } ?: return null
        "${dirName(context)}/${target.name}"
    }.getOrNull()

    fun newAudio(context: Context): File = File(dir(context), "rec_${System.currentTimeMillis()}.m4a")

    fun newImage(context: Context): File = File(dir(context), "img_${System.currentTimeMillis()}.jpg")

    private const val ASSETS = "assets"
}
