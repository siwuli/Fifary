package io.github.fifary.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import io.github.fifary.ui.theme.Motion

// 按压回弹（§8.3 #1/#2/#3）：按下 0.97，长按 0.94 后弹菜单
// pointerInput 用 Unit 做 key + rememberUpdatedState 存回调，
// 否则每次重组都会重建手势检测器（列表滚动时的常见性能坑）
fun Modifier.springPressable(
    onLongPress: (() -> Unit)? = null,
    onClick: () -> Unit,
): Modifier = composed {
    val haptics = rememberHaptics()
    val currentClick by rememberUpdatedState(onClick)
    val currentLongPress by rememberUpdatedState(onLongPress)
    var pressed by remember { mutableStateOf(false) }
    var longPressed by remember { mutableStateOf(false) }
    val target = when {
        longPressed -> 0.94f
        pressed -> 0.97f
        else -> 1f
    }
    val scale by animateFloatAsState(target, Motion.PressSpring, label = "pressScale")

    this
        .graphicsLayer {
            // 果冻不等比缩：竖着压扁、横着鼓出来，弹回时才有胶体的形变
            scaleX = 1f + (1f - scale) * 1.2f
            scaleY = scale
        }
        .pointerInput(Unit) {
            detectTapGestures(
                onPress = {
                    pressed = true
                    tryAwaitRelease()
                    pressed = false
                    longPressed = false
                },
                onLongPress = {
                    val action = currentLongPress
                    if (action != null) {
                        longPressed = true
                        haptics.longPress()
                        action()
                    }
                },
                onTap = {
                    currentClick()
                },
            )
        }
}
