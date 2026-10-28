package io.github.fifary.ui.editor

import android.annotation.SuppressLint
import android.graphics.Color
import android.os.Build
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import org.json.JSONObject
import java.io.File

// 正文照参考 app 走 WebView 里的富文本（参考本体就是 WebView + 富文本编辑器），
// Markdown 只作为存盘格式：进来 md→html，点完成 html→md
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun RichBody(
    markdown: String,
    dark: Boolean,
    modifier: Modifier = Modifier,
    onReady: (WebView) -> Unit,
) {
    val context = LocalContext.current
    val html = remember { context.assets.open("editor.html").bufferedReader().use { it.readText() } }
    val base = remember { "file://" + File(context.filesDir, "vault").absolutePath + "/" }
    val theme = if (dark) "dark" else "light"
    var page by remember { mutableStateOf<WebView?>(null) }

    // 深浅色由我们的设置决定，不让 WebView 自己再反色一遍，否则正文会黑底黑字
    LaunchedEffect(page, theme) {
        page?.evaluateJavascript("document.documentElement.dataset.theme='$theme'", null)
    }

    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            WebView(ctx).apply {
                setBackgroundColor(Color.TRANSPARENT)
                settings.javaScriptEnabled = true
                settings.allowFileAccess = true
                settings.allowFileAccessFromFileURLs = true
                settings.allowUniversalAccessFromFileURLs = true
                settings.loadWithOverviewMode = false
                settings.useWideViewPort = false
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    settings.isAlgorithmicDarkeningAllowed = false
                } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    @Suppress("DEPRECATION")
                    settings.forceDark = WebSettings.FORCE_DARK_OFF
                }
                webViewClient = object : WebViewClient() {
                    override fun onPageFinished(view: WebView, url: String?) {
                        page = view
                        view.evaluateJavascript("document.documentElement.dataset.theme='$theme'", null)
                        view.evaluateJavascript("setMd(${JSONObject.quote(markdown)})", null)
                        onReady(view)
                    }
                }
                loadDataWithBaseURL(base, html, "text/html", "utf-8", null)
            }
        },
        // 一页一个 WebView，不销毁的话开几十条笔记内存就堆起来了
        onRelease = { it.destroy() },
    )
}
