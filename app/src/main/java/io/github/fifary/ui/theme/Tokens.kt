package io.github.fifary.ui.theme

import androidx.compose.ui.unit.dp

// 设计基准：间距 / 圆角 / 高度。所有页面只用这里的刻度，不写魔法数字。
object Space {
    val xxs = 4.dp
    val xs = 8.dp
    val s = 12.dp
    val m = 16.dp
    val l = 20.dp
    val xl = 28.dp
}

object Radius {
    val chip = 22.dp
    val card = 22.dp
    val sheet = 28.dp
    val check = 7.dp
}

object Size {
    val touch = 44.dp
    // 分类胶囊做扁：参考图的胶囊是"扁长"的，不要圆滚滚
    val chipHeight = 34.dp
    val categoryButton = 38.dp
    val gridCardMin = 168.dp
    val check = 20.dp
}

object Elev {
    val floating = 8.dp
    val chip = 2.dp
}
