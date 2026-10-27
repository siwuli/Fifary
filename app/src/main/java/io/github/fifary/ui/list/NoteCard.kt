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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.fifary.data.Note
import io.github.fifary.ui.components.SelectCheck
import io.github.fifary.ui.components.springPressable
import io.github.fifary.ui.theme.Motion
import io.github.fifary.ui.theme.Radius
import io.github.fifary.ui.theme.Size
import io.github.fifary.ui.theme.Space
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private const val STAGGER_LIMIT = 12
private val DateFormat = SimpleDateFormat("yyyy/MM/dd", Locale.getDefault())

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
        color = if (selected) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            MaterialTheme.colorScheme.surface
        },
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
                        if (note.pinned) {
                            Box(
                                Modifier
                                    .size(6.dp)
                                    .background(MaterialTheme.colorScheme.primary, CircleShape)
                            )
                            Spacer(Modifier.width(6.dp))
                        }
                        Text(
                            text = note.name,
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = if (grid) 2 else 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }

                    if (!grid) {
                        val preview = previewOf(note.body)
                        if (preview.isNotEmpty()) {
                            Text(
                                text = "${DateFormat.format(Date(note.updatedAt))}  $preview",
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
                val preview = previewOf(note.body)
                if (preview.isNotEmpty()) {
                    Text(
                        text = preview,
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
                    text = DateFormat.format(Date(note.updatedAt)),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline,
                    modifier = Modifier.padding(top = Space.xs),
                )
            }
        }
    }
}

// 卡片预览去掉 Markdown 噪音（井号、引用符、列表符、代码围栏）
internal fun previewOf(body: String): String =
    body.lineSequence()
        .map { it.trim() }
        .filter { it.isNotEmpty() && !it.startsWith("```") }
        .map { it.trimStart('#', '>', '-', '*', ' ', '\t') }
        .filter { it.isNotEmpty() }
        .joinToString(" ")
        .take(160)
