package io.github.fifary.ui.components

import io.github.fifary.ui.S
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import io.github.fifary.data.PrivateVault
import io.github.fifary.ui.theme.Elev
import io.github.fifary.ui.theme.Radius
import io.github.fifary.ui.theme.Space

// 输入框：新建/重命名分组、设密码都用这一个。圆角用中等圆角，不是圆的。
@Composable
fun InputDialog(
    title: String,
    placeholder: String,
    confirmText: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
    initial: String = "",
    masked: Boolean = false,
    note: String? = null,
) {
    var value by remember { mutableStateOf(initial) }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(Radius.sheet),
        containerColor = MaterialTheme.colorScheme.surface,
        titleContentColor = MaterialTheme.colorScheme.onSurface,
        textContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        title = { Text(title, style = MaterialTheme.typography.titleMedium) },
        text = {
            Column {
                if (note != null) {
                    Text(
                        text = note,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = Space.s),
                    )
                }
                Surface(
                    shape = MaterialTheme.shapes.medium,
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Box(Modifier.padding(horizontal = Space.m, vertical = Space.s)) {
                        if (value.isEmpty()) {
                            Text(
                                text = placeholder,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.outline,
                            )
                        }
                        BasicTextField(
                            value = value,
                            onValueChange = { value = it },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth().focusRequester(focus),
                            visualTransformation = if (masked) {
                                PasswordVisualTransformation()
                            } else {
                                VisualTransformation.None
                            },
                            textStyle = MaterialTheme.typography.bodyMedium.copy(
                                color = MaterialTheme.colorScheme.onSurface,
                            ),
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(value) }, enabled = value.isNotBlank()) {
                Text(confirmText)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(S("取消")) }
        },
    )
}

@Composable
fun ConfirmDialog(
    title: String,
    text: String,
    confirmText: String,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(Radius.sheet),
        containerColor = MaterialTheme.colorScheme.surface,
        titleContentColor = MaterialTheme.colorScheme.onSurface,
        textContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        title = { Text(title, style = MaterialTheme.typography.titleMedium) },
        text = { Text(text, style = MaterialTheme.typography.bodyMedium) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(confirmText, color = MaterialTheme.colorScheme.error)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(S("取消")) }
        },
    )
}

data class BarAction(
    val label: String,
    val onClick: () -> Unit,
    val icon: ImageVector? = null,
    val enabled: Boolean = true,
)

internal enum class PasswordAsk { SET, VERIFY }

// 需要密码的动作统一走这里：没设过先设，设了没解锁先解，解锁了直接执行
class SecretGate internal constructor() {
    internal var ask by mutableStateOf<PasswordAsk?>(null)
    private var pending by mutableStateOf<((ByteArray) -> Unit)?>(null)

    fun run(action: (ByteArray) -> Unit) {
        when {
            !PrivateVault.enabled -> {
                pending = action
                ask = PasswordAsk.SET
            }
            !PrivateVault.unlocked -> {
                pending = action
                ask = PasswordAsk.VERIFY
            }
            else -> PrivateVault.key?.let(action)
        }
    }

    internal fun submit(password: String) {
        val ok = if (ask == PasswordAsk.SET) PrivateVault.setPassword(password) else PrivateVault.unlock(password)
        if (!ok) return
        ask = null
        pending?.let { it(PrivateVault.key ?: return@let) }
        pending = null
    }

    internal fun dismiss() {
        ask = null
        pending = null
    }
}

@Composable
fun rememberSecretGate(): SecretGate {
    val gate = remember { SecretGate() }
    when (gate.ask) {
        PasswordAsk.SET -> InputDialog(
            title = S("设置私密密码"),
            placeholder = S("至少 4 位"),
            confirmText = S("保存"),
            masked = true,
            note = S("密码只存在本机，忘了就解不开，也没有找回入口。"),
            onDismiss = gate::dismiss,
            onConfirm = gate::submit,
        )
        PasswordAsk.VERIFY -> InputDialog(
            title = S("输入私密密码"),
            placeholder = S("密码"),
            confirmText = S("解锁"),
            masked = true,
            onDismiss = gate::dismiss,
            onConfirm = gate::submit,
        )
        null -> Unit
    }
    return gate
}


// 底部悬浮操作条：图标 + 文字，均匀分布，整条像一颗浮在内容上的胶囊
@Composable
fun FloatingActionBar(actions: List<BarAction>, modifier: Modifier = Modifier) {
    Bubble(
        shape = CircleShape,
        elevation = Elev.bubble,
        modifier = modifier,
    ) {
        Row(
            Modifier.padding(horizontal = Space.s, vertical = Space.xs),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            actions.forEach { action -> BarItem(action) }
        }
    }
}

@Composable
private fun BarItem(action: BarAction) {
    val tint = if (action.enabled) {
        MaterialTheme.colorScheme.onSurface
    } else {
        MaterialTheme.colorScheme.outline.copy(alpha = 0.45f)
    }
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .then(
                if (action.enabled) {
                    Modifier.springPressable(onClick = action.onClick)
                } else {
                    Modifier
                }
            )
            .padding(horizontal = Space.m, vertical = 2.dp),
    ) {
        if (action.icon != null) {
            Icon(
                imageVector = action.icon,
                contentDescription = action.label,
                tint = tint,
                modifier = Modifier.size(22.dp),
            )
        }
        Text(
            text = action.label,
            style = MaterialTheme.typography.labelSmall,
            color = tint,
            modifier = Modifier.padding(top = 3.dp),
        )
    }
}
