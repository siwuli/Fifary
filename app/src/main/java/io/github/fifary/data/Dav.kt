package io.github.fifary.data

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import java.io.File
import java.net.URL
import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.concurrent.TimeUnit

// 只认标准 WebDAV：PROPFIND 列目录 / PUT 上传 / GET 下载 / MKCOL 建目录。
// 冲突策略是「谁的最后修改时间新听谁的」，不删远端也不删本地 —— 宁可多两份，不吞数据
object Dav {

    enum class Mode { UPLOAD, DOWNLOAD, BOTH }

    var running by mutableStateOf(false)
        private set
    var message by mutableStateOf<String?>(null)
        private set

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    val configured: Boolean
        get() = SettingsStore.davUrl.isNotBlank() && SettingsStore.davUser.isNotBlank()

    fun start(context: Context, mode: Mode) {
        if (running || !configured) return
        running = true
        message = null
        val app = context.applicationContext
        scope.launch {
            val outcome = runCatching { sync(app, mode) }
            message = outcome.fold(
                onSuccess = { (up, down) ->
                    if (up + down == 0) "已是最新" else "上传 $up · 下载 $down"
                },
                onFailure = { "同步失败：${it.message ?: "网络错误"}" },
            )
            running = false
            if (outcome.isSuccess) NotesStore.reload(app)
        }
    }

    // 进后台/回前台各同步一次，不占用前台时间
    fun auto(context: Context) {
        if (SettingsStore.davAuto && configured) start(context, Mode.BOTH)
    }

    private data class Item(val url: String, val time: Long)

    private fun sync(context: Context, mode: Mode): Pair<Int, Int> {
        val base = base()
        val local = Storage.root(context)
        var up = 0
        var down = 0

        mkcol(base)
        val remote = list(base, 0)
        val localFiles = local.walkTopDown()
            .filter { it.isFile && it.extension != "tmp" }
            .associateBy { it.relativeTo(local).invariantSeparatorsPath }

        // 远端缺的目录先建出来，不然 PUT 到不存在的目录会 409
        localFiles.keys.mapNotNull { it.substringBeforeLast('/', "").ifEmpty { null } }
            .distinct()
            .forEach { mkcol(base + encodePath(it) + "/") }

        for ((rel, file) in localFiles) {
            val hit = remote[rel]
            val stamp = file.lastModified()
            val newer = hit == null || stamp > hit.time + 2000
            if (mode != Mode.DOWNLOAD && newer) {
                put(base + encodePath(rel), file)
                up++
            } else if (mode != Mode.UPLOAD && hit != null && hit.time > stamp + 2000) {
                get(hit.url, file)
                down++
            }
        }

        if (mode != Mode.UPLOAD) {
            for ((rel, item) in remote) {
                if (rel in localFiles) continue
                val target = File(local, rel)
                target.parentFile?.mkdirs()
                get(item.url, target)
                down++
            }
        }
        return up to down
    }

    // ---- WebDAV ----

    // 用户填的地址只当父目录，真正读写的永远是它下面的 Fifary/：
    // 直接把根目录当同步目录，会和用户 WebDAV 里已有的文件混在一起
    private fun base(): String {
        val url = SettingsStore.davUrl.trim().trimEnd('/')
        if (url.endsWith("/$DIR", true)) return "$url/"
        return "$url/$DIR/"
    }

    private const val DIR = "Fifary"

    private fun basic(): String {
        val raw = "${SettingsStore.davUser}:${SettingsStore.davPass}"
        return "Basic " + android.util.Base64.encodeToString(raw.toByteArray(), android.util.Base64.NO_WRAP)
    }

    private fun mkcol(url: String) {
        runCatching { call(url, "MKCOL", null).close() }
    }

    private fun call(url: String, method: String, body: RequestBody?, depth: Int? = null): Response {
        val request = Request.Builder()
            .url(url)
            .header("Authorization", basic())
            .header("User-Agent", "Fifary")
            .apply { if (depth != null) header("Depth", depth.toString()) }
            .method(method, body)
            .build()
        return client.newCall(request).execute()
    }

    // 递归列目录：分组是平铺的，attachments 只有一层，深度给 4 足够
    private fun list(dirUrl: String, depth: Int): Map<String, Item> {
        if (depth > 4) return emptyMap()
        val result = LinkedHashMap<String, Item>()
        call(dirUrl, "PROPFIND", XmlBody.toRequestBody(Xml), 1).use { response ->
            if (!response.isSuccessful) error("列目录失败 HTTP ${response.code}")
            val xml = response.body?.string().orEmpty()
            for (block in blocks(xml)) {
                val href = tag(block, "href") ?: continue
                val url = absolute(href)
                val dir = isDir(block)
                if (dir) {
                    // 自己这条会被列出来，跳过；子目录继续往里看
                    if (url.trimEnd('/') != dirUrl.trimEnd('/')) {
                        result.putAll(list(url, depth + 1))
                    }
                } else {
                    val rel = rel(url) ?: continue
                    if (rel.isEmpty()) continue
                    result[rel] = Item(url, time(tag(block, "getlastmodified")))
                }
            }
        }
        return result
    }

    private fun absolute(href: String): String {
        if (!href.startsWith("http")) {
            val u = URL(base())
            return "${u.protocol}://${u.authority}${if (href.startsWith("/")) "" else "/"}$href"
        }
        return href
    }

    // href 换算成相对 vault 根的路径（= 远端 base 之后的路径）
    private fun rel(url: String): String? {
        val path = runCatching { URL(url).path }.getOrNull() ?: return null
        val basePath = runCatching { URL(base()).path }.getOrDefault("/")
        if (!path.startsWith(basePath)) return null
        return java.net.URLDecoder.decode(path.removePrefix(basePath), "UTF-8").trimEnd('/')
    }

    private fun put(url: String, file: File) {
        call(url, "PUT", file.asRequestBody(Octet)).use { response ->
            if (!response.isSuccessful) error("上传失败 HTTP ${response.code}")
        }
        // 传完把本地时间对齐到"刚刚"：远端此刻也是这个时间，下一次就不会又判成一边更新
        file.setLastModified(System.currentTimeMillis())
    }

    private fun get(url: String, file: File) {
        call(url, "GET", null).use { response ->
            if (!response.isSuccessful) error("下载失败 HTTP ${response.code}")
            val body = response.body ?: error("下载失败：没有内容")
            // 先写临时文件再改名：下到一半断了，本地原来的文件还在
            val tmp = File(file.parentFile, file.name + ".tmp")
            body.byteStream().use { input -> tmp.outputStream().use { input.copyTo(it) } }
            if (!tmp.renameTo(file)) {
                file.delete()
                tmp.renameTo(file)
            }
            file.setLastModified(System.currentTimeMillis())
        }
    }

    private fun encodePath(path: String): String = path.split('/').joinToString("/") {
        URLEncoder.encode(it, "UTF-8").replace("+", "%20")
    }

    private fun tag(block: String, name: String): String? = Regex(
        "<(?:[\\w-]+:)?$name[^>]*>(.*?)</(?:[\\w-]+:)?$name>",
        setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE),
    ).find(block)?.groupValues?.get(1)?.trim()

    private fun blocks(xml: String): List<String> = Regex(
        "<(?:[\\w-]+:)?response[^>]*>(.*?)</(?:[\\w-]+:)?response>",
        setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE),
    ).findAll(xml).map { it.groupValues[1] }.toList()

    private fun isDir(block: String): Boolean =
        Regex("<(?:[\\w-]+:)?collection", RegexOption.IGNORE_CASE).containsMatchIn(block)

    private fun time(text: String?): Long {
        if (text.isNullOrBlank()) return 0L
        val format = SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss zzz", Locale.US)
        return runCatching { format.parse(text)?.time ?: 0L }.getOrDefault(0L)
    }

    private const val XmlBody =
        "<?xml version=\"1.0\" encoding=\"utf-8\"?>" +
            "<d:propfind xmlns:d=\"DAV:\"><d:prop>" +
            "<d:getlastmodified/><d:getcontentlength/><d:resourcetype/>" +
            "</d:prop></d:propfind>"

    private val client by lazy {
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build()
    }

    private val Xml = "application/xml; charset=utf-8".toMediaType()
    private val Octet = "application/octet-stream".toMediaType()
}
