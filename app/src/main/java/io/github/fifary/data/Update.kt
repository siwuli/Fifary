package io.github.fifary.data

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.net.HttpURLConnection
import java.net.URL

// 检查更新就走仓库的 Releases：有新版就把页面地址给出去，App 自己不下 APK
object Update {

    private const val API = "https://api.github.com/repos/siwuli/Fifary/releases/latest"

    var busy by mutableStateOf(false)
        private set
    var message by mutableStateOf<String?>(null)
        private set
    // 有新版时才有：打开它就能下
    var url by mutableStateOf<String?>(null)
        private set

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun check(current: String) {
        if (busy) return
        busy = true
        message = null
        url = null
        scope.launch {
            val outcome = runCatching { fetch() }
            outcome.onSuccess { (tag, page) ->
                if (newer(tag, current)) {
                    message = "发现新版本 $tag"
                    url = page
                } else {
                    message = "已是最新版本"
                }
            }.onFailure {
                message = if (it.message == "404") "还没有发布版本" else "检查失败，稍后再试"
            }
            busy = false
        }
    }

    private fun fetch(): Pair<String, String> {
        val conn = URL(API).openConnection() as HttpURLConnection
        conn.connectTimeout = 15_000
        conn.readTimeout = 15_000
        conn.setRequestProperty("Accept", "application/vnd.github+json")
        conn.setRequestProperty("User-Agent", "Fifary")
        try {
            if (conn.responseCode != 200) error("${conn.responseCode}")
            val text = conn.inputStream.bufferedReader().readText()
            val tag = Regex("\"tag_name\"\\s*:\\s*\"([^\"]+)\"").find(text)?.groupValues?.get(1)
                ?: error("no tag")
            val page = Regex("\"html_url\"\\s*:\\s*\"([^\"]+)\"").find(text)?.groupValues?.get(1)
                ?: error("no page")
            return tag to page
        } finally {
            conn.disconnect()
        }
    }

    // 1.2.10 > 1.2.9：逐段比数字，不是字符串比
    private fun newer(tag: String, current: String): Boolean {
        fun parts(s: String) = s.trim().removePrefix("v").substringBefore('-')
            .split('.').map { it.toIntOrNull() ?: 0 }
        val a = parts(tag)
        val b = parts(current)
        for (i in 0 until maxOf(a.size, b.size)) {
            val x = a.getOrElse(i) { 0 }
            val y = b.getOrElse(i) { 0 }
            if (x != y) return x > y
        }
        return false
    }
}
