package io.github.fifary.ui.components

import android.content.Context
import android.content.ContextWrapper
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import io.github.fifary.data.LockMode
import io.github.fifary.data.PrivateVault
import io.github.fifary.data.Secret
import io.github.fifary.data.SettingsStore
import io.github.fifary.ui.S
import io.github.fifary.ui.theme.Radius
import io.github.fifary.ui.theme.Space

// 私密锁的输入全在屏幕下方的一张薄片上：标题一行、进度点、下面就是键盘。
// 数字键盘和九点图形最后都只是同一个密码字符串，底层加密不用区分。

private val KeySize = 68.dp

// 整张薄片的骨架：标题 / 错误提示 / 内容
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LockSheetShell(
    title: String,
    error: String?,
    onDismiss: () -> Unit,
    action: (@Composable () -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(topStart = Radius.sheet, topEnd = Radius.sheet),
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .imePadding()
                .padding(bottom = Space.m),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = Space.l),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f),
                )
                action?.invoke()
                TextButton(onClick = onDismiss) { Text(S("取消")) }
            }
            if (error != null) {
                Text(
                    text = error,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(top = Space.xs),
                )
            }
            content()
        }
    }
}

// 输入进度：pinLen 未知时（设置阶段）就给一个最低位数
@Composable
private fun Progress(typed: Int, pinLen: Int) {
    Row(
        Modifier.padding(vertical = Space.m),
        horizontalArrangement = Arrangement.spacedBy(Space.s),
    ) {
        repeat(if (pinLen > 0) pinLen else typed.coerceAtLeast(4)) { i ->
            Box(
                Modifier
                    .size(11.dp)
                    .background(
                        if (i < typed) {
                            MaterialTheme.colorScheme.onSurface
                        } else {
                            MaterialTheme.colorScheme.surfaceVariant
                        },
                        CircleShape,
                    ),
            )
        }
    }
}

@Composable
private fun Key(label: String, onClick: () -> Unit) {
    Box(
        Modifier
            .size(KeySize)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .springPressable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

@Composable
private fun Keypad(value: String, digits: Int, onChange: (String) -> Unit, onSubmit: () -> Unit) {
    val submit by rememberUpdatedState(onSubmit)
    LaunchedEffect(value, digits) { if (digits > 0 && value.length == digits) submit() }
    Column(verticalArrangement = Arrangement.spacedBy(Space.xs)) {
        listOf(
            listOf("1", "2", "3"),
            listOf("4", "5", "6"),
            listOf("7", "8", "9"),
        ).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(Space.m)) {
                row.forEach { key ->
                    Key(key) { if (digits <= 0 || value.length < digits) onChange(value + key) }
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(Space.m)) {
            Spacer(Modifier.size(KeySize))
            Key("0") { if (digits <= 0 || value.length < digits) onChange(value + "0") }
            Box(
                Modifier
                    .size(KeySize)
                    .clip(CircleShape)
                    .springPressable(onClick = { onChange(value.dropLast(1)) }),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.Backspace,
                    contentDescription = S("删除"),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(22.dp),
                )
            }
        }
    }
}

// 九点图形：手指离开才算一条（够 4 个点才收），路径就是号码串
@Composable
fun PatternPad(onRelease: (String) -> Unit) {
    var cell by remember { mutableStateOf(0f) }
    var path by remember { mutableStateOf("") }
    val release by rememberUpdatedState(onRelease)
    val onColor = MaterialTheme.colorScheme.onSurface
    val offColor = MaterialTheme.colorScheme.surfaceVariant

    Canvas(
        Modifier
            .padding(vertical = Space.m)
            .fillMaxWidth(0.72f)
            .aspectRatio(1f)
            .onSizeChanged { cell = it.width / 3f }
            .pointerInput(Unit) {
                fun hit(p: Offset): Int? {
                    if (cell <= 0f) return null
                    val column = (p.x / cell).toInt().coerceIn(0, 2)
                    val row = (p.y / cell).toInt().coerceIn(0, 2)
                    val center = Offset((column + 0.5f) * cell, (row + 0.5f) * cell)
                    // 手指得落在点附近才算连上，不然划一条直线会顺手点亮中间的点
                    return if ((p - center).getDistance() <= cell * 0.38f) row * 3 + column else null
                }
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    path = ""
                    var last = -1
                    hit(down.position)?.let { last = it; path = "${it + 1}" }
                    while (true) {
                        val change = awaitPointerEvent().changes.firstOrNull { it.id == down.id } ?: break
                        if (!change.pressed) break
                        change.consume()
                        hit(change.position)?.let {
                            if (it != last) {
                                last = it
                                val dot = "${it + 1}"
                                if (!path.contains(dot)) path += dot
                            }
                        }
                    }
                    val done = path
                    path = ""
                    release(done)
                }
            },
    ) {
        val radius = cell * 0.115f
        val points = path.map { dot ->
            val i = dot - '1'
            Offset((i % 3 + 0.5f) * cell, (i / 3 + 0.5f) * cell)
        }
        for (k in 1 until points.size) {
            drawLine(onColor, points[k - 1], points[k], strokeWidth = cell * 0.028f)
        }
        for (i in 0..8) {
            val center = Offset((i % 3 + 0.5f) * cell, (i / 3 + 0.5f) * cell)
            val used = path.contains("${i + 1}")
            drawCircle(
                color = if (used) onColor else offColor,
                radius = if (used) radius * 1.35f else radius,
                center = center,
            )
        }
    }
}

// 老密码是文本的用户：换锁之前还得能进得去，所以只在这种情况下出输入框
@Composable
private fun TextField(value: String, onChange: (String) -> Unit) {
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier.fillMaxWidth().padding(horizontal = Space.l).padding(top = Space.m),
    ) {
        Box(Modifier.padding(horizontal = Space.m, vertical = Space.s)) {
            if (value.isEmpty()) {
                Text(
                    text = S("密码"),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.outline,
                )
            }
            BasicTextField(
                value = value,
                onValueChange = onChange,
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth().focusRequester(focus),
                textStyle = MaterialTheme.typography.bodyMedium.copy(
                    color = MaterialTheme.colorScheme.onSurface,
                ),
            )
        }
    }
}

// 解锁：进私密页就弹这一张，指纹先试，不成再用下面的键盘
@Composable
fun LockSheet(
    mode: LockMode,
    pinLen: Int,
    error: String?,
    onDismiss: () -> Unit,
    onSubmit: (String) -> Unit,
    onBiometric: (() -> Unit)? = null,
) {
    var value by remember(mode) { mutableStateOf("") }
    // 输错就清空重来，别让人一个个删
    LaunchedEffect(error) { if (error != null) value = "" }
    LockSheetShell(
        title = S("输入私密锁"),
        error = error,
        onDismiss = onDismiss,
        action = onBiometric?.let {
            { TextButton(onClick = it) { Text(S("指纹")) } }
        },
    ) {
        when (mode) {
            LockMode.TEXT -> {
                TextField(value) { value = it }
                TextButton(onClick = { onSubmit(value) }, enabled = value.isNotEmpty()) {
                    Text(S("解锁"))
                }
            }
            LockMode.PIN -> {
                Progress(value.length, pinLen)
                Keypad(value, pinLen, { value = it }) { if (value.isNotEmpty()) onSubmit(value) }
            }
            LockMode.PATTERN -> PatternPad { secret -> if (secret.isNotEmpty()) onSubmit(secret) }
        }
    }
}

// 设锁：选键盘 → 输一遍 → 再输一遍
@Composable
fun LockSetupSheet(onDismiss: () -> Unit, onDone: (String, LockMode) -> Unit) {
    var mode by remember { mutableStateOf(LockMode.PIN) }
    var value by remember { mutableStateOf("") }
    var first by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }

    fun next(secret: String) {
        if (secret.length < 4) {
            error = S(if (mode == LockMode.PATTERN) "至少连 4 个点" else "至少 4 位")
            value = ""
            return
        }
        if (first.isEmpty()) {
            first = secret
            value = ""
            error = null
            return
        }
        if (first != secret) {
            first = ""
            value = ""
            error = S("两次输入不一样，重来")
            return
        }
        onDone(secret, mode)
    }

    LockSheetShell(
        title = S(if (first.isEmpty()) "设置私密锁" else "再输一次"),
        error = error,
        onDismiss = onDismiss,
    ) {
        if (first.isEmpty()) {
            Row(
                Modifier.padding(bottom = Space.xs),
                horizontalArrangement = Arrangement.spacedBy(Space.xs),
            ) {
                ModePill(S("数字"), mode == LockMode.PIN) {
                    mode = LockMode.PIN
                    value = ""
                    error = null
                }
                ModePill(S("图形"), mode == LockMode.PATTERN) {
                    mode = LockMode.PATTERN
                    value = ""
                    error = null
                }
            }
        }
        when (mode) {
            LockMode.PATTERN -> PatternPad { next(it) }
            else -> {
                Progress(value.length, if (first.isEmpty()) 0 else first.length)
                Keypad(value, if (first.isEmpty()) 0 else first.length, { value = it }, { next(value) })
                if (first.isEmpty()) {
                    TextButton(onClick = { next(value) }, enabled = value.length >= 4) {
                        Text(S("下一步"))
                    }
                }
            }
        }
    }
}

@Composable
private fun ModePill(text: String, selected: Boolean, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
        contentColor = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.height(34.dp),
    ) {
        Row(Modifier.padding(horizontal = Space.m), verticalAlignment = Alignment.CenterVertically) {
            Text(text, style = MaterialTheme.typography.labelMedium)
        }
    }
}

// ---- 指纹 ----

fun bioAvailable(context: Context): Boolean =
    BiometricManager.from(context)
        .canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG) == BiometricManager.BIOMETRIC_SUCCESS

// 系统指纹过了才解开 Keystore 那一层；onFail 的 null = 用户自己退回，不用报错
fun bioUnlock(context: Context, onOk: () -> Unit, onFail: (String?) -> Unit) {
    val blob = SettingsStore.secretBio ?: return onFail(null)
    biometric(context, onFail) {
        val dataKey = Secret.bioUnwrap(blob)
        if (dataKey == null) {
            // 钥匙被系统换掉了（重新录指纹 / 恢复出厂）：这一层作废，退回键盘。
            // 后面括号里是自检结果，出错时靠它定位
            val detail = Secret.bioSelfCheck()
            PrivateVault.forgetBiometric()
            onFail("${S("指纹没解开，用下面的密码进")} · $detail")
        } else {
            PrivateVault.adopt(dataKey)
            onOk()
        }
    }
}

fun bioEnroll(context: Context, onOk: () -> Unit, onFail: (String?) -> Unit) {
    val dataKey = PrivateVault.key ?: return onFail(null)
    biometric(context, onFail) {
        val blob = Secret.bioWrap(dataKey)
        if (blob == null) {
            onFail(S("指纹没开成"))
        } else {
            SettingsStore.updateSecretBio(blob)
            onOk()
        }
    }
}

// 只负责「验一次指纹」，验过了回调里再加解密
private fun biometric(context: Context, onFail: (String?) -> Unit, onOk: () -> Unit) {
    val activity = context.fragmentActivity() ?: return onFail(S("这台设备没有指纹"))
    BiometricPrompt(
        activity,
        ContextCompat.getMainExecutor(activity),
        object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) = onOk()

            override fun onAuthenticationError(code: Int, message: CharSequence) {
                onFail(if (cancelled(code)) null else message.toString())
            }
        },
    ).authenticate(promptInfo())
}

private fun promptInfo(): BiometricPrompt.PromptInfo = BiometricPrompt.PromptInfo.Builder()
    .setTitle(S("解锁私密笔记"))
    .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_STRONG)
    .setNegativeButtonText(S("用密码"))
    .build()

// 用户自己取消不是错误，别弹提示也不要把指纹关掉
private fun cancelled(code: Int): Boolean = code == BiometricPrompt.ERROR_USER_CANCELED ||
    code == BiometricPrompt.ERROR_NEGATIVE_BUTTON ||
    code == BiometricPrompt.ERROR_CANCELED

private fun Context.fragmentActivity(): FragmentActivity? {
    var current: Context = this
    while (current is ContextWrapper) {
        if (current is FragmentActivity) return current
        current = current.baseContext
    }
    return null
}
