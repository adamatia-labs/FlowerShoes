package com.sadturtleman.flowershoes.common.presentation.mvi

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update

/**
 * 사용자 입력(클릭, 스크롤 등)을 표현하는 마커 인터페이스.
 * `View → ViewModel` 방향으로만 흐른다.
 */
interface MviIntent

/**
 * 화면에 노출되는 불변 상태의 마커 인터페이스.
 * `ViewModel → View` 방향으로만 흐른다.
 */
interface UiState

/**
 * Reducer 에 입력되는 내부 이벤트 마커 인터페이스.
 * (예: 데이터 로딩 완료, 저장 목록 변경 등) — 외부에서 직접 dispatch 하지 않는다.
 */
interface ReducerEvent

/**
 * MVI 베이스 ViewModel.
 *
 * - [uiState] 단일 [StateFlow] 만 View 에 노출한다.
 * - 외부 진입점은 [onIntent] 하나로 통일한다.
 * - 모든 상태 변이는 [reduce] 한 곳을 거쳐 [dispatch] 로만 일어난다.
 *   (collectors / coroutines 곳곳에서 `uiState.update {}` 를 직접 호출하지 않는다)
 */
abstract class MviViewModel<I : MviIntent, S : UiState, E : ReducerEvent>(
    initialState: S,
) : ViewModel() {

    val uiState: StateFlow<S>
        field = MutableStateFlow<S>(initialState)

    protected val currentState: S
        get() = uiState.value

    abstract fun onIntent(intent: I)

    protected abstract fun reduce(state: S, event: E): S

    protected fun dispatch(event: E) {
        uiState.update { current -> reduce(current, event) }
    }
}
