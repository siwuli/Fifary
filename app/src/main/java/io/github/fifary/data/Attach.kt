package io.github.fifary.data

import android.content.Context
import android.net.Uri
import java.io.File

// 图片和录音跟笔记放在同一个 vault 里，目录拷走时附件跟着走
object Attach {

    const val DIR = "attachments"

    private fun dir(context: Context): File =
        File(File(context.filesDir, "vault"), DIR).apply { mkdirs() }

    fun file(context: Context, relative: String): File = File(File(context.filesDir, "vault"), relative)

    fun copy(context: Context, uri: Uri): String? = runCatching {
        val type = context.contentResolver.getType(uri)?.substringAfterLast('/') ?: "jpg"
        val ext = if (type == "jpeg") "jpg" else type
        val target = File(dir(context), "img_${System.currentTimeMillis()}.$ext")
        context.contentResolver.openInputStream(uri)?.use { input ->
            target.outputStream().use { output -> input.copyTo(output) }
        } ?: return null
        "$DIR/${target.name}"
    }.getOrNull()

    fun newAudio(context: Context): File = File(dir(context), "rec_${System.currentTimeMillis()}.m4a")
}
