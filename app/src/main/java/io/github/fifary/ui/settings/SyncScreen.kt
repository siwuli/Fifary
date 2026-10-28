package io.github.fifary.ui.settings

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import io.github.fifary.data.Dav
import io.github.fifary.data.SettingsStore
import io.github.fifary.ui.S
import io.github.fifary.ui.components.InputDialog
import io.github.fifary.ui.theme.Space

@Composable
fun SyncScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    var askUrl by remember { mutableStateOf(false) }
    var askUser by remember { mutableStateOf(false) }
    var askPass by remember { mutableStateOf(false) }

    SettingsPage(title = S("同步数据"), onBack = onBack) {
        SectionLabel("WebDAV")
        Group {
            RowItem(S("服务器地址"), SettingsStore.davUrl.ifBlank { S("未配置") }) { askUrl = true }
            Rule()
            RowItem(S("账号"), SettingsStore.davUser.ifBlank { S("未配置") }) { askUser = true }
            Rule()
            RowItem(S("密码"), if (SettingsStore.davPass.isEmpty()) S("未配置") else "••••••") { askPass = true }
            Rule()
            SwitchItem(S("自动同步"), SettingsStore.davAuto) { on ->
                SettingsStore.updateDav(
                    SettingsStore.davUrl, SettingsStore.davUser, SettingsStore.davPass, on,
                )
                if (on) Dav.auto(context)
            }
        }

        SectionLabel(S("同步"))
        Group {
            RowItem(S("立即上传")) {
                if (Dav.configured) Dav.start(context, Dav.Mode.UPLOAD) else warn(context)
            }
            Rule()
            RowItem(S("立即下载")) {
                if (Dav.configured) Dav.start(context, Dav.Mode.DOWNLOAD) else warn(context)
            }
        }

        val status = when {
            Dav.running -> S("正在同步…")
            else -> Dav.message
        }
        if (status != null) {
            Text(
                text = status,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = Space.xxs),
            )
        }
    }

    if (askUrl) {
        InputDialog(
            title = S("服务器地址"),
            placeholder = "https://dav.example.com/fifary/",
            confirmText = S("保存"),
            initial = SettingsStore.davUrl,
            onDismiss = { askUrl = false },
            onConfirm = { value ->
                askUrl = false
                SettingsStore.updateDav(
                    value.trim(), SettingsStore.davUser, SettingsStore.davPass, SettingsStore.davAuto,
                )
            },
        )
    }

    if (askUser) {
        InputDialog(
            title = S("账号"),
            placeholder = S("账号"),
            confirmText = S("保存"),
            initial = SettingsStore.davUser,
            onDismiss = { askUser = false },
            onConfirm = { value ->
                askUser = false
                SettingsStore.updateDav(
                    SettingsStore.davUrl, value.trim(), SettingsStore.davPass, SettingsStore.davAuto,
                )
            },
        )
    }

    if (askPass) {
        InputDialog(
            title = S("密码"),
            placeholder = S("密码"),
            confirmText = S("保存"),
            masked = true,
            initial = SettingsStore.davPass,
            onDismiss = { askPass = false },
            onConfirm = { value ->
                askPass = false
                SettingsStore.updateDav(
                    SettingsStore.davUrl, SettingsStore.davUser, value, SettingsStore.davAuto,
                )
            },
        )
    }
}

private fun warn(context: android.content.Context) {
    android.widget.Toast.makeText(context, io.github.fifary.ui.S("先填服务器地址和账号"), android.widget.Toast.LENGTH_SHORT).show()
}
