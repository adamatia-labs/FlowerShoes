package com.sadturtleman.flowershoes.home.presentation.home

import com.sadturtleman.flowershoes.common.presentation.mvi.UiState

/** 홈 화면 상태. */
sealed interface HomeUiState : UiState {

    /** 최초 진입 · 다시 시도 중 */
    data object Loading : HomeUiState

    /** 조회 실패 */
    data class Error(val message: String) : HomeUiState

    /**
     * 조회 성공.
     *
     * @param greeting 상단 인사 문구
     * @param bannerTitle 상단 배너 문구. null 이면 배너를 그리지 않는다.
     */
    data class Success(
        val greeting: String,
        val bannerTitle: String?,
    ) : HomeUiState
}
