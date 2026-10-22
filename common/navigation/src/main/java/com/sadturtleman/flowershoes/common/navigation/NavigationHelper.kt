package com.sadturtleman.flowershoes.common.navigation

import kotlinx.coroutines.flow.Flow

/**
 * 단일 네비게이션 플로우. 전진/후진 모두 [NavSignal] 한 가지 형식으로 emit 된다.
 *
 * 호출부는 다음 중 하나로 사용한다.
 * - [navigateByRoute] — 직접 [NavRoute] 를 구성해서 전진 이동.
 * - [navigateTo] — 각 feature 가 정의한 [Page] 객체를 그대로 전달 (권장).
 * - [navigateToBack] — 하드웨어 백 키와 동일하게 한 단계 뒤로 이동.
 *
 * feature 모듈은 서로를 모른 채 이 인터페이스에만 의존한다.
 * 실제 백스택 조작은 호스트인 :app 의 AppNavHost 한 곳에서만 일어나므로
 * feature -> feature 간 컴파일 의존이 생기지 않는다.
 */
interface NavigationHelper {
    val navigationFlow: Flow<NavSignal>
    fun navigateByRoute(route: NavRoute)
    fun navigateTo(page: Page)
    fun navigateToBack()
}
