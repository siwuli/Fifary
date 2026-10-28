package io.github.fifary.ui.settings

import android.content.Intent
import android.net.Uri
import android.os.Environment
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import io.github.fifary.data.AppLang
import io.github.fifary.data.Dav
import io.github.fifary.data.NotesStore
import io.github.fifary.data.Palette
import io.github.fifary.data.Palettes
import io.github.fifary.data.PrivateVault
import io.github.fifary.data.SettingsStore
import io.github.fifary.data.Storage
import io.github.fifary.data.ThemeMode
import io.github.fifary.ui.S
import io.github.fifary.ui.applyLang
import io.github.fifary.ui.components.InputDialog
import io.github.fifary.ui.components.TextPill
import io.github.fifary.ui.components.springPressable
import io.github.fifary.ui.theme.Radius
import io.github.fifary.ui.theme.Size
import io.github.fifary.ui.theme.Space
import java.io.File

private val MODES = listOf(
    ThemeMode.SYSTEM to "跟随系统",
    ThemeMode.LIGHT to "浅色",
    ThemeMode.DARK to "深色",
)

private val LANGS = listOf(
    AppLang.SYSTEM to "跟随系统",
    AppLang.ZH to "简体中文",
    AppLang.EN to "English",
)

@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onOpenSync: () -> Unit,
    onOpenAbout: () -> Unit,
    onOpenDebug: () -> Unit,
) {
    val context = LocalContext.current

    var askSet by remember { mutableStateOf(false) }
    var askOld by remember { mutableStateOf(false) }
    var askNew by remember { mutableStateOf(false) }
    var oldPassword by remember { mutableStateOf("") }
    var pickStorage by remember { mutableStateOf(false) }
    var askPath by remember { mutableStateOf(false) }
    var askImport by remember { mutableStateOf(false) }
    var pendingImport by remember { mutableStateOf<Uri?>(null) }

    val toast: (String) -> Unit = { Toast.makeText(context, it, Toast.LENGTH_SHORT).show() }

    fun relocate(target: File) {
        pickStorage = false
        val ok = Storage.relocate(context, target)
        NotesStore.reload(context)
        toast(S(if (ok) "已切换存储位置" else "切换失败，文件没有动"))
    }

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/zip")
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        val count = runCatching {
            context.contentResolver.openOutputStream(uri)?.use { Storage.export(context, it) }
        }.getOrNull()
        toast(S(if (count == null) "导出失败" else "导出完成"))
    }

    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        pendingImport = uri
        askImport = true
    }

    fun doImport(uri: Uri) {
        val count = runCatching {
            context.contentResolver.openInputStream(uri)?.use { Storage.import(context, it) }
        }.getOrNull()
        NotesStore.reload(context)
        toast(S(if (count == null) "导入失败" else "导入完成"))
    }

    SettingsPage(title = S("设置"), onBack = onBack) {
        SectionLabel(S("外观"))
        Group {
            Column(Modifier.padding(Space.l), verticalArrangement = Arrangement.spacedBy(Space.s)) {
                Label(S("主题"))
                Row(horizontalArrangement = Arrangement.spacedBy(Space.xs)) {
                    MODES.forEach { (mode, text) ->
                        Pill(S(text), SettingsStore.themeMode == mode) { SettingsStore.updateThemeMode(mode) }
                    }
                }
                Spacer(Modifier.height(Space.xxs))
                Label(S("颜色"))
                // 六个色块平分一行，窄屏也不会有够不到的第七个
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Space.xs)) {
                    Palettes.forEach { palette ->
                        Swatch(
                            palette = palette,
                            selected = SettingsStore.palette.id == palette.id,
                            onClick = { SettingsStore.updatePalette(palette) },
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
        }

        SectionLabel(S("语言"))
        Group {
            Row(Modifier.padding(Space.l), horizontalArrangement = Arrangement.spacedBy(Space.xs)) {
                LANGS.forEach { (lang, text) ->
                    Pill(S(text), SettingsStore.language == lang) {
                        SettingsStore.updateLanguage(lang)
                        applyLang(lang)
                    }
                }
            }
        }

        SectionLabel(S("存储"))
        Group {
            RowItem(S("存储位置"), S(Storage.label(context))) {
                Toast.makeText(context, Storage.root(context).absolutePath, Toast.LENGTH_LONG).show()
            }
            Rule()
            RowItem(S("设置新路径")) { pickStorage = true }
        }

        SectionLabel(S("数据"))
        Group {
            RowItem(S("导出备份")) { exportLauncher.launch("fifary-${System.currentTimeMillis()}.zip") }
            Rule()
            RowItem(S("导入备份")) { importLauncher.launch(arrayOf("application/zip", "application/octet-stream")) }
        }

        SectionLabel(S("私密"))
        Group {
            RowItem(S("私密密码"), S(if (PrivateVault.enabled) "已设置" else "未设置")) {
                if (PrivateVault.enabled) askOld = true else askSet = true
            }
            if (PrivateVault.unlocked) {
                Rule()
                RowItem(S("立即锁定")) {
                    PrivateVault.lock()
                    toast(S("已锁定"))
                }
            }
        }

        SectionLabel(S("更多"))
        Group {
            RowItem(S("同步数据"), S(if (Dav.configured) "已配置" else "未配置")) { onOpenSync() }
            Rule()
            RowItem(S("关于"), appVersion()) { onOpenAbout() }
            Rule()
            RowItem(S("调试")) { onOpenDebug() }
        }
    }

    if (pickStorage) {
        AlertDialog(
            onDismissRequest = { pickStorage = false },
            shape = RoundedCornerShape(Radius.sheet),
            containerColor = MaterialTheme.colorScheme.surface,
            titleContentColor = MaterialTheme.colorScheme.onSurface,
            title = { Text(S("存储位置"), style = MaterialTheme.typography.titleMedium) },
            text = {
                Column(Modifier.fillMaxWidth()) {
                    Text(
                        text = Storage.root(context).absolutePath,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(Space.s))
                    StorageOption(S("内部存储"), Storage.internalRoot(context).absolutePath) {
                        relocate(Storage.internalRoot(context))
                    }
                    StorageOption(S("应用外部目录"), Storage.externalRoot(context).absolutePath) {
                        relocate(Storage.externalRoot(context))
                    }
                    StorageOption(S("自定义目录"), null) {
                        pickStorage = false
                        askPath = true
                    }
                }
            },
            confirmButton = { TextPill(S("取消")) { pickStorage = false } },
        )
    }

    if (askPath) {
        InputDialog(
            title = S("输入目录路径"),
            placeholder = "/sdcard/Documents/Fifary",
            confirmText = S("保存"),
            onDismiss = { askPath = false },
            onConfirm = { path ->
                askPath = false
                val target = File(path.trim())
                // 自定义到 /sdcard 下要「所有文件访问权限」，没有就先带用户去开
                if (!Storage.hasAllFiles() && !target.canWrite()) {
                    runCatching {
                        context.startActivity(
                            Intent(
                                Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                                Uri.parse("package:${context.packageName}"),
                            )
                        )
                    }.onFailure {
                        context.startActivity(Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION))
                    }
                    toast(S("所有文件访问权限"))
                } else {
                    relocate(target)
                }
            },
        )
    }

    if (askImport && pendingImport != null) {
        AlertDialog(
            onDismissRequest = { askImport = false },
            shape = RoundedCornerShape(Radius.sheet),
            containerColor = MaterialTheme.colorScheme.surface,
            titleContentColor = MaterialTheme.colorScheme.onSurface,
            title = { Text(S("导入备份"), style = MaterialTheme.typography.titleMedium) },
            text = { Text(S("导入会覆盖同名笔记，继续？"), style = MaterialTheme.typography.bodyMedium) },
            confirmButton = {
                TextPill(S("确定")) {
                    askImport = false
                    pendingImport?.let(::doImport)
                    pendingImport = null
                }
            },
            dismissButton = { TextPill(S("取消")) { askImport = false } },
        )
    }

    if (askSet) {
        InputDialog(
            title = S("设置私密密码"),
            placeholder = S("至少 4 位"),
            confirmText = S("保存"),
            masked = true,
            note = S("密码只存在本机，忘了就解不开。"),
            onDismiss = { askSet = false },
            onConfirm = { value ->
                if (PrivateVault.setPassword(value)) {
                    askSet = false
                    toast(S("私密密码已设置"))
                }
            },
        )
    }

    if (askOld) {
        InputDialog(
            title = S("输入当前密码"),
            placeholder = S("密码"),
            confirmText = S("下一步"),
            masked = true,
            onDismiss = { askOld = false },
            onConfirm = { value ->
                if (PrivateVault.unlock(value)) {
                    oldPassword = value
                    askOld = false
                    askNew = true
                } else {
                    toast(S("密码不对"))
                }
            },
        )
    }

    if (askNew) {
        InputDialog(
            title = S("设置新密码"),
            placeholder = S("至少 4 位"),
            confirmText = S("保存"),
            masked = true,
            onDismiss = { askNew = false },
            onConfirm = { value ->
                askNew = false
                toast(S(if (PrivateVault.changePassword(oldPassword, value)) "密码已修改" else "修改失败，密码没有变"))
                oldPassword = ""
            },
        )
    }
}

@Composable
private fun Label(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun Pill(text: String, selected: Boolean, onClick: () -> Unit) {
    // 卡片是白底，所以选中的胶囊用强调色填充，而不是"白底+阴影"
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
        contentColor = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.height(Size.chipHeight),
    ) {
        Row(Modifier.padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(text, style = MaterialTheme.typography.labelMedium)
        }
    }
}

@Composable
private fun StorageOption(title: String, path: String?, onClick: () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .springPressable(onClick = onClick)
            .padding(vertical = Space.s),
    ) {
        Text(title, style = MaterialTheme.typography.bodyLarge)
        if (path != null) {
            Text(
                text = path,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.outline,
            )
        }
    }
}

@Composable
private fun Swatch(
    palette: Palette,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = palette.lightAccent,
        border = BorderStroke(
            width = if (selected) 3.dp else 1.dp,
            color = if (selected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.outlineVariant,
        ),
        modifier = modifier.aspectRatio(1f),
    ) {
        Box(Modifier.background(palette.lightAccent), contentAlignment = Alignment.Center) {
            if (selected) {
                Box(Modifier.size(8.dp).background(MaterialTheme.colorScheme.surface, CircleShape))
            }
        }
    }
}
