package io.github.fifary.ui.settings

import android.os.Build
import android.widget.Toast
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import io.github.fifary.data.NotesStore
import io.github.fifary.data.SettingsStore
import io.github.fifary.data.Storage
import io.github.fifary.data.Vault
import io.github.fifary.ui.S
import io.github.fifary.ui.components.ConfirmDialog
import io.github.fifary.ui.theme.Space

// 排查问题用：路径、数量、当前配置，加上几个能立刻验证的动作
@Composable
fun DebugScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    var askEmpty by remember { mutableStateOf(false) }
    val toast: (String) -> Unit = { Toast.makeText(context, it, Toast.LENGTH_SHORT).show() }

    val notes = Storage.notes(context)
    val attachments = Storage.attachments(context)
    val bytes = Storage.bytes(context)

    SettingsPage(title = S("调试"), onBack = onBack) {
        SectionLabel(S("存储"))
        Group {
            RowItem(S("存储位置"), S(Storage.label(context)), arrow = false)
            Rule()
            RowItem(S("笔记数"), "$notes", arrow = false)
            Rule()
            RowItem(S("附件数"), "$attachments", arrow = false)
            Rule()
            RowItem(S("占用空间"), size(bytes), arrow = false)
            Rule()
            RowItem(S("运行环境"), "Android ${Build.VERSION.RELEASE} · API ${Build.VERSION.SDK_INT}", arrow = false)
        }
        Text(
            text = Storage.root(context).absolutePath,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.outline,
            modifier = Modifier.padding(start = Space.xxs),
        )

        SectionLabel(S("操作"))
        Group {
            RowItem(S("重新加载笔记")) {
                NotesStore.reload(context)
                toast(S("已重新加载"))
            }
            Rule()
            RowItem(S("存储自检")) {
                toast(S(if (Vault.selfCheck()) "自检通过" else "自检失败"))
            }
            Rule()
            RowItem(S("清空回收站")) { askEmpty = true }
            Rule()
            RowItem(S("复制诊断信息")) {
                clipboard.setText(AnnotatedString(diagnostics(context)))
                toast(S("已复制"))
            }
        }
    }

    if (askEmpty) {
        ConfirmDialog(
            title = S("清空回收站"),
            text = S("彻底删除"),
            confirmText = S("删除"),
            onDismiss = { askEmpty = false },
            onConfirm = {
                askEmpty = false
                val paths = NotesStore.notesFor(NotesStore.TRASH).map { it.path }.toSet()
                NotesStore.purge(paths)
                toast(S("已清空"))
            },
        )
    }
}

private fun diagnostics(context: android.content.Context): String = buildString {
    appendLine("Fifary ${versionName(context)}")
    appendLine("storage=${Storage.root(context).absolutePath}")
    appendLine("notes=${Storage.notes(context)} attachments=${Storage.attachments(context)}")
    appendLine("theme=${SettingsStore.themeMode} palette=${SettingsStore.palette.id}")
    appendLine("lang=${SettingsStore.language} view=${SettingsStore.viewMode} sort=${SettingsStore.sortMode}")
    appendLine("dav=${SettingsStore.davUrl} auto=${SettingsStore.davAuto}")
    appendLine("android=${Build.VERSION.RELEASE} api=${Build.VERSION.SDK_INT}")
}

private fun size(bytes: Long): String = when {
    bytes >= 1024 * 1024 -> "%.1f MB".format(bytes / 1024f / 1024f)
    bytes >= 1024 -> "${bytes / 1024} KB"
    else -> "$bytes B"
}
