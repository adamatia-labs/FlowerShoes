package com.sadturtleman.flowershoes.common.presentation.helper

import android.util.Log
import androidx.compose.runtime.compositionLocalOf
import com.sadturtleman.flowershoes.common.navigation.NavigationHelper
import com.sadturtleman.flowershoes.common.navigation.NavRoute
import com.sadturtleman.flowershoes.common.navigation.NavSignal
import com.sadturtleman.flowershoes.common.navigation.Page
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow

/**
 * [Channel] 기반 구현. 화면 회전 등으로 collector 가 잠시 없더라도 BUFFERED 채널이 신호를 보관한다.
 * (SharedFlow 와 달리 한 신호가 정확히 한 번만 소비되므로 중복 push 가 생기지 않는다.)
 *
 * Hilt 로 @Singleton 제공되므로 ViewModel / UseCase 어디서든 주입받아 쓸 수 있고,
 * Composable 트리에는 [LocalNavigationHelper] 로 내려준다.
 */
class NavigationHelperImpl : NavigationHelper {
    private val _navigationFlow = Channel<NavSignal>(capacity = Channel.BUFFERED)
    override val navigationFlow: Flow<NavSignal> = _navigationFlow.receiveAsFlow()

    override fun navigateTo(page: Page) {
        navigateByRoute(page.toRoute())
    }

    override fun navigateByRoute(route: NavRoute) {
        emit(NavSignal.GoToDestPage(route))
    }

    override fun navigateToBack() {
        emit(NavSignal.Back)
    }

    private fun emit(navSignal: NavSignal) {
        val result = _navigationFlow.trySend(navSignal)
        if (result.isFailure) Log.w(TAG, "dropped: $navSignal")
    }

    private companion object {
        const val TAG = "NavigationHelper"
    }
}

val LocalNavigationHelper = compositionLocalOf<NavigationHelper> {
    error("LocalNavigationHelper is not provided. Wrap with CompositionLocalProvider in MainActivity.")
}
