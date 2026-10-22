package com.sadturtleman.flowershoes.common.presentation.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * 그림자 스케일 (Figma: 01 · Foundations → Elevation).
 *
 * Figma 는 각 단계를 offset/blur/spread 가 다른 2겹 드롭섀도로 정의하지만
 * Compose 의 [Modifier.shadow] 는 단일 elevation 값만 받는다.
 * 아래 dp 는 원본의 주 그림자 blur/offset 에 맞춘 근사값이고,
 * 그림자 색은 원본과 같은 먹색(#1F1A17) 계열을 [FlowerElevation.shadowColor] 로 유지한다.
 *
 * | 단계 | Figma 주 그림자           | 용도                |
 * |------|---------------------------|---------------------|
 * | 1    | y1 blur3 (+ y0 blur1)     | 카드 · 칩           |
 * | 2    | y4 blur12 spread-2        | 플로팅 버튼 · 팝오버 |
 * | 3    | y12 blur28 spread-6       | 바텀시트 · 모달     |
 */
@Immutable
data class FlowerElevation(
    /** 카드 · 칩 */
    val level1: Dp = 2.dp,
    /** 플로팅 버튼 · 팝오버 */
    val level2: Dp = 8.dp,
    /** 바텀시트 · 모달 */
    val level3: Dp = 16.dp,
    /** Figma Elevation 토큰이 쓰는 먹색 그림자 */
    val shadowColor: Color = Color(0xFF1F1A17),
)

/**
 * 디자인 시스템 그림자를 적용한다. 색상까지 토큰에 맞추기 위해
 * [Modifier.shadow] 대신 이 확장을 쓴다.
 */
@Composable
fun Modifier.flowerShadow(
    elevation: Dp,
    shape: Shape,
): Modifier {
    if (elevation <= 0.dp) return this
    val color = FlowerTheme.elevation.shadowColor
    return shadow(
        elevation = elevation,
        shape = shape,
        clip = false,
        ambientColor = color,
        spotColor = color,
    )
}
