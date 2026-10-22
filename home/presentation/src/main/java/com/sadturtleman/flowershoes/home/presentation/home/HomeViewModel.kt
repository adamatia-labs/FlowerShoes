package com.sadturtleman.flowershoes.home.presentation.home

import androidx.lifecycle.viewModelScope
import com.sadturtleman.flowershoes.common.presentation.mvi.MviViewModel
import com.sadturtleman.flowershoes.home.domain.GetHomeContentUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * 홈 화면 ViewModel.
 *
 * 상태 변이는 [reduce] 한 곳만 거치고, 외부 진입점은 [onIntent] 하나다.
 * 화면 이동이 필요해지면 NavigationHelper 를, 스낵바 · 다이얼로그가 필요해지면 MessageHelper 를
 * 생성자로 주입받는다(둘 다 :common:presentation 이 @Singleton 으로 제공한다).
 */
@HiltViewModel
class HomeViewModel @Inject constructor(
    private val getHomeContent: GetHomeContentUseCase,
) : MviViewModel<HomeIntent, HomeUiState, HomeReducerEvent>(HomeUiState.Loading) {

    private var loadJob: Job? = null

    init {
        loadHome()
    }

    override fun onIntent(intent: HomeIntent) {
        when (intent) {
            HomeIntent.Retry -> loadHome()
        }
    }

    override fun reduce(state: HomeUiState, event: HomeReducerEvent): HomeUiState = when (event) {
        HomeReducerEvent.LoadStarted -> HomeUiState.Loading

        is HomeReducerEvent.LoadFailed -> HomeUiState.Error(event.message)

        is HomeReducerEvent.Loaded -> HomeUiState.Success(
            greeting = event.content.greeting,
            bannerTitle = event.content.banner.takeIf { it.isVisible }?.title,
        )
    }

    /** 다시 시도를 연달아 누르면 앞의 조회가 늦게 도착해 상태를 덮을 수 있어, 새 조회는 앞의 것을 취소한다. */
    private fun loadHome() {
        loadJob?.cancel()
        dispatch(HomeReducerEvent.LoadStarted)
        loadJob = viewModelScope.launch {
            runCatching { getHomeContent() }
                .onSuccess { content -> dispatch(HomeReducerEvent.Loaded(content)) }
                .onFailure { throwable ->
                    dispatch(HomeReducerEvent.LoadFailed(throwable.message ?: "홈을 불러오지 못했습니다."))
                }
        }
    }
}
