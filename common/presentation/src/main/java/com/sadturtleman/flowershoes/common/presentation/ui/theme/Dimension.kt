package com.sadturtleman.flowershoes.common.presentation.ui.theme

import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * 4px 배수 간격 스케일 (Figma: 01 · Foundations → 03 · SPACE, RADIUS & ELEVATION).
 *
 * 화면 좌우 여백은 [lg], 카드 내부 여백은 [md], 섹션 간 간격은 [xxxl] 을 기본으로 사용한다.
 */
@Immutable
data class FlowerSpacing(
    /** 2dp. 아이콘과 라벨 사이 같은 최소 간격 */
    val xxs: Dp = 2.dp,
    /** 4dp. 칩 내부 아이콘 간격, 뱃지 사이 */
    val xs: Dp = 4.dp,
    /** 8dp. 버튼 내부 아이콘·라벨 간격 */
    val sm: Dp = 8.dp,
    /** 12dp. 카드 내부 여백 */
    val md: Dp = 12.dp,
    /** 16dp. 화면 좌우 여백 */
    val lg: Dp = 16.dp,
    /** 20dp */
    val xl: Dp = 20.dp,
    /** 24dp. Size L 버튼 좌우 여백 */
    val xxl: Dp = 24.dp,
    /** 32dp. 섹션 간 간격 */
    val xxxl: Dp = 32.dp,
    /** 40dp */
    val xxxxl: Dp = 40.dp,
    /** 48dp */
    val xxxxxl: Dp = 48.dp,
    /** 64dp */
    val xxxxxxl: Dp = 64.dp,
)

/**
 * 모서리 반경 스케일. Dp 가 직접 필요할 때 쓰고,
 * 보통은 [FlowerShapes] 의 Shape 를 그대로 사용한다.
 */
object FlowerRadius {
    val None: Dp = 0.dp
    val Xs: Dp = 4.dp
    val Sm: Dp = 8.dp
    val Md: Dp = 12.dp
    val Lg: Dp = 16.dp
    val Xl: Dp = 24.dp
}

/** [FlowerRadius] 를 Compose [Shape] 로 노출한다. */
@Immutable
data class FlowerShapes(
    /** 0dp */
    val none: Shape = RectangleShape,
    /** 4dp */
    val xs: Shape = RoundedCornerShape(FlowerRadius.Xs),
    /** 8dp. 리스트 썸네일 · Size M 버튼 · 필터 행 */
    val sm: Shape = RoundedCornerShape(FlowerRadius.Sm),
    /** 12dp. 카드 미디어 · Size L 버튼 · 검색 바 */
    val md: Shape = RoundedCornerShape(FlowerRadius.Md),
    /** 16dp */
    val lg: Shape = RoundedCornerShape(FlowerRadius.Lg),
    /** 24dp. 바텀시트 상단 */
    val xl: Shape = RoundedCornerShape(FlowerRadius.Xl),
    /** pill. 뱃지 · 칩 · 아이콘 버튼 */
    val full: Shape = CircleShape,
)

/**
 * 컴포넌트 고정 치수 (Figma Size 변수 및 각 컴포넌트 스펙).
 */
@Immutable
data class FlowerSize(
    /** 44dp. 최소 터치 영역 (size/touch/min) */
    val touchMin: Dp = 44.dp,
    /** 56dp. 상단 앱바 높이 */
    val appBarHeight: Dp = 56.dp,
    /** 64dp. 하단 탭바 높이 */
    val tabBarHeight: Dp = 64.dp,
    /** 48dp. 검색 바 높이 */
    val searchBarHeight: Dp = 48.dp,
    /** 24dp. 기본 아이콘 크기 */
    val icon: Dp = 24.dp,
    /** 20dp. Size L 버튼 · 검색 바 안의 아이콘 */
    val iconSm: Dp = 20.dp,
    /** 16dp. Size M 버튼 · 칩 안의 아이콘 */
    val iconXs: Dp = 16.dp,
    /** 84dp. 리스트 행 썸네일 */
    val listThumb: Dp = 84.dp,
    /** 36dp. 카드 위 저장 버튼 */
    val cardOverlayButton: Dp = 36.dp,
)
