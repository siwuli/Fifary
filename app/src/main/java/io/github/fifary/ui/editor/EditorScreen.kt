package io.github.fifary.ui.editor

import android.content.Context
import android.content.Intent
import android.media.MediaRecorder
import android.os.Build
import android.speech.RecognizerIntent
import android.view.inputmethod.InputMethodManager
import android.webkit.WebView
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Checklist
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FormatBold
import androidx.compose.material.icons.filled.FormatItalic
import androidx.compose.material.icons.filled.FormatListBulleted
import androidx.compose.material.icons.filled.FormatListNumbered
import androidx.compose.material.icons.filled.FormatSize
import androidx.compose.material.icons.filled.FormatStrikethrough
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.HorizontalRule
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Photo
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.Redo
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.TableChart
import androidx.compose.material.icons.filled.Undo
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import io.github.fifary.data.Attach
import io.github.fifary.data.Note
import io.github.fifary.data.NotesStore
import io.github.fifary.ui.list.titleOf
import io.github.fifary.ui.theme.Space
import org.json.JSONObject
import org.json.JSONTokener
import kotlinx.coroutines.delay

import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private enum class Panel { NONE, FORMAT, INSERT }

// 编辑页照参考 App：标题一块、富文本正文一块。有内容就存着走人；
// 一个字都没有的新笔记直接扔掉，不留「无标题」
@Composable
@OptIn(ExperimentalLayoutApi::class)
fun EditorScreen(
    note: Note?,
    folder: String,
    onDiscard: () -> Unit,
    onSave: (title: String, body: String) -> Unit,
) {
    val context = LocalContext.current
    val initial = remember(note?.path) { note?.body.orEmpty() }
    var panel by remember { mutableStateOf(Panel.NONE) }
    var page by remember { mutableStateOf<WebView?>(null) }

    var recorder by remember { mutableStateOf<MediaRecorder?>(null) }
    var recordFile by remember { mutableStateOf<File?>(null) }
    var recordStart by remember { mutableStateOf(0L) }
    var recordSec by remember { mutableStateOf(0) }
    var canUndo by remember { mutableStateOf(false) }
    var canRedo by remember { mutableStateOf(false) }

    // 没历史可走的那一头，顶栏按钮得灰掉
    LaunchedEffect(page) {
        val view = page ?: return@LaunchedEffect
        while (true) {
            view.evaluateJavascript("canUndo() + ',' + canRedo()") { r ->
                val pair = r.trim('"').split(",")
                canUndo = pair.getOrNull(0) == "1"
                canRedo = pair.getOrNull(1) == "1"
            }
            delay(400)
        }
    }

    // 录音时每秒刷新格子上的秒数：点了没任何变化，等于没反应
    LaunchedEffect(recorder) {
        if (recorder == null) {
            recordSec = 0
            return@LaunchedEffect
        }
        while (true) {
            recordSec = ((System.currentTimeMillis() - recordStart) / 1000).toInt()
            delay(1000)
        }
    }

    DisposableEffect(Unit) {
        onDispose { recorder?.release() }
    }

    fun js(code: String) {
        page?.evaluateJavascript(code, null)
    }

    // 格式按了得有个说法：JS 找不到要改的行就回 0，用提示顶上去，不然按钮看着像坏的；
    // 改完顺手留一版历史，连续点几次格式也都能撤销
    fun fmt(code: String) {
        val view = page
        if (view == null) {
            Toast.makeText(context, "编辑器还没准备好", Toast.LENGTH_SHORT).show()
            return
        }
        // 焦点得在改之前给回 WebView：焦点在标题上时改 DOM，浏览器不画光标
        view.requestFocus()
        // 焦点是从标题那边抢回来的话输入法已经收了，改完得叫回来，不然还得再点一下才能打字
        view.post {
            val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
            imm.showSoftInput(view, 0)
        }
        view.evaluateJavascript("var _r = ($code); snap(); _r") { r ->
            if (r == "0") Toast.makeText(context, "先点一下要改的那一行", Toast.LENGTH_SHORT).show()
        }
        // 改的是 DOM，WebView 偶尔不自己重绘
        view.invalidate()
    }

    fun leave() {
        val view = page
        if (view == null) {
            onDiscard()
            return
        }
        // 笔记的名字就是正文第一行（参考便签没有单独的标题框）。
        // 已经起过名的沿用原名，不然一存就把用户的文件改名了（外面显示的标题照样跟正文走）
        view.evaluateJavascript("getMd()") { json ->
            val md = runCatching { JSONTokener(json).nextValue() as String }.getOrNull()
            if (md == null) {
                Toast.makeText(context, "没能取出正文，再点一次完成", Toast.LENGTH_SHORT).show()
                return@evaluateJavascript
            }
            val old = note?.name.orEmpty()
            val name = if (NotesStore.isUntitled(old)) titleOf(md) else old
            if (note == null && md.isBlank()) onDiscard() else onSave(name, md)
        }
    }

    fun startRecord() {
        val file = Attach.newAudio(context)
        val started = runCatching {
            val r = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                MediaRecorder(context)
            } else {
                @Suppress("DEPRECATION") MediaRecorder()
            }
            r.setAudioSource(MediaRecorder.AudioSource.MIC)
            r.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            r.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            r.setOutputFile(file.absolutePath)
            r.prepare()
            r.start()
            r
        }.getOrNull()
        if (started == null) {
            file.delete()
            Toast.makeText(context, "这台设备没法录音", Toast.LENGTH_SHORT).show()
            return
        }
        recorder = started
        recordFile = file
        recordStart = System.currentTimeMillis()
    }

    fun stopRecord() {
        val r = recorder ?: return
        runCatching { r.stop() }
        r.release()
        recorder = null
        val file = recordFile ?: return
        val seconds = ((System.currentTimeMillis() - recordStart) / 1000).toInt()
        // 手抖按到的零秒录音不留，免得 vault 里堆空附件
        if (seconds < 1) {
            file.delete()
            return
        }
        val path = "${Attach.DIR}/${file.name}"
        fmt("insertLink(${JSONObject.quote(path)}, ${JSONObject.quote("录音 $seconds 秒")})")
    }

    val pickImage = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) {
            Attach.copy(context, uri)?.let { path -> fmt("insertImage(${JSONObject.quote(path)})") }
        }
    }
    val askMic = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) startRecord()
        else Toast.makeText(context, "没有麦克风权限", Toast.LENGTH_SHORT).show()
    }

    var shot by remember { mutableStateOf<File?>(null) }
    val photographLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        val file = shot
        shot = null
        if (file == null) return@rememberLauncherForActivityResult
        if (ok) fmt("insertImage(${JSONObject.quote("${Attach.DIR}/${file.name}")})") else file.delete()
    }

    fun photograph() {
        val file = Attach.newImage(context)
        shot = file
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.file", file)
        runCatching { photographLauncher.launch(uri) }.onFailure {
            shot = null
            file.delete()
        }
    }

    val pickFile = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            val name = uri.lastPathSegment?.substringAfterLast('/') ?: "文件"
            Attach.copy(context, uri)?.let { path ->
                fmt("insertLink(${JSONObject.quote(path)}, ${JSONObject.quote(name)})")
            }
        }
    }

    // 参考 app 气泡里的麦克风是「语音输入」：说的话直接变成正文
    val talk = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val heard = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()
        if (!heard.isNullOrBlank()) fmt("insertText(${JSONObject.quote(heard)})")
    }

    fun dictate() {
        val ask = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE, "zh-CN")
        runCatching { talk.launch(ask) }
    }

    BackHandler { leave() }

    // 输入法一冒头气泡就长开，缩回去就只剩「格式」「插入」
    val typing = WindowInsets.isImeVisible

    // 收起输入法就把面板一并合上，不然它会一直挂在下面
    LaunchedEffect(typing) { if (!typing) panel = Panel.NONE }

    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
            .statusBarsPadding()
            .windowInsetsPadding(WindowInsets.ime.union(WindowInsets.navigationBars))
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .height(52.dp)
                .padding(horizontal = Space.xxs),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = { leave() }) {
                Icon(Icons.Filled.ArrowBack, contentDescription = "返回")
            }
            Spacer(Modifier.weight(1f))
            // 参考顶栏：撤销 / 重做 / 完成
            IconButton(onClick = { js("undo()") }, enabled = canUndo) {
                Icon(Icons.Filled.Undo, contentDescription = "撤销")
            }
            IconButton(onClick = { js("redo()") }, enabled = canRedo) {
                Icon(Icons.Filled.Redo, contentDescription = "重做")
            }
            TextButton(onClick = { leave() }) {
                Text(
                    text = "完成",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }

        // 参考 app 顶栏下面那行：最后修改时间 | 分组（不是创建时间）
        Text(
            text = "${stamp(note?.updatedAt ?: System.currentTimeMillis())}  |  ${folder.ifEmpty { NotesStore.DEFAULT }}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 24.dp, bottom = 4.dp),
        )

        RichBody(
            markdown = initial,
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            onReady = { page = it },
        )

        Box(
            Modifier
                .fillMaxWidth()
                .padding(start = Space.m, end = Space.m, bottom = Space.xs),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                Modifier
                    // 平时是居中的小胶囊，点开面板才铺满整宽（参考同样是底部整宽面板）
                    .then(if (panel == Panel.NONE) Modifier.wrapContentWidth() else Modifier.fillMaxWidth())
                    .shadow(8.dp, RoundedCornerShape(32.dp))
                    .clip(RoundedCornerShape(32.dp))
                    .background(MaterialTheme.colorScheme.surface)
                    .animateContentSize(spring(dampingRatio = 0.78f, stiffness = 460f)),
            ) {
                Crossfade(targetState = panel, animationSpec = tween(180), label = "bubble") { current ->
                    when (current) {
                        Panel.NONE -> Row(
                            Modifier
                                .height(64.dp)
                                .horizontalScroll(rememberScrollState())
                                .padding(horizontal = 10.dp),
                            horizontalArrangement = Arrangement.spacedBy(Space.xxs),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            BubbleIcon(Icons.Filled.FormatSize, "格式") {
                                // 面板一开 WebView 就丢焦点，进来先把选区抓下来
                                js("syncSel()")
                                panel = Panel.FORMAT
                            }
                            if (typing) {
                                BubbleIcon(Icons.Filled.Checklist, "待办") { fmt("todo()") }
                                BubbleIcon(Icons.Filled.Photo, "图片") {
                                    pickImage.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                                }
                                BubbleIcon(Icons.Filled.Mic, "语音输入") { dictate() }
                            }
                            BubbleIcon(Icons.Filled.Add, "插入") { panel = Panel.INSERT }
                        }

                        // 参考 rich_text_tool_panel_layout：标题栏（左 20dp「格式」+ 右上 24dp ✕），
                        // 下面四排：文字样式 / 字符样式 / 段落 / 分割线与字号；每排高 toolbar_item_height 44dp
                        Panel.FORMAT -> Column(Modifier.padding(bottom = Space.xxs)) {
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .height(50.dp)
                                    .padding(start = 20.dp, end = 16.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text("格式", style = MaterialTheme.typography.titleLarge)
                                Spacer(Modifier.weight(1f))
                                Box(
                                    Modifier
                                        .size(32.dp)
                                        .clip(RoundedCornerShape(16.dp))
                                        .clickable { panel = Panel.NONE },
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Icon(Icons.Filled.Close, contentDescription = "收起", modifier = Modifier.size(24.dp))
                                }
                            }

                            // 文字样式：标题 24 / 副标题 20 / 小标题 16 半粗 / 正文 16 / 注释 14
                            PanelRow {
                                StyleButton("标题", 24, FontWeight.SemiBold) { fmt("block('h1')") }
                                StyleButton("副标题", 20, FontWeight.SemiBold) { fmt("block('h2')") }
                                StyleButton("小标题", 16, FontWeight.SemiBold) { fmt("block('h3')") }
                                StyleButton("正文", 16, FontWeight.Normal) { fmt("block('p')") }
                                StyleButton("注释", 14, FontWeight.Normal) { fmt("block('blockquote')") }
                            }

                            // 只留 markdown 存得下的：下划线/文字底色/字体/字号/缩进/对齐/波浪线
                            // 存进 .md 只能变成一堆内嵌 HTML，干脆不要这些按钮
                            PanelRow {
                                BubbleIcon(Icons.Filled.FormatBold, "加粗", box = 44.dp) { fmt("wrapTag('b')") }
                                BubbleIcon(Icons.Filled.FormatItalic, "斜体", box = 44.dp) { fmt("wrapTag('i')") }
                                BubbleIcon(Icons.Filled.FormatStrikethrough, "删除线", box = 44.dp) {
                                    fmt("wrapTag('s')")
                                }
                                BubbleIcon(Icons.Filled.FormatListBulleted, "无序列表", box = 44.dp) {
                                    fmt("list(false)")
                                }
                                BubbleIcon(Icons.Filled.FormatListNumbered, "有序列表", box = 44.dp) {
                                    fmt("list(true)")
                                }
                            }

                            PanelRow {
                                BubbleIcon(Icons.Filled.Checklist, "待办", box = 44.dp) { fmt("todo()") }
                                BubbleIcon(Icons.Filled.HorizontalRule, "分割线", box = 44.dp) { fmt("insertHr()") }
                            }
                        }

                        // 参考的「插入」面板：左上「关闭」、正中「添加」，下面 70dp 图标格子，8dp 间距
                        Panel.INSERT -> Column(Modifier.padding(top = Space.xxs, bottom = Space.l)) {
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .height(52.dp)
                                    .padding(horizontal = Space.m),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Box(
                                    Modifier
                                        .clip(RoundedCornerShape(20.dp))
                                        .background(MaterialTheme.colorScheme.surfaceVariant)
                                        .clickable { if (recorder != null) stopRecord(); panel = Panel.NONE }
                                        .padding(horizontal = 20.dp, vertical = 8.dp),
                                ) {
                                    Text("关闭", style = MaterialTheme.typography.titleSmall)
                                }
                                Spacer(Modifier.weight(1f))
                                Text("添加", style = MaterialTheme.typography.titleLarge)
                                Spacer(Modifier.weight(1f))
                                Spacer(Modifier.size(64.dp, 1.dp))
                            }
                            ExtraRow(
                                listOf(
                                    "相册" to Icons.Filled.Photo,
                                    "拍摄" to Icons.Filled.PhotoCamera,
                                    "表格" to Icons.Filled.TableChart,
                                    "文件" to Icons.Filled.Folder,
                                ),
                            ) { label ->
                                when (label) {
                                    "相册" -> pickImage.launch(
                                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
                                    )
                                    "拍摄" -> photograph()
                                    "表格" -> fmt("insertTable()")
                                    "文件" -> pickFile.launch(arrayOf("*/*"))
                                }
                            }
                            ExtraRow(
                                listOf(
                                    (if (recorder != null) "录音中 ${recordSec}s" else "录音") to
                                        (if (recorder != null) Icons.Filled.Stop else Icons.Filled.Mic),
                                    "分割线" to Icons.Filled.HorizontalRule,
                                ),
                                activeIndex = if (recorder != null) 0 else -1,
                            ) { label ->
                                when {
                                    label.startsWith("录音") -> if (recorder != null) {
                                        stopRecord()
                                    } else {
                                        askMic.launch(android.Manifest.permission.RECORD_AUDIO)
                                    }
                                    label == "分割线" -> fmt("insertHr()")
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

// 参考 editor_toolbar_extra：childMinWidth 70dp、间距 8dp，图标格子 + 下方小标签
@Composable
private fun ExtraRow(items: List<Pair<String, ImageVector>>, activeIndex: Int = -1, onClick: (String) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = Space.m, vertical = Space.xs),
        horizontalArrangement = Arrangement.spacedBy(Space.xs),
    ) {
        items.forEachIndexed { i, (label, icon) ->
            val on = i == activeIndex
            Column(
                Modifier.weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(
                    Modifier
                        .size(60.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(
                            if (on) MaterialTheme.colorScheme.primaryContainer
                            else MaterialTheme.colorScheme.surfaceVariant
                        )
                        .clickable { onClick(label) },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = label,
                        tint = if (on) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.size(28.dp),
                    )
                }
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (on) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    minLines = 2,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = Space.xxs),
                )
            }
        }
        // 参考是等宽的 70dp 格子：不满一行时补空位，不让剩下的格子被拉宽
        repeat(4 - items.size) { Spacer(Modifier.weight(1f)) }
    }
}

@Composable
private fun PanelRow(content: @Composable RowScope.() -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .height(44.dp)
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalAlignment = Alignment.CenterVertically,
        content = content,
    )
}

@Composable
private fun BubbleIcon(icon: ImageVector, label: String, box: Dp = 40.dp, onClick: () -> Unit) {
    Box(
        Modifier
            .size(box)
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.size(24.dp),
        )
    }
}

@Composable
private fun StyleButton(label: String, size: Int, weight: FontWeight, onClick: () -> Unit) {
    Box(
        Modifier
            .height(40.dp)
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text = label, fontSize = size.sp, fontWeight = weight, maxLines = 1)
    }
}


private fun stamp(at: Long): String =
    SimpleDateFormat("yyyy年M月d日 EEEE HH:mm", Locale.CHINA).format(Date(at))
