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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.platform.LocalContext
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
import io.github.fifary.ui.theme.Size
import io.github.fifary.ui.theme.Space

// 私密锁的输入面板：数字键盘、九点图形、文本密码。三种都只是把同一个密码字符串输进去。
// 图形密码存的就是点位号码串（1-9），所以换方式等于换密码，底层加密一个字都不用改。

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
            .fillMaxWidth()
            .height(264.dp)
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
        val radius = cell * 0.13f
        val points = path.map { dot ->
            val i = dot - '1'
            Offset((i % 3 + 0.5f) * cell, (i / 3 + 0.5f) * cell)
        }
        for (k in 1 until points.size) {
            drawLine(onColor, points[k - 1], points[k], strokeWidth = cell * 0.03f)
        }
        for (i in 0..8) {
            val center = Offset((i % 3 + 0.5f) * cell, (i / 3 + 0.5f) * cell)
            val used = path.contains("${i + 1}")
            drawCircle(
                color = if (used) onColor else offColor,
                radius = if (used) radius * 1.3f else radius,
                center = center,
            )
        }
    }
}

// 数字键盘：digits > 0 时数满就自动交（解锁用），= 0 时由对话框的按钮交（设置时还不知道几位）
@Composable
fun PinPad(value: String, digits: Int, onChange: (String) -> Unit, onSubmit: () -> Unit) {
    val submit by rememberUpdatedState(onSubmit)
    LaunchedEffect(value, digits) { if (digits > 0 && value.length == digits) submit() }
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Row(
            Modifier.padding(bottom = Space.xs),
            horizontalArrangement = Arrangement.spacedBy(Space.xs),
        ) {
            repeat(if (digits > 0) digits else value.length.coerceAtLeast(4)) { i ->
                Box(
                    Modifier
                        .size(9.dp)
                        .background(
                            if (i < value.length) {
                                MaterialTheme.colorScheme.onSurface
                            } else {
                                MaterialTheme.colorScheme.surfaceVariant
                            },
                            CircleShape,
                        ),
                )
            }
        }
        val keys = listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "", "0", "⌫")
        keys.chunked(3).forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                row.forEach { key ->
                    Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                        when (key) {
                            "" -> Box(Modifier.height(Size.touch))
                            "⌫" -> PadKey("⌫") { onChange(value.dropLast(1)) }
                            else -> PadKey(key) { if (digits <= 0 || value.length < digits) onChange(value + key) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PadKey(label: String, onClick: () -> Unit) {
    Box(
        Modifier
            .padding(Space.xxs)
            .fillMaxWidth()
            .height(Size.touch)
            .clip(CircleShape)
            .springPressable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, style = MaterialTheme.typography.headlineSmall)
    }
}

@Composable
private fun SecretField(value: String, onChange: (String) -> Unit) {
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier.fillMaxWidth(),
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

@Composable
private fun ErrorLine(error: String?) {
    if (error != null) {
        Text(
            text = error,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.error,
            modifier = Modifier.padding(bottom = Space.xs),
        )
    }
}

// onDone 带上「刚输完的那串」：图形密码是一次拖出来的，不能等重组后再从 value 里取（会取到上一帧）
@Composable
private fun LockBody(
    mode: LockMode,
    value: String,
    onChange: (String) -> Unit,
    digits: Int,
    onDone: (String) -> Unit,
) {
    when (mode) {
        LockMode.TEXT -> SecretField(value, onChange)
        LockMode.PIN -> PinPad(value, digits, onChange) { onDone(value) }
        LockMode.PATTERN -> PatternPad { onDone(it) }
    }
}

// 解锁 / 改密码时输一遍
@Composable
fun LockEntry(
    title: String,
    mode: LockMode,
    digits: Int,
    error: String?,
    onDismiss: () -> Unit,
    onSubmit: (String) -> Unit,
) {
    var value by remember(mode) { mutableStateOf("") }
    // 输错就清空重来，别让人一个个删
    LaunchedEffect(error) { if (error != null) value = "" }
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(Radius.sheet),
        containerColor = MaterialTheme.colorScheme.surface,
        titleContentColor = MaterialTheme.colorScheme.onSurface,
        title = { Text(title, style = MaterialTheme.typography.titleMedium) },
        text = {
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                ErrorLine(error)
                LockBody(mode, value, { value = it }, digits) { secret ->
                    if (secret.isNotEmpty()) onSubmit(secret)
                }
            }
        },
        confirmButton = {
            if (mode == LockMode.TEXT) {
                TextButton(onClick = { onSubmit(value) }, enabled = value.isNotEmpty()) {
                    Text(S("解锁"))
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(S("取消")) }
        },
    )
}

// 设锁：选方式 → 输一遍 → 再输一遍（文本密码照旧只输一遍）
@Composable
fun LockSetup(onDismiss: () -> Unit, onDone: (String, LockMode) -> Unit) {
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
        if (mode == LockMode.TEXT) return onDone(secret, mode)
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

    val digits = when {
        mode != LockMode.PIN -> 0
        first.isEmpty() -> 0
        else -> first.length
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(Radius.sheet),
        containerColor = MaterialTheme.colorScheme.surface,
        titleContentColor = MaterialTheme.colorScheme.onSurface,
        title = {
            Text(
                text = S(if (first.isEmpty()) "设置私密锁" else "再输一次"),
                style = MaterialTheme.typography.titleMedium,
            )
        },
        text = {
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                if (first.isEmpty()) {
                    Row(
                        Modifier.padding(bottom = Space.m),
                        horizontalArrangement = Arrangement.spacedBy(Space.xs),
                    ) {
                        listOf(
                            LockMode.PIN to "数字",
                            LockMode.PATTERN to "图形",
                            LockMode.TEXT to "密码",
                        ).forEach { (kind, label) ->
                            ModePill(S(label), mode == kind) {
                                mode = kind
                                value = ""
                                first = ""
                                error = null
                            }
                        }
                    }
                }
                ErrorLine(error)
                LockBody(mode, value, { value = it }, digits) { next(it) }
            }
        },
        confirmButton = {
            TextButton(onClick = { next(value) }, enabled = value.length >= 4) {
                Text(S(if (first.isEmpty()) "下一步" else "保存"))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(S("取消")) }
        },
    )
}

@Composable
private fun ModePill(text: String, selected: Boolean, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
        contentColor = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.height(Size.chipHeight),
    ) {
        Row(Modifier.padding(horizontal = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(text, style = MaterialTheme.typography.labelMedium)
        }
    }
}

// ---- 指纹 ----

fun bioAvailable(context: Context): Boolean =
    BiometricManager.from(context)
        .canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG) == BiometricManager.BIOMETRIC_SUCCESS

// 用指纹解开 Keystore 那一层，把数据密钥交给 vault
fun bioUnlock(context: Context, onOk: () -> Unit, onFail: (String) -> Unit) {
    val activity = context.fragmentActivity() ?: return
    val blob = SettingsStore.secretBio ?: return
    val cipher = Secret.bioDecrypt(blob) ?: run {
        // 指纹被重新录入 / 系统换了密钥：这一层作废，退回密码
        PrivateVault.forgetBiometric()
        onFail(S("指纹变了，先输密码解锁"))
        return
    }
    BiometricPrompt(
        activity,
        ContextCompat.getMainExecutor(activity),
        object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                val dataKey = result.cryptoObject?.cipher?.let { Secret.bioUnwrap(it, blob) }
                if (dataKey == null) {
                    onFail(S("指纹没解开，先输密码解锁"))
                } else {
                    PrivateVault.adopt(dataKey)
                    onOk()
                }
            }

            override fun onAuthenticationError(code: Int, message: CharSequence) {
                if (!cancelled(code)) onFail(message.toString())
            }
        },
    ).authenticate(promptInfo(), BiometricPrompt.CryptoObject(cipher))
}

// 开启指纹：先让系统认证一次，再拿那把 Keystore 密钥把数据密钥包起来存
fun bioEnroll(context: Context, onOk: () -> Unit, onFail: (String) -> Unit) {
    val activity = context.fragmentActivity() ?: return onFail(S("这台设备没有指纹"))
    val dataKey = PrivateVault.key ?: return onFail(S("先解锁私密笔记"))
    val cipher = runCatching { Secret.bioEncrypt() }.getOrNull() ?: return onFail(S("这台设备没有指纹"))
    BiometricPrompt(
        activity,
        ContextCompat.getMainExecutor(activity),
        object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                val c = result.cryptoObject?.cipher
                val blob = c?.let { runCatching { Secret.bioWrap(it, dataKey) }.getOrNull() }
                if (blob == null) {
                    onFail(S("指纹没开成"))
                } else {
                    SettingsStore.updateSecretBio(blob)
                    onOk()
                }
            }

            override fun onAuthenticationError(code: Int, message: CharSequence) {
                if (!cancelled(code)) onFail(message.toString())
            }
        },
    ).authenticate(promptInfo(), BiometricPrompt.CryptoObject(cipher))
}

private fun promptInfo(): BiometricPrompt.PromptInfo = BiometricPrompt.PromptInfo.Builder()
    .setTitle(S("解锁私密笔记"))
    .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_STRONG)
    .setNegativeButtonText(S("用密码"))
    .build()

// 用户自己取消不是错误，别弹 toast 也不要把指纹关掉
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
