package com.sadturtleman.flowershoes.home.presentation.home

import com.sadturtleman.flowershoes.common.presentation.mvi.ReducerEvent
import com.sadturtleman.flowershoes.home.entity.HomeContentVO

/** 홈 상태를 바꾸는 내부 이벤트. ViewModel 안에서만 dispatch 된다. */
sealed interface HomeReducerEvent : ReducerEvent {
    data object LoadStarted : HomeReducerEvent
    data class LoadFailed(val message: String) : HomeReducerEvent
    data class Loaded(val content: HomeContentVO) : HomeReducerEvent
}
