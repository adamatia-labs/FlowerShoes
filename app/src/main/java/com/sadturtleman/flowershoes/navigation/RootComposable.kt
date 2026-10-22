package com.sadturtleman.flowershoes.navigation

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.rememberNavBackStack
import com.sadturtleman.flowershoes.common.navigation.NavRoute
import com.sadturtleman.flowershoes.common.presentation.helper.LocalNavigationHelper
import com.sadturtleman.flowershoes.common.presentation.ui.component.FlowerTab
import com.sadturtleman.flowershoes.common.presentation.ui.component.FlowerTabBar
import com.sadturtleman.flowershoes.common.presentation.ui.theme.FlowerShoesTheme
import com.sadturtleman.flowershoes.common.presentation.ui.theme.FlowerTheme
import com.sadturtleman.flowershoes.home.navigation.HomePage

/**
 * 앱 최상위 Composable. 백스택 소유 + 시스템 인셋 처리 + [AppNavHost] 배치를 담당한다.
 *
 * 스낵바 · 다이얼로그 · 토스트도 여기서 그린다([MessageHost]). 탭 바와 같은 이유다 —
 * 스낵바 호스트는 앱에 하나뿐이라 화면마다 두면 같은 코드가 화면 수만큼 복제된다.
 *
 * 하단 탭 바도 여기서 그린다. 화면이 각자 그리면 어느 화면이든 다른 탭의 Page 정의를 알아야 해서
 * feature 끼리 서로를 참조하게 되는데, 탭 구성은 개별 화면이 아니라 앱 전체의 결정이다.
 * 어느 탭이 활성인지는 백스택 맨 위 경로의 [AppRoute.tab] 이 알려준다 — 화면은 탭 바의 존재를 모른다.
 *
 * @param startStack deep-link 로 진입한 경우의 시작 백스택. 기본값은 홈 단일 스택.
 */
@Composable
fun RootComposable(
    modifier: Modifier = Modifier,
    startStack: List<NavKey> = listOf(GenericNavKey(HomePage.PATH)),
) {
    val navigationHelper = LocalNavigationHelper.current

    FlowerShoesTheme {
        // rememberNavBackStack 은 @Serializable NavKey 를 저장/복원한다 (프로세스 사망 대응).
        val backStack = rememberNavBackStack(*startStack.toTypedArray())

        val currentPath = (backStack.lastOrNull() as? GenericNavKey)?.path
        val currentRoute = currentPath?.let { path -> appRouteByPath[path] }
        val snackbarHostState = remember { SnackbarHostState() }

        MessageHost(snackbarHostState = snackbarHostState)
        // 화면 진입 이벤트는 백스택을 쥔 여기서 한 번에 남긴다(화면은 알 필요가 없다).
        ViewEnterEffect(currentPath = currentPath)

        Scaffold(
            modifier = modifier.fillMaxSize(),
            snackbarHost = {
                // 기본 스낵바는 M3 팔레트(보라색 액션)를 쓰므로 디자인 시스템 색으로 덮는다.
                SnackbarHost(snackbarHostState) { data ->
                    Snackbar(
                        snackbarData = data,
                        shape = FlowerTheme.shapes.sm,
                        containerColor = FlowerTheme.colors.bgSurfaceInverse,
                        contentColor = FlowerTheme.colors.textInverse,
                        actionColor = FlowerTheme.colors.textOnDark,
                    )
                }
            },
            bottomBar = {
                // 탭에 속하지 않는 화면에서는 그리지 않는다.
                val selectedTab = currentRoute?.tab ?: return@Scaffold
                // 탭 루트가 하나뿐이면 옮겨 갈 곳이 없으므로 탭 바를 그리지 않는다.
                // 두 번째 탭 루트를 appRoutes 에 등록하는 순간 나타난다.
                if (tabRootRouteByTab.size < MIN_TAB_COUNT) return@Scaffold
                FlowerTabBar(
                    // 탭 바 자신은 홈 인디케이터 영역을 그리지 않는다(컴포넌트 규약).
                    modifier = Modifier.navigationBarsPadding(),
                    selected = selectedTab,
                    tabs = FlowerTab.entries.filter { it in tabRootRouteByTab },
                    onSelect = { tab ->
                        tabRootRouteByTab[tab]?.let { route ->
                            navigationHelper.navigateByRoute(NavRoute(route.path))
                        }
                    },
                )
            },
        ) { innerPadding ->
            AppNavHost(
                backStack = backStack,
                modifier = Modifier.padding(innerPadding),
            )
        }
    }
}

private const val MIN_TAB_COUNT = 2
