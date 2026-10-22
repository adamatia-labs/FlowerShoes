package com.sadturtleman.flowershoes.navigation

import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.sadturtleman.flowershoes.common.presentation.ui.component.FlowerTab
import com.sadturtleman.flowershoes.home.navigation.HomePage
import com.sadturtleman.flowershoes.home.presentation.home.HomeScreen
import com.sadturtleman.flowershoes.home.presentation.home.HomeViewModel

/**
 * 앱의 모든 페이지 메타데이터 + 렌더러 모음.
 * 새 화면 추가 시 본 리스트에 한 줄을 더한다. (AppNavHost 코드는 손대지 않는다.)
 *
 * feature 간 화면 참조가 여기 한 곳으로만 모이기 때문에, feature 모듈끼리는
 * 서로의 presentation 을 참조하지 않고 :*:navigation 의 Page 정의만 알면 된다.
 *
 * 모든 feature:presentation 을 참조하는 유일한 모듈이 되어야 하므로 조립 지점인 :app 에 둔다.
 * (feature 모듈 하나가 이 역할을 겸하면 aggregator 가 둘이 된다)
 *
 * 각 render 는 ViewModel 을 붙이는 한 줄이고, 화면 그리기는 feature 의 *Screen 컴포저블이 맡는다.
 *
 * 인자가 있는 페이지는 render 안에서 `remember(rawArgs) { XxxPage.Args.from(rawArgs) }` 로
 * 한 번만 디코딩하고, deep-link 로 바로 들어와도 뒤로가기가 홈으로 빠지도록
 * syntheticStack 에 부모 키를 함께 쌓는다.
 */
val appRoutes: List<AppRoute> = listOf(
    AppRoute(
        path = HomePage.PATH,
        isTopTab = true,
        tab = FlowerTab.Home,
        render = { HomeScreen(viewModel = hiltViewModel<HomeViewModel>()) },
    ),
)

val appRouteByPath: Map<String, AppRoute> = appRoutes.associateBy { it.path }

/** 탭 → 그 탭의 루트 페이지. 탭을 눌렀을 때 이동할 대상을 찾는 데 쓴다. */
val tabRootRouteByTab: Map<FlowerTab, AppRoute> = appRoutes
    .filter { it.isTopTab }
    .associateBy { route -> checkNotNull(route.tab) { "탭 루트에는 tab 이 있어야 한다: ${route.path}" } }
