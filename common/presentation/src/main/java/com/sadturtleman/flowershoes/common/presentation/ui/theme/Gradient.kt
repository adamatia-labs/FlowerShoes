package com.sadturtleman.flowershoes.common.presentation.ui.theme

import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

/**
 * Figma(CSS)의 각도 기반 선형 그라디언트를 그대로 그린다.
 *
 * CSS 는 0deg 가 위쪽이고 시계 방향으로 증가하므로 진행 방향은 `(sin θ, -cos θ)` 이고,
 * 화면 좌표계는 y 가 아래로 증가하므로 그대로 쓸 수 있다.
 * 그라디언트 선은 요소 중심을 지나며 길이는 `|w·sin θ| + |h·cos θ|` 이다.
 *
 * 크기를 알아야 각도를 오프셋으로 바꿀 수 있어 [drawWithCache] 안에서 계산한다.
 * 크기가 바뀔 때만 브러시를 다시 만든다.
 *
 * @param angleDegrees Figma dev mode 가 알려주는 `linear-gradient(<각도>deg, ...)` 값
 * @param colorStops 위치(0f~1f)와 색. CSS 의 `%` 를 100 으로 나눈 값이다.
 */
fun Modifier.flowerLinearGradient(
    angleDegrees: Float,
    vararg colorStops: Pair<Float, Color>,
): Modifier = drawWithCache {
    val radians = Math.toRadians(angleDegrees.toDouble())
    val dx = sin(radians).toFloat()
    val dy = -cos(radians).toFloat()
    val lineLength = abs(size.width * dx) + abs(size.height * dy)
    val halfX = dx * lineLength / 2f
    val halfY = dy * lineLength / 2f
    val centerX = size.width / 2f
    val centerY = size.height / 2f

    val brush = Brush.linearGradient(
        colorStops = colorStops,
        start = Offset(centerX - halfX, centerY - halfY),
        end = Offset(centerX + halfX, centerY + halfY),
    )
    onDrawBehind { drawRect(brush) }
}
