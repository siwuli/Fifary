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
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.URL
import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

// 只认标准 WebDAV：PROPFIND 列目录 / PUT 上传 / GET 下载 / MKCOL 建目录 / DELETE 删文件。
// 冲突策略：谁的最后修改时间新听谁的，两边都改过又不一样就把旧的那份另存成「(冲突 时间)」，
// 不丢任何一边的内容。删除靠本地记的一份索引传播：上次同步时两边都有的文件，
// 这次只有一边没了、另一边没动，就是那一边删的 —— 跟着删，而不是又拉回来。
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
                onSuccess = { (up, down, gone) ->
                    if (up + down + gone == 0) {
                        "已是最新"
                    } else {
                        "上传 $up · 下载 $down" + if (gone > 0) " · 删除 $gone" else ""
                    }
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

    private data class Item(val url: String, val time: Long, val size: Long)

    // 上次同步完某一刻两边各是什么样。有它才能分辨「这边刚改了」和「那边删了」
    private data class Mark(val lm: Long, val ls: Long, val rm: Long, val rs: Long) {
        fun localChanged(file: File): Boolean = lm != file.lastModified() || ls != file.length()
        // 服务器不报 content-length 时 size 是 0，只比时间，不然每轮都被当成远端改过
        fun remoteChanged(item: Item): Boolean =
            rm != item.time || (rs > 0 && item.size > 0 && rs != item.size)
    }

    private var marks: MutableMap<String, Mark> = HashMap()

    private fun sync(context: Context, mode: Mode): Triple<Int, Int, Int> {
        val base = base()
        val local = Storage.root(context)
        var up = 0
        var down = 0
        var gone = 0
        loadMarks(context, base)

        mkcol(base)
        val remote = list(base, 0)
        val files = local.walkTopDown()
            .filter { it.isFile && it.extension != "tmp" }
            .associateBy { it.relativeTo(local).invariantSeparatorsPath }

        // 远端缺的目录先建出来，不然 PUT 到不存在的目录会 409
        files.keys.mapNotNull { it.substringBeforeLast('/', "").ifEmpty { null } }
            .distinct()
            .forEach { mkcol(base + encodePath(it) + "/") }

        // 单向模式只负责推/拉，不对冲突留副本；删除两边都传播（索引能证明是删不是改）
        for (rel in files.keys + remote.keys + marks.keys) {
            val file = files[rel]
            val item = remote[rel]
            val mark = marks[rel]
            when {
                file != null && item != null -> {
                    val localMoved = mark == null || mark.localChanged(file)
                    val remoteMoved = mark == null || mark.remoteChanged(item)
                    when {
                        !localMoved && !remoteMoved -> {}
                        localMoved && !remoteMoved -> if (mode != Mode.DOWNLOAD) {
                            put(base + encodePath(rel), file)
                            up++
                        }
                        !localMoved && remoteMoved -> if (mode != Mode.UPLOAD) {
                            get(item.url, file, item.time)
                            down++
                        }
                        mode != Mode.BOTH -> {
                            // 单向模式不做冲突副本：本地的改动往上传，或者按远端覆盖下来
                            if (mode == Mode.UPLOAD) {
                                put(base + encodePath(rel), file)
                                up++
                            } else {
                                get(item.url, file, item.time)
                                down++
                            }
                        }
                        // 两边都动过：大小一样就是同一份内容（多半只是时间差），取新的；不一样才留副本
                        item.size == file.length() && item.size > 0 -> {
                            if (file.lastModified() > item.time) {
                                put(base + encodePath(rel), file)
                                up++
                            } else {
                                get(item.url, file, item.time)
                                down++
                            }
                        }
                        file.lastModified() > item.time -> {
                            get(item.url, conflictOf(file), item.time)
                            put(base + encodePath(rel), file)
                            up++
                            down++
                        }
                        else -> {
                            file.copyTo(conflictOf(file), overwrite = true)
                            get(item.url, file, item.time)
                            down++
                        }
                    }
                }
                // 只在本地：上次两边都有、本地没动过 → 远端那份被删了，跟着删；否则就是新笔记，传上去
                file != null -> {
                    if (mark != null && !mark.localChanged(file)) {
                        if (file.delete()) gone++
                    } else if (mode != Mode.DOWNLOAD) {
                        put(base + encodePath(rel), file)
                        up++
                    }
                }
                // 只在远端：上次两边都有、远端没动过 → 本地删的，远端也删；否则是别处新加的，拉下来
                item != null -> {
                    if (mark != null && !mark.remoteChanged(item)) {
                        remove(item.url)
                        gone++
                    } else if (mode != Mode.UPLOAD) {
                        val target = File(local, rel)
                        target.parentFile?.mkdirs()
                        get(item.url, target, item.time)
                        down++
                    }
                }
            }
        }

        rebuildMarks(context, base, local)
        return Triple(up, down, gone)
    }

    // ---- 同步索引：只存两边都有的文件，删掉的自然就不在了 ----

    private fun indexFile(context: Context) = File(context.filesDir, "sync-state.json")

    private fun loadMarks(context: Context, base: String) {
        marks = HashMap()
        val text = runCatching { indexFile(context).readText() }.getOrNull() ?: return
        runCatching {
            val one = JSONObject(text).optJSONObject(base) ?: return
            for (rel in one.keys()) {
                val a = one.getJSONArray(rel)
                marks[rel] = Mark(a.getLong(0), a.getLong(1), a.getLong(2), a.getLong(3))
            }
        }
    }

    private fun rebuildMarks(context: Context, base: String, local: File) {
        val remote = runCatching { list(base, 0) }.getOrElse { return }
        val fresh = HashMap<String, Mark>()
        local.walkTopDown().filter { it.isFile && it.extension != "tmp" }.forEach { file ->
            val rel = file.relativeTo(local).invariantSeparatorsPath
            val item = remote[rel] ?: return@forEach
            fresh[rel] = Mark(file.lastModified(), file.length(), item.time, item.size)
        }
        marks = fresh
        runCatching {
            val file = indexFile(context)
            // 换过服务器也要留着别的账号的索引，不然切回来一次全当新文件
            val root = JSONObject(runCatching { file.readText() }.getOrDefault("{}"))
            val one = JSONObject()
            marks.forEach { (rel, m) -> one.put(rel, JSONArray().put(m.lm).put(m.ls).put(m.rm).put(m.rs)) }
            root.put(base, one)
            file.writeText(root.toString())
        }
    }

    // 冲突不覆盖：另存一份「名字 (冲突 时间)」放同一个目录，下一轮它会自己传上去
    private fun conflictOf(file: File): File {
        val stamp = SimpleDateFormat("yyyy-MM-dd HHmmss", Locale.US).format(Date())
        val name = file.name
        val dot = name.lastIndexOf('.')
        val tagged = if (dot > 0) {
            name.substring(0, dot) + " (冲突 $stamp)" + name.substring(dot)
        } else {
            "$name (冲突 $stamp)"
        }
        return File(file.parentFile, tagged)
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
                    val size = tag(block, "getcontentlength")?.toLongOrNull() ?: 0L
                    result[rel] = Item(url, time(tag(block, "getlastmodified")), size)
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

    // 下完把 mtime 对齐成远端那份的修改时间：给成「现在」的话，所有笔记一同步就全变成刚刚
    private fun get(url: String, file: File, stamp: Long) {
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
            file.setLastModified(if (stamp > 0L) stamp else System.currentTimeMillis())
        }
    }

    private fun remove(url: String) {
        call(url, "DELETE", null).use { response ->
            // 404 = 已经没了，目的达到
            if (!response.isSuccessful && response.code != 404) error("删除失败 HTTP ${response.code}")
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
