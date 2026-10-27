package io.github.fifary.ui.editor

import android.annotation.SuppressLint
import android.graphics.Color
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
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
    modifier: Modifier = Modifier,
    onReady: (WebView) -> Unit,
) {
    val context = LocalContext.current
    val html = remember { context.assets.open("editor.html").bufferedReader().use { it.readText() } }
    val base = remember { "file://" + File(context.filesDir, "vault").absolutePath + "/" }

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
                webViewClient = object : WebViewClient() {
                    override fun onPageFinished(view: WebView, url: String?) {
                        view.evaluateJavascript("setMd(${JSONObject.quote(markdown)})", null)
                        onReady(view)
                    }
                }
                loadDataWithBaseURL(base, html, "text/html", "utf-8", null)
            }
        },
    )
}
