package io.github.fifary.ui.editor

import android.content.Intent
import android.graphics.BitmapFactory
import android.media.MediaRecorder
import android.os.Build
import android.speech.RecognizerIntent
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
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
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckBox
import androidx.compose.material.icons.filled.Checklist
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.FormatBold
import androidx.compose.material.icons.filled.FormatItalic
import androidx.compose.material.icons.filled.FormatListBulleted
import androidx.compose.material.icons.filled.FormatListNumbered
import androidx.compose.material.icons.filled.FormatQuote
import androidx.compose.material.icons.filled.FormatSize
import androidx.compose.material.icons.filled.FormatStrikethrough
import androidx.compose.material.icons.filled.HorizontalRule
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Photo
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Title
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.fifary.data.Attach
import io.github.fifary.data.Note
import io.github.fifary.data.NotesStore
import io.github.fifary.ui.theme.Radius
import io.github.fifary.ui.theme.Space
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private enum class Panel { NONE, FORMAT, INSERT }

// 参考 app 的块类型：标题 / 副标题 / 小标题 / 列表 / 待办 / 注释
private val Blocks = listOf(
    "标题" to "# ",
    "副标题" to "## ",
    "小标题" to "### ",
    "列表" to "- ",
    "有序列表" to "1. ",
    "待办" to "- [ ] ",
    "注释" to "> ",
)

// 编辑页照参考 App：标题一块、正文一块，正文就是 Markdown 原文。
// 有内容就存着走人；一个字都没有的新笔记直接扔掉，不留「无标题」
@Composable
@OptIn(ExperimentalLayoutApi::class)
fun EditorScreen(
    note: Note?,
    folder: String,
    onDiscard: () -> Unit,
    onSave: (title: String, body: String) -> Unit,
) {
    val context = LocalContext.current
    var title by remember(note?.path) { mutableStateOf(note?.name.orEmpty()) }
    var body by remember(note?.path) { mutableStateOf(TextFieldValue(note?.body.orEmpty())) }
    var panel by remember { mutableStateOf(Panel.NONE) }
    var preview by remember { mutableStateOf(false) }

    var recorder by remember { mutableStateOf<MediaRecorder?>(null) }
    var recordFile by remember { mutableStateOf<File?>(null) }
    var recordStart by remember { mutableStateOf(0L) }

    DisposableEffect(Unit) {
        onDispose { recorder?.release() }
    }

    fun leave() {
        if (note == null && title.isBlank() && body.text.isBlank()) onDiscard() else onSave(title, body.text)
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
        }.getOrNull() ?: return
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
        body = insert(body, "[录音 $seconds 秒](${Attach.DIR}/${file.name})")
    }

    val pickImage = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) {
            Attach.copy(context, uri)?.let { path -> body = insert(body, "![]($path)") }
        }
    }
    val askMic = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) startRecord()
    }

    // 参考 app 气泡里的麦克风是「语音输入」：说的话直接变成正文
    val talk = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val heard = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()
        if (!heard.isNullOrBlank()) body = insert(body, heard)
    }

    fun dictate() {
        val ask = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE, "zh-CN")
        runCatching { talk.launch(ask) }
    }

    BackHandler { leave() }

    // 输入法一冒头气泡就长开（多出待办/图片/语音），缩回去就只剩「格式」「插入」
    val typing = WindowInsets.isImeVisible && !preview

    // 收起输入法就把格式面板一并合上，不然它会一直挂在下面
    LaunchedEffect(typing) { if (!typing) panel = Panel.NONE }

    // 参考 app 的「标题」入口其实是往正文里打一个 /，再弹块类型让人选
    fun blockLineStart(): Int {
        val caret = body.selection.end.coerceIn(0, body.text.length)
        val at = body.text.lastIndexOf('\n', (caret - 1).coerceAtLeast(0))
        return if (at < 0) 0 else at + 1
    }

    fun pickBlock(prefix: String) {
        val start = blockLineStart()
        val caret = body.selection.end.coerceIn(0, body.text.length)
        val fresh = body.text.replaceRange(start, caret, prefix)
        body = TextFieldValue(fresh, TextRange(start + prefix.length))
    }

    // 参考 app 的判据：光标前是 "/" 或 " /" 就弹块类型（tiptap 里是 [1, "/"] / [2, " /"]）
    val lineText = body.text.substring(blockLineStart(), body.selection.end.coerceIn(0, body.text.length))
    val slashOpen = panel == Panel.FORMAT && (lineText == "/" || lineText == " /")

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
            TextButton(onClick = { preview = !preview }) {
                Text(if (preview) "编辑" else "预览", style = MaterialTheme.typography.labelLarge)
            }
            TextButton(onClick = { leave() }) {
                Text(
                    text = "完成",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }

        // 参考 app 顶栏下面那行：时间 | 分组
        Text(
            text = "${stamp(note?.createdAt ?: System.currentTimeMillis())}  |  ${folder.ifEmpty { NotesStore.DEFAULT }}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 24.dp, bottom = 4.dp),
        )

        // 标题：参考 app 单独一块，上限 50 字，占位就写「标题」
        BasicTextField(
            value = title,
            onValueChange = { if (it.length <= 50) title = it.replace('\n', ' ') },
            singleLine = true,
            textStyle = TextStyle(
                fontSize = 24.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
            ),
            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 24.dp, end = 20.dp, top = 12.dp, bottom = 10.dp)
                .heightIn(min = 45.dp),
            decorationBox = { inner ->
                Box {
                    if (title.isEmpty()) {
                        Text(
                            text = "标题",
                            fontSize = 24.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.outline,
                        )
                    }
                    inner()
                }
            },
        )

        if (preview) {
            MarkdownPreview(text = body.text, modifier = Modifier.weight(1f))
        } else {
            BasicTextField(
                value = body,
                onValueChange = { body = it },
                textStyle = TextStyle(
                    fontSize = 16.sp,
                    lineHeight = 26.sp,
                    color = MaterialTheme.colorScheme.onSurface,
                ),
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                visualTransformation = MarkedUp(
                    accent = MaterialTheme.colorScheme.primary,
                    faint = MaterialTheme.colorScheme.outline,
                ),
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(horizontal = 24.dp, vertical = 8.dp),
                decorationBox = { inner ->
                    Box {
                        if (body.text.isEmpty()) {
                            Text(
                                // 参考 app 正文的占位原文
                                text = "输入文本, 键入\"/\"快速调整格式",
                                fontSize = 16.sp,
                                color = MaterialTheme.colorScheme.outline,
                            )
                        }
                        inner()
                    }
                },
            )
        }

        // 底部气泡（照 COUIBottomFloatingEditToolbar）：16dp 边距、圆角胶囊、大阴影，
        // 不输入时只有两个入口，输入法一冒头就多出几个，尺寸变化走弹簧
        if (slashOpen) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Space.m, vertical = Space.xxs)
                    .shadow(6.dp, RoundedCornerShape(20.dp))
                    .clip(RoundedCornerShape(20.dp))
                    .background(MaterialTheme.colorScheme.surface)
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = Space.xs, vertical = Space.xxs),
                horizontalArrangement = Arrangement.spacedBy(Space.xxs),
            ) {
                for ((label, prefix) in Blocks) {
                    TextChip(label) { pickBlock(prefix) }
                }
            }
        }

        Box(
            Modifier
                .fillMaxWidth()
                .padding(start = Space.m, end = Space.m, bottom = Space.xs),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                Modifier
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
                                preview = false
                                panel = Panel.FORMAT
                            }
                            if (typing) {
                                BubbleIcon(Icons.Filled.Checklist, "待办") { body = prefixLines(body, "- [ ] ") }
                                BubbleIcon(Icons.Filled.Photo, "图片") {
                                    pickImage.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                                }
                                BubbleIcon(Icons.Filled.Mic, "语音输入") { dictate() }
                            }
                            BubbleIcon(Icons.Filled.Add, "插入") { panel = Panel.INSERT }
                        }

                        Panel.FORMAT -> Row(
                            Modifier
                                .height(64.dp)
                                .padding(horizontal = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            // 收起钉在最前面，别跟着横滑跑出屏幕（参考 app 的面板 ✕ 也是固定的）
                            BubbleIcon(Icons.Filled.Close, "收起") { panel = Panel.NONE }
                            Row(
                                Modifier
                                    .weight(1f, fill = false)
                                    .horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(Space.xxs),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                BubbleIcon(Icons.Filled.Title, "块类型") { body = insert(body, "/") }
                                BubbleIcon(Icons.Filled.FormatBold, "加粗") { body = wrap(body, "**") }
                                BubbleIcon(Icons.Filled.FormatItalic, "斜体") { body = wrap(body, "*") }
                                BubbleIcon(Icons.Filled.FormatStrikethrough, "删除线") { body = wrap(body, "~~") }
                                BubbleIcon(Icons.Filled.FormatListBulleted, "无序列表") { body = prefixLines(body, "- ") }
                                BubbleIcon(Icons.Filled.FormatListNumbered, "有序列表") { body = prefixLines(body, "1. ") }
                                BubbleIcon(Icons.Filled.FormatQuote, "注释") { body = prefixLines(body, "> ") }
                                BubbleIcon(Icons.Filled.CheckBox, "待办") { body = prefixLines(body, "- [ ] ") }
                                BubbleIcon(Icons.Filled.Code, "代码") { body = wrap(body, "`") }
                                BubbleIcon(Icons.Filled.HorizontalRule, "分割线") { body = insert(body, "\n\n---\n\n") }
                            }
                        }

                        Panel.INSERT -> Row(
                            Modifier
                                .height(64.dp)
                                .horizontalScroll(rememberScrollState())
                                .padding(horizontal = 10.dp),
                            horizontalArrangement = Arrangement.spacedBy(Space.xxs),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            BubbleIcon(Icons.Filled.Photo, "图片") {
                                pickImage.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                            }
                            val recording = recorder != null
                            BubbleIcon(if (recording) Icons.Filled.Stop else Icons.Filled.Mic, "录音") {
                                if (recording) stopRecord() else askMic.launch(android.Manifest.permission.RECORD_AUDIO)
                            }
                            BubbleIcon(Icons.Filled.HorizontalRule, "分割线") { body = insert(body, "\n\n---\n\n") }
                            BubbleIcon(Icons.Filled.Close, "收起") { panel = Panel.NONE }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun BubbleIcon(icon: ImageVector, label: String, onClick: () -> Unit) {
    Box(
        Modifier
            .size(48.dp)
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
private fun TextChip(label: String, onClick: () -> Unit) {
    Box(
        Modifier
            .heightIn(min = 36.dp)
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = Space.s, vertical = Space.xs),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.titleSmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

// ---- 边写边看：Markdown 标记留在原地但淡下去，内容按格式渲染（不改字，所以光标不用重映射） ----

private val InlineMarks = listOf(
    Regex("""\*\*([^*\n]+)\*\*""") to SpanStyle(fontWeight = FontWeight.Bold),
    Regex("""~~([^~\n]+)~~""") to SpanStyle(textDecoration = TextDecoration.LineThrough),
    Regex("""`([^`\n]+)`""") to SpanStyle(fontFamily = FontFamily.Monospace),
    Regex("""(?<![*\w])\*([^*\n]+)\*(?![*\w])""") to SpanStyle(fontStyle = FontStyle.Italic),
)

private val OrderedMark = Regex("""^\d+[.)] """)
private val ImageMark = Regex("""^!\[[^]]*]\([^)]*\)$""")

private class MarkedUp(private val accent: Color, private val faint: Color) : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText =
        TransformedText(markedUp(text.text, accent, faint), OffsetMapping.Identity)
}

private fun markedUp(src: String, accent: Color, faint: Color): AnnotatedString {
    val builder = AnnotatedString.Builder(src)
    var at = 0
    while (at < src.length) {
        val end = src.indexOf('\n', at).let { if (it < 0) src.length else it }
        val line = src.substring(at, end)
        val level = when {
            line.startsWith("### ") -> 3
            line.startsWith("## ") -> 2
            line.startsWith("# ") -> 1
            else -> 0
        }
        when {
            level > 0 -> {
                builder.addStyle(SpanStyle(color = faint), at, at + level + 1)
                builder.addStyle(
                    SpanStyle(fontSize = (22 - level * 2).sp, fontWeight = FontWeight.SemiBold),
                    at + level + 1,
                    end,
                )
            }

            line.startsWith("- [ ] ") || line.startsWith("- [x] ") ->
                builder.addStyle(SpanStyle(color = accent, fontWeight = FontWeight.Bold), at, at + 5)

            line.startsWith("- ") || line.startsWith("* ") || line.startsWith("> ") ->
                builder.addStyle(SpanStyle(color = accent), at, at + 2)

            else -> OrderedMark.find(line)?.let {
                builder.addStyle(SpanStyle(color = accent), at, at + it.value.length)
            }
        }
        if (ImageMark.matches(line.trim())) {
            builder.addStyle(SpanStyle(color = faint, fontFamily = FontFamily.Monospace), at, end)
        }
        for ((regex, style) in InlineMarks) {
            for (hit in regex.findAll(line)) {
                val inner = hit.groups[1] ?: continue
                builder.addStyle(SpanStyle(color = faint), at + hit.range.first, at + inner.range.first)
                builder.addStyle(style, at + inner.range.first, at + inner.range.last + 1)
                builder.addStyle(SpanStyle(color = faint), at + inner.range.last + 1, at + hit.range.last + 1)
            }
        }
        at = end + 1
    }
    return builder.toAnnotatedString()
}

// ---- 选区操作：选中就包住，再点一次拆掉 ----

private fun stamp(at: Long): String =
    SimpleDateFormat("yyyy/MM/dd HH:mm", Locale.getDefault()).format(Date(at))

private fun insert(value: TextFieldValue, text: String): TextFieldValue {
    val start = value.selection.min.coerceIn(0, value.text.length)
    val end = value.selection.max.coerceIn(0, value.text.length)
    val fresh = value.text.replaceRange(start, end, text)
    return TextFieldValue(fresh, TextRange(start + text.length))
}

private fun wrap(value: TextFieldValue, open: String, close: String = open): TextFieldValue {
    val text = value.text
    val start = value.selection.min.coerceIn(0, text.length)
    val end = value.selection.max.coerceIn(0, text.length)
    val already = start >= open.length && end + close.length <= text.length &&
        text.startsWith(open, start - open.length) && text.startsWith(close, end)
    return if (already) {
        val fresh = text.removeRange(end, end + close.length).removeRange(start - open.length, start)
        TextFieldValue(fresh, TextRange(start - open.length, end - open.length))
    } else {
        val fresh = text.replaceRange(start, end, open + text.substring(start, end) + close)
        TextFieldValue(fresh, TextRange(start + open.length, end + open.length))
    }
}

private fun prefixLines(value: TextFieldValue, prefix: String): TextFieldValue {
    val text = value.text
    val start = text.lastIndexOf('\n', (value.selection.min - 1).coerceIn(0, text.length))
        .let { if (it < 0) 0 else it + 1 }
    val end = text.indexOf('\n', value.selection.max.coerceIn(0, text.length))
        .let { if (it < 0) text.length else it }
    val lines = text.substring(start, end).split('\n')
    val on = lines.all { it.startsWith(prefix) }
    val fresh = lines.joinToString("\n") { if (on) it.removePrefix(prefix) else prefix + it }
    return TextFieldValue(text.replaceRange(start, end, fresh), TextRange(start, start + fresh.length))
}

// ---- 预览：够看就行，块级 + 图片，行内样式不渲染 ----

private val ImageLine = Regex("""^!\[[^]]*]\(([^)]+)\)$""")
private val Markers = Regex("""\*\*|~~|`""")

@Composable
private fun MarkdownPreview(text: String, modifier: Modifier) {
    val context = LocalContext.current
    Column(modifier.verticalScroll(rememberScrollState()).padding(horizontal = 24.dp, vertical = 8.dp)) {
        for (line in text.lines()) {
            val image = ImageLine.find(line.trim())?.groupValues?.get(1)
            when {
                image != null -> {
                    val file = Attach.file(context, image)
                    val bitmap = remember(file.path) {
                        if (file.exists()) BitmapFactory.decodeFile(file.path)?.asImageBitmap() else null
                    }
                    if (bitmap != null) {
                        Image(
                            bitmap = bitmap,
                            contentDescription = null,
                            contentScale = ContentScale.FillWidth,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = Space.xs)
                                .clip(RoundedCornerShape(Radius.card)),
                        )
                    } else {
                        Text(
                            text = image,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.outline,
                        )
                    }
                }

                line.isBlank() -> Spacer(Modifier.height(Space.s))

                line.trim() == "---" -> HorizontalDivider(Modifier.padding(vertical = Space.s))

                line.startsWith("#") -> Text(
                    text = line.trimStart('#').trim(),
                    fontSize = 20.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(top = Space.s, bottom = Space.xxs),
                )

                line.startsWith("> ") -> Text(
                    text = line.removePrefix("> ").replace(Markers, ""),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = Space.s),
                )

                line.startsWith("- [ ] ") -> Row {
                    Text("☐ ", style = MaterialTheme.typography.bodyMedium)
                    Text(line.removePrefix("- [ ] ").replace(Markers, ""), style = MaterialTheme.typography.bodyMedium)
                }

                line.startsWith("- [x] ") -> Row {
                    Text("☑ ", style = MaterialTheme.typography.bodyMedium)
                    Text(line.removePrefix("- [x] ").replace(Markers, ""), style = MaterialTheme.typography.bodyMedium)
                }

                line.startsWith("- ") || line.startsWith("* ") -> Row {
                    Text("• ", style = MaterialTheme.typography.bodyMedium)
                    Text(line.drop(2).replace(Markers, ""), style = MaterialTheme.typography.bodyMedium)
                }

                else -> Text(
                    text = line.replace(Markers, ""),
                    style = MaterialTheme.typography.bodyMedium.copy(lineHeight = 24.sp),
                )
            }
        }
        Spacer(Modifier.height(Space.xl))
    }
}
