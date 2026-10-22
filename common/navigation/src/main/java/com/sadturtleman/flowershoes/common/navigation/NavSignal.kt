package com.sadturtleman.flowershoes.common.navigation

/**
 * 단일 네비게이션 플로우에 흘려보내는 신호.
 *
 * - [GoToDestPage]: 특정 [NavRoute] 로 전진 이동.
 * - [Back]: 시스템/하드웨어 백 키와 동일하게 한 단계 뒤로 이동.
 */
sealed interface NavSignal {
    data class GoToDestPage(val route: NavRoute) : NavSignal
    data object Back : NavSignal
}
