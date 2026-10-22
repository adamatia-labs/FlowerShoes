package com.sadturtleman.flowershoes.navigation

import androidx.compose.runtime.Composable
import com.sadturtleman.flowershoes.common.presentation.ui.component.FlowerTab

/**
 * 앱 내 한 페이지의 호스트(:app) 측 메타데이터.
 *
 * - 캐스팅 가능한 typed args (`*Page.Args`) 는 각 feature/domain 모듈에 정의되어 호출자가 사용한다.
 * - 본 객체는 그 path 가 어떤 Composable 로 렌더되며 어떤 백스택 특성을 가지는지를
 *   호스트 측에서 단일 위치로 모은다. 새 페이지 추가 시 [appRoutes] 에만 한 줄 추가하면 된다.
 */
data class AppRoute(
    val path: String,
    /** 탭 바에서 이 탭을 눌렀을 때 도착하는 루트 페이지인지 여부. */
    val isTopTab: Boolean = false,
    /**
     * 이 페이지가 떠 있을 때 하단 탭 바에서 활성으로 보일 탭. null 이면 탭 바를 그리지 않는다.
     *
     * 검색 결과처럼 탭 루트는 아니지만 같은 탭 안에 머무는 페이지가 있어 [isTopTab] 과 나눠 둔다.
     * 탭 바를 그리는 곳은 RootComposable 한 곳이다 — 화면은 자기 본문만 그린다.
     */
    val tab: FlowerTab? = null,
    /**
     * deep-link 진입 시 구성할 시작 백스택. 일반 페이지는 자기 자신만 푸시되고,
     * 부모 페이지가 있는 경우 부모 키들을 함께 반환한다.
     */
    val syntheticStack: (args: Map<String, String>) -> List<GenericNavKey> = { args ->
        listOf(GenericNavKey(path, args))
    },
    /**
     * 페이지 본체 렌더러. args 를 받아 typed Args 로 디코딩하고 Composable 을 호출한다.
     * 디코딩 실패 시 안전한 기본값으로 폴백하는 책임도 본 함수에 있다.
     */
    val render: @Composable (args: Map<String, String>) -> Unit,
)
