package io.github.fifary.ui.editor

import io.github.fifary.ui.Lang
import io.github.fifary.ui.S
import android.content.Context
import android.media.MediaRecorder
import android.os.Build
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
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
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
import io.github.fifary.ui.components.Bubble
import io.github.fifary.ui.components.bubble
import io.github.fifary.ui.list.titleOf
import io.github.fifary.ui.theme.Elev
import io.github.fifary.ui.theme.LocalDarkTheme
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
    // 切后台时先把正文落盘，不退出页面；不落盘的话打完字被系统杀掉就全没了
    onStash: (title: String, body: String) -> Unit = { _, _ -> },
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

    // 录到一半就走了：停下、释放，没插进正文的那个文件删掉，不然 vault 里全是没人用的录音
    DisposableEffect(Unit) {
        onDispose {
            val r = recorder
            if (r != null) {
                runCatching { r.stop() }
                r.release()
                recordFile?.delete()
            }
        }
    }

    fun js(code: String) {
        page?.evaluateJavascript(code, null)
    }

    // 格式按了得有个说法：JS 找不到要改的行就回 0，用提示顶上去，不然按钮看着像坏的；
    // 改完顺手留一版历史，连续点几次格式也都能撤销
    fun fmt(code: String) {
        val view = page
        if (view == null) {
            Toast.makeText(context, S("编辑器还没准备好"), Toast.LENGTH_SHORT).show()
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
            if (r == "0") Toast.makeText(context, S("先点一下要改的那一行"), Toast.LENGTH_SHORT).show()
        }
        // 改的是 DOM，WebView 偶尔不自己重绘
        view.invalidate()
    }

    // 正文只在 WebView 里，取出来是异步的：存盘和切后台存盘都走这里
    fun readMd(after: (String) -> Unit) {
        val view = page ?: return
        view.evaluateJavascript("getMd()") { json ->
            val md = runCatching { JSONTokener(json).nextValue() as String }.getOrNull()
            if (md == null) {
                Toast.makeText(context, S("没能取出正文，再点一次完成"), Toast.LENGTH_SHORT).show()
                return@evaluateJavascript
            }
            after(md)
        }
    }

    fun leave() {
        if (page == null) {
            onDiscard()
            return
        }
        // 笔记没有单独的标题：正文第一行就是标题，外面（列表卡片、文件名）都跟着它走。
        // 重名照存不误——文件重名由存储层自己错开，两条同名笔记能同时存在
        readMd { md -> if (note == null && md.isBlank()) onDiscard() else onSave(titleOf(md), md) }
    }

    val stashNow by rememberUpdatedState(onStash)
    val owner = LocalLifecycleOwner.current
    DisposableEffect(owner) {
        val observer = LifecycleEventObserver { _, event ->
            // ON_PAUSE 而不是 ON_STOP：App 在 ON_STOP 上锁，私密笔记得赶在密钥清掉之前落盘
            if (event == Lifecycle.Event.ON_PAUSE) readMd { md -> if (md.isNotBlank()) stashNow(titleOf(md), md) }
        }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer) }
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
            Toast.makeText(context, S("这台设备没法录音"), Toast.LENGTH_SHORT).show()
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
        else Toast.makeText(context, S("没有麦克风权限"), Toast.LENGTH_SHORT).show()
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
            val name = uri.lastPathSegment?.substringAfterLast('/') ?: S("文件")
            Attach.copy(context, uri)?.let { path ->
                fmt("insertLink(${JSONObject.quote(path)}, ${JSONObject.quote(name)})")
            }
        }
    }

    // 参考 app 气泡里的麦克风是「语音输入」，但那要靠系统的识别引擎：手机上没装/装的是个
    // 只会转写不回调结果的界面时，按下去等于没反应。改成 App 自己录，录完插进正文能点开播
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
                .height(56.dp)
                .padding(horizontal = Space.s),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Space.xs),
        ) {
            TopBubble(Icons.Filled.ArrowBack, S("返回")) { leave() }
            Spacer(Modifier.weight(1f))
            // 参考顶栏：撤销 / 重做 / 完成
            TopBubble(Icons.Filled.Undo, S("撤销"), enabled = canUndo) { js("undo()") }
            TopBubble(Icons.Filled.Redo, S("重做"), enabled = canRedo) { js("redo()") }
            Bubble(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primary,
                elevation = Elev.bubble,
                modifier = Modifier.height(38.dp),
                onClick = { leave() },
            ) {
                Text(
                    text = S("完成"),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.padding(horizontal = Space.m),
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
            dark = LocalDarkTheme.current,
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
                    .bubble(RoundedCornerShape(32.dp), MaterialTheme.colorScheme.surfaceVariant)
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
                            BubbleIcon(Icons.Filled.FormatSize, S("格式")) {
                                // 面板一开 WebView 就丢焦点，进来先把选区抓下来
                                js("syncSel()")
                                panel = Panel.FORMAT
                            }
                            if (typing || recorder != null) {
                                BubbleIcon(Icons.Filled.Checklist, S("待办")) { fmt("todo()") }
                                BubbleIcon(Icons.Filled.Photo, S("图片")) {
                                    pickImage.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                                }
                                // 录音走 App 自己的录制：系统的「语音输入」能不能用全看设备装了什么，
                                // 实测这台机器上 ACTION_RECOGNIZE_SPEECH 只会去开一个转写界面，回来永远是空结果
                                val recording = recorder != null
                                BubbleIcon(
                                    icon = if (recording) Icons.Filled.Stop else Icons.Filled.Mic,
                                    label = if (recording) "录音中 ${recordSec}s" else S("录音"),
                                    tint = if (recording) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                                ) {
                                    if (recording) {
                                        stopRecord()
                                    } else {
                                        askMic.launch(android.Manifest.permission.RECORD_AUDIO)
                                    }
                                }
                            }
                            BubbleIcon(Icons.Filled.Add, S("插入")) { panel = Panel.INSERT }
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
                                Text(S("格式"), style = MaterialTheme.typography.titleLarge)
                                Spacer(Modifier.weight(1f))
                                Box(
                                    Modifier
                                        .size(32.dp)
                                        .clip(RoundedCornerShape(16.dp))
                                        .clickable { panel = Panel.NONE },
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Icon(Icons.Filled.Close, contentDescription = S("收起"), modifier = Modifier.size(24.dp))
                                }
                            }

                            // 文字样式：标题 24 / 副标题 20 / 小标题 16 半粗 / 正文 16 / 注释 14
                            PanelRow {
                                StyleButton(S("标题"), 24, FontWeight.SemiBold) { fmt("block('h1')") }
                                StyleButton(S("副标题"), 20, FontWeight.SemiBold) { fmt("block('h2')") }
                                StyleButton(S("小标题"), 16, FontWeight.SemiBold) { fmt("block('h3')") }
                                StyleButton(S("正文"), 16, FontWeight.Normal) { fmt("block('p')") }
                                StyleButton(S("注释"), 14, FontWeight.Normal) { fmt("block('blockquote')") }
                            }

                            // 只留 markdown 存得下的：下划线/文字底色/字体/字号/缩进/对齐/波浪线
                            // 存进 .md 只能变成一堆内嵌 HTML，干脆不要这些按钮
                            PanelRow {
                                BubbleIcon(Icons.Filled.FormatBold, S("加粗"), box = 44.dp) { fmt("wrapTag('b')") }
                                BubbleIcon(Icons.Filled.FormatItalic, S("斜体"), box = 44.dp) { fmt("wrapTag('i')") }
                                BubbleIcon(Icons.Filled.FormatStrikethrough, S("删除线"), box = 44.dp) {
                                    fmt("wrapTag('s')")
                                }
                                BubbleIcon(Icons.Filled.FormatListBulleted, S("无序列表"), box = 44.dp) {
                                    fmt("list(false)")
                                }
                                BubbleIcon(Icons.Filled.FormatListNumbered, S("有序列表"), box = 44.dp) {
                                    fmt("list(true)")
                                }
                            }

                            PanelRow {
                                BubbleIcon(Icons.Filled.Checklist, S("待办"), box = 44.dp) { fmt("todo()") }
                                BubbleIcon(Icons.Filled.HorizontalRule, S("分割线"), box = 44.dp) { fmt("insertHr()") }
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
                                    Text(S("关闭"), style = MaterialTheme.typography.titleSmall)
                                }
                                Spacer(Modifier.weight(1f))
                                Text(S("添加"), style = MaterialTheme.typography.titleLarge)
                                Spacer(Modifier.weight(1f))
                                Spacer(Modifier.size(64.dp, 1.dp))
                            }
                            ExtraRow(
                                listOf(
                                    S("相册") to Icons.Filled.Photo,
                                    S("拍摄") to Icons.Filled.PhotoCamera,
                                    S("表格") to Icons.Filled.TableChart,
                                    S("文件") to Icons.Filled.Folder,
                                ),
                            ) { label ->
                                when (label) {
                                    S("相册") -> pickImage.launch(
                                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
                                    )
                                    S("拍摄") -> photograph()
                                    S("表格") -> fmt("insertTable()")
                                    S("文件") -> pickFile.launch(arrayOf("*/*"))
                                }
                            }
                            ExtraRow(
                                listOf(
                                    (if (recorder != null) "录音中 ${recordSec}s" else S("录音")) to
                                        (if (recorder != null) Icons.Filled.Stop else Icons.Filled.Mic),
                                    S("分割线") to Icons.Filled.HorizontalRule,
                                ),
                                activeIndex = if (recorder != null) 0 else -1,
                            ) { label ->
                                when {
                                    label.startsWith(S("录音")) -> if (recorder != null) {
                                        stopRecord()
                                    } else {
                                        askMic.launch(android.Manifest.permission.RECORD_AUDIO)
                                    }
                                    label == S("分割线") -> fmt("insertHr()")
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
private fun TopBubble(icon: ImageVector, label: String, enabled: Boolean = true, onClick: () -> Unit) {
    Bubble(
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceVariant,
        elevation = Elev.bubble,
        modifier = Modifier.size(38.dp),
        enabled = enabled,
        onClick = onClick,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.size(20.dp),
        )
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
private fun BubbleIcon(
    icon: ImageVector,
    label: String,
    box: Dp = 40.dp,
    tint: Color = MaterialTheme.colorScheme.onSurface,
    onClick: () -> Unit,
) {
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
            tint = tint,
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


private fun stamp(at: Long): String = SimpleDateFormat(
    if (Lang.en) "MMM d, yyyy HH:mm" else "yyyy年M月d日 EEEE HH:mm",
    if (Lang.en) Locale.ENGLISH else Locale.CHINA,
).format(Date(at))
