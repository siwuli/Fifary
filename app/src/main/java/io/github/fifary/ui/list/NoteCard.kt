package io.github.fifary.ui.list

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.fifary.data.Note
import io.github.fifary.data.NotesStore
import io.github.fifary.ui.components.SelectCheck
import io.github.fifary.ui.components.springPressable
import io.github.fifary.ui.theme.Motion
import io.github.fifary.ui.theme.Radius
import io.github.fifary.ui.theme.Size
import io.github.fifary.ui.theme.Space
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

private const val STAGGER_LIMIT = 12
private val HmFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
private val DayFormat = SimpleDateFormat("yyyy年M月d日 EEEE", Locale.CHINA)
private val DateFormat = SimpleDateFormat("yyyy年M月d日", Locale.CHINA)
private const val MINUTE = 60_000L

// 卡片上的时间是「现在 - 最后修改」的差；列表里有个 20 秒走一次的钟推着它重新算
internal var clockNow by mutableStateOf(System.currentTimeMillis())

// 说法照参考便签（strings 里就是 %d 分钟前 / 今天 %s / 昨天 %s / yyyy年M月d日 EEEE）：
// 1 分钟内「刚刚」，1 小时内「N 分钟前」，今天/昨天带时间，再老给年月日 + 星期
internal fun timeLabel(at: Long): String {
    val now = clockNow
    val diff = now - at
    return when {
        diff < MINUTE -> "刚刚"
        diff < 60 * MINUTE -> "${diff / MINUTE} 分钟前"
        sameDay(now, at) -> "今天 ${HmFormat.format(Date(at))}"
        sameDay(now - 24 * 60 * MINUTE, at) -> "昨天 ${HmFormat.format(Date(at))}"
        else -> DayFormat.format(Date(at))
    }
}

private fun sameDay(a: Long, b: Long): Boolean {
    val ca = Calendar.getInstance().apply { timeInMillis = a }
    val cb = Calendar.getInstance().apply { timeInMillis = b }
    return ca.get(Calendar.YEAR) == cb.get(Calendar.YEAR) &&
        ca.get(Calendar.DAY_OF_YEAR) == cb.get(Calendar.DAY_OF_YEAR)
}

@Composable
fun NoteCard(
    note: Note,
    grid: Boolean,
    selectionMode: Boolean,
    selected: Boolean,
    index: Int,
    animateIn: Boolean,
    onOpen: () -> Unit,
    onLongPress: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // 入场错峰只在首次进入 App 时做一次：切分类时再逐项延迟会显得"反应慢"
    var appeared by remember(note.path) { mutableStateOf(!animateIn || index >= STAGGER_LIMIT) }
    if (animateIn && index < STAGGER_LIMIT) {
        LaunchedEffect(note.path) {
            delay(index * Motion.ITEM_STAGGER_MS)
            appeared = true
        }
    }
    val appear by animateFloatAsState(
        targetValue = if (appeared) 1f else 0f,
        animationSpec = Motion.FaidSpring,
        label = "appear",
    )

    val (head, rest) = headingOf(note)

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .then(if (grid) Modifier.height(Size.gridCardMin) else Modifier)
            .springPressable(onLongPress = onLongPress, onClick = onOpen)
            .graphicsLayer {
                alpha = appear
                translationY = (1f - appear) * 12.dp.toPx()
            },
        shape = RoundedCornerShape(Radius.card),
        color = MaterialTheme.colorScheme.surface,
    ) {
        Column(
            if (grid) {
                Modifier.fillMaxSize().padding(Space.l)
            } else {
                Modifier.fillMaxWidth().padding(Space.l)
            }
        ) {
            Row(verticalAlignment = Alignment.Top) {
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = head,
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Medium),
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = if (grid) 2 else 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }

                    if (!grid) {
                        val line = if (note.deletedAt > 0L) {
                            "删除于 ${DateFormat.format(Date(note.deletedAt))}"
                        } else {
                            "${timeLabel(note.updatedAt)}  $rest"
                        }
                        if (line.isNotEmpty()) {
                            Text(
                                text = line,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.padding(top = Space.xs),
                            )
                        }
                    }
                }
                if (selectionMode) {
                    Spacer(Modifier.width(Space.xs))
                    SelectCheck(selected)
                }
            }

            if (grid) {
                if (rest.isNotEmpty()) {
                    Text(
                        text = rest,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                        // fill = false：内容少时不吃多余空间，内容多时被裁掉而不是压到日期上
                        modifier = Modifier
                            .padding(top = Space.xs)
                            .weight(1f, fill = false),
                    )
                } else {
                    Spacer(Modifier.weight(1f))
                }
                Text(
                    text = timeLabel(note.updatedAt),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.outline,
                    modifier = Modifier.padding(top = Space.xs),
                )
            }
        }
    }
}

// 卡片预览去掉 Markdown 噪音（井号、引用符、列表符、代码围栏、内嵌 HTML 标签），
// 图片和链接只留文字，文件地址不能露在预览里
internal fun noteLines(body: String): List<String> =
    body.lineSequence()
        .map { it.trim() }
        .filter { it.isNotEmpty() && !it.startsWith("```") }
        .map { TAG.replace(it, "") }
        .map { IMG.replace(it, "") }
        .map { LINK.replace(it, "$1") }
        .map { LEAD.replace(it, "") }
        .map { BOX.replace(it, "") }
        .map { STRIKE.replace(it, "$1") }
        .map { BOLD.replace(it, "$1") }
        .map { ITAL.replace(it, "$1") }
        .map { CODE.replace(it, "$1") }
        .map { NOISE.replace(it, "") }
        .map { it.trim() }
        .filter { it.isNotEmpty() && it != "---" }
        .toList()

// 笔记的名字就是正文第一行（参考便签没有单独的标题），预览从第二行接着
internal fun titleOf(body: String): String = noteLines(body).firstOrNull().orEmpty()

internal fun headingOf(note: Note): Pair<String, String> {
    val lines = noteLines(note.body)
    if (NotesStore.isUntitled(note.name)) {
        if (lines.isEmpty()) return note.name to ""
        return lines.first() to joinPreview(lines.drop(1))
    }
    val rest = if (lines.firstOrNull() == note.name) lines.drop(1) else lines
    return note.name to joinPreview(rest)
}

private fun joinPreview(lines: List<String>): String = lines.joinToString(" ").take(160)

private val TAG = Regex("<[^>]+>")
private val IMG = Regex("!\\[[^\\]]*\\]\\([^)]*\\)")
private val LINK = Regex("\\[([^\\]]+)\\]\\([^)]*\\)")
private val LEAD = Regex("^(?:[#>]+\\s*|[-*+]\\s+|\\d+\\.\\s+)+")
private val BOX = Regex("^\\[[ xX]\\]\\s*")
private val STRIKE = Regex("~~([^~]+)~~")
private val BOLD = Regex("\\*\\*([^*]+)\\*\\*")
private val ITAL = Regex("\\*([^*]+)\\*")
private val CODE = Regex("`([^`]+)`")
private val NOISE = Regex("\\*+|~~|`|\\|")
