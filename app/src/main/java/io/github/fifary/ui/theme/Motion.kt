package io.github.fifary.ui.theme

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring

// 手感参数集中在这一个文件，调手感只改这里（设计文档 §8.2）
object Motion {
    val EnterEasing = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1.0f)
    val ExitEasing = CubicBezierEasing(0.3f, 0.0f, 0.8f, 0.15f)

    val PressSpring = spring<Float>(dampingRatio = 0.55f, stiffness = 700f)
    val PageSpring = spring<Float>(dampingRatio = 0.90f, stiffness = 380f)
    // 顶栏返回：比手势手感更快一点，收尾不拖泥带水
    val BackSpring = spring<Float>(dampingRatio = 0.95f, stiffness = 620f)
    // 「+」气泡长成整页：起飞快、收尾稳，别弹过头
    val BubbleSpring = spring<Float>(dampingRatio = 0.86f, stiffness = 420f)
    val FaidSpring = spring<Float>(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMediumLow)

    const val D_PAGE_IN = 300
    const val D_PAGE_OUT = 250
    const val D_FADE = 220
    const val D_SHEET = 280
    const val D_ITEM_MOVE = 230

    const val ITEM_STAGGER_MS = 25L
    const val AUTOSAVE_DEBOUNCE_MS = 800L
}
