package io.github.fifary.ui.settings

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import io.github.fifary.data.Update
import io.github.fifary.ui.S
import io.github.fifary.ui.components.TextPill
import io.github.fifary.ui.theme.Radius

private const val REPO = "https://github.com/siwuli/Fifary"

@Composable
fun AboutScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val toast: (String) -> Unit = { Toast.makeText(context, it, Toast.LENGTH_SHORT).show() }
    val browse: (String) -> Unit = { url ->
        runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
            .onFailure { toast(S("没有能打开网页的应用")) }
    }
    var askDownload by remember { mutableStateOf(false) }

    SettingsPage(title = S("关于"), onBack = onBack) {
        Group {
            RowItem(S("版本"), appVersion(), arrow = false)
            Rule()
            RowItem(S("项目地址"), "siwuli/Fifary") { browse(REPO) }
            Rule()
            RowItem(S("开源协议"), "MIT") { browse("$REPO/blob/main/LICENSE") }
            Rule()
            RowItem(
                title = S("检查更新"),
                value = when {
                    Update.busy -> S("正在同步…")
                    else -> Update.message
                },
                arrow = false,
            ) {
                Update.check(versionName(context))
                askDownload = true
            }
        }
    }

    val url = Update.url
    if (askDownload && url != null) {
        AlertDialog(
            onDismissRequest = { askDownload = false },
            shape = RoundedCornerShape(Radius.sheet),
            containerColor = MaterialTheme.colorScheme.surface,
            titleContentColor = MaterialTheme.colorScheme.onSurface,
            title = { Text(Update.message ?: "", style = MaterialTheme.typography.titleMedium) },
            text = {
                Text(
                    text = versionName(context) + " → " + Update.message.orEmpty().removePrefix("发现新版本 "),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.fillMaxWidth(),
                )
            },
            confirmButton = {
                TextPill(S("去下载")) {
                    askDownload = false
                    browse(url)
                }
            },
            dismissButton = { TextPill(S("取消")) { askDownload = false } },
        )
    }
}
