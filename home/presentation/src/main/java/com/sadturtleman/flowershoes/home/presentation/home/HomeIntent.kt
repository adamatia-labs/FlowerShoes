package com.sadturtleman.flowershoes.home.presentation.home

import com.sadturtleman.flowershoes.common.presentation.mvi.MviIntent

/** 홈 화면에서 올라오는 사용자 입력. `View → ViewModel` 방향으로만 흐른다. */
sealed interface HomeIntent : MviIntent {
    /** 에러 화면의 다시 시도. */
    data object Retry : HomeIntent
}
