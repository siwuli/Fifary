package io.github.fifary.ui.components

import android.view.HapticFeedbackConstants
import android.view.View
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalView

// 只在"进入选择模式"这种长按动作上震一次，跟参考 App 一样。
// 点击、切分组、删笔记、保存都不震——开了系统触感反馈的话那些本来就有回馈，别叠。
class Haptics(private val view: View) {

    fun longPress() = view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
}

@Composable
fun rememberHaptics(): Haptics {
    val view = LocalView.current
    return remember(view) { Haptics(view) }
}
